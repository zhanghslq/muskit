package io.github.zhanghslq.muskit.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineExceededException;

/**
 * 在协议无关核心层编码和恢复 HTTP 调用链上下文。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ClientPropagation {

    /** 传播绝对截止时间的请求头。 */
    public static final String DEADLINE_HEADER = "X-Muskit-Deadline-Epoch-Millis";

    private final ClientPropagationPolicy policy;
    private final Clock clock;

    /**
     * 使用系统 UTC 时钟创建传播器。
     *
     * @param policy 传播策略
     */
    public ClientPropagation(ClientPropagationPolicy policy) {
        this(policy, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建传播器。
     *
     * @param policy 传播策略
     * @param clock 时间来源
     */
    public ClientPropagation(ClientPropagationPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "调用链传播策略不能为空");
        this.clock = Objects.requireNonNull(clock, "调用链传播时钟不能为空");
    }

    /**
     * 生成需要覆盖写入出站请求的安全请求头。
     *
     * @return 不可变出站请求头
     */
    public Map<String, String> outboundHeaders() {
        Instant now = clock.instant();
        Deadline parent = DeadlineContext.current().orElse(null);
        if (parent != null && parent.isExpired()) {
            throw new DeadlineExceededException();
        }
        Instant configuredLimit = now.plus(policy.outboundTimeout());
        Instant effectiveDeadline = parent == null || parent.expiresAt().isAfter(configuredLimit)
                ? configuredLimit
                : parent.expiresAt();

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put(DEADLINE_HEADER, Long.toString(effectiveDeadline.toEpochMilli()));
        MuskitContext context = MuskitContextHolder.currentOrEmpty();
        policy.contextHeaders().forEach((contextKey, headerName) -> context.get(contextKey)
                .ifPresent(value -> headers.put(headerName, validateValue(value))));
        return Map.copyOf(headers);
    }

    /**
     * 从入站请求头打开业务上下文和 Deadline 作用域。
     *
     * @param headerReader 按名称读取第一个请求头值的函数
     * @return 必须关闭的入站作用域
     */
    public InboundScope openInbound(Function<String, String> headerReader) {
        Objects.requireNonNull(headerReader, "请求头读取器不能为空");
        MuskitContext merged = mergeContext(headerReader);
        MuskitContextHolder.Scope contextScope = merged == null ? null : MuskitContextHolder.open(merged);
        try {
            DeadlineContext.Scope deadlineScope = openDeadline(headerReader.apply(DEADLINE_HEADER));
            return new InboundScope(contextScope, deadlineScope);
        } catch (RuntimeException exception) {
            if (contextScope != null) {
                contextScope.close();
            }
            throw exception;
        }
    }

    /**
     * 合并白名单内的远端业务上下文。
     *
     * @param headerReader 请求头读取器
     * @return 合并后的上下文，没有远端值时返回空
     */
    private MuskitContext mergeContext(Function<String, String> headerReader) {
        MuskitContext merged = MuskitContextHolder.currentOrEmpty();
        boolean changed = false;
        for (Map.Entry<String, String> entry : policy.contextHeaders().entrySet()) {
            String value = headerReader.apply(entry.getValue());
            if (value != null) {
                merged = merged.with(entry.getKey(), validateValue(value));
                changed = true;
            }
        }
        return changed ? merged : null;
    }

    /**
     * 校验并打开远端 Deadline 作用域。
     *
     * @param headerValue Deadline 请求头值
     * @return Deadline 作用域，没有请求头时返回空
     */
    private DeadlineContext.Scope openDeadline(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        long epochMillis;
        try {
            epochMillis = Long.parseLong(headerValue);
        } catch (NumberFormatException exception) {
            throw new InvalidPropagationHeaderException("调用链 Deadline 请求头格式错误");
        }
        Instant now = clock.instant();
        Instant requested;
        try {
            requested = Instant.ofEpochMilli(epochMillis);
        } catch (RuntimeException exception) {
            throw new InvalidPropagationHeaderException("调用链 Deadline 请求头超出有效范围");
        }
        Duration remaining = Duration.between(now, requested);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new DeadlineExceededException();
        }
        Duration effective = remaining.compareTo(policy.maxInboundTimeout()) > 0
                ? policy.maxInboundTimeout()
                : remaining;
        return DeadlineContext.open(Deadline.after(effective, clock));
    }

    /**
     * 校验传播值长度和换行符，防止请求头注入。
     *
     * @param value 待传播值
     * @return 原传播值
     */
    private String validateValue(String value) {
        if (value.length() > policy.maxHeaderValueLength() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new InvalidPropagationHeaderException("业务上下文传播请求头不合法");
        }
        return value;
    }

    /**
     * 入站调用链作用域，关闭时按逆序恢复线程状态。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static final class InboundScope implements AutoCloseable {

        private final MuskitContextHolder.Scope contextScope;
        private final DeadlineContext.Scope deadlineScope;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 创建入站调用链作用域。
         *
         * @param contextScope 业务上下文作用域
         * @param deadlineScope Deadline 作用域
         */
        private InboundScope(
                MuskitContextHolder.Scope contextScope,
                DeadlineContext.Scope deadlineScope) {
            this.contextScope = contextScope;
            this.deadlineScope = deadlineScope;
        }

        /**
         * 恢复进入前的 Deadline 和业务上下文，重复关闭保持幂等。
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            // 即使 Deadline 恢复异常，也必须清理业务上下文，避免容器线程复用时泄漏。
            try {
                if (deadlineScope != null) {
                    deadlineScope.close();
                }
            } finally {
                if (contextScope != null) {
                    contextScope.close();
                }
            }
        }
    }
}
