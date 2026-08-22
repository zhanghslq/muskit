package io.github.zhanghslq.muskit.audit.service;

import io.github.zhanghslq.muskit.audit.exception.AuditWriteException;
import io.github.zhanghslq.muskit.audit.model.AuditEvent;
import io.github.zhanghslq.muskit.audit.model.AuditFailureMode;
import io.github.zhanghslq.muskit.audit.model.AuditOutcome;
import io.github.zhanghslq.muskit.audit.service.AuditRecorder;
import io.github.zhanghslq.muskit.audit.spi.AuditFailureListener;
import io.github.zhanghslq.muskit.audit.spi.AuditWriter;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审计记录器失败语义测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class AuditRecorderTest {

    /**
     * 验证完整审计事件被安全创建并写入。
     */
    @Test
    void shouldWriteCompleteEvent() {
        AtomicReference<AuditEvent> captured = new AtomicReference<>();
        AuditRecorder recorder = recorder(captured::set, AuditFailureMode.FAIL_FAST, (action, failure) -> { });

        recorder.record("order.cancel", AuditOutcome.SUCCESS, "order", "sensitive-id", null, Map.of("channel", "api"));

        assertThat(captured.get().eventId()).isEqualTo("event-1");
        assertThat(captured.get().actor()).contains("operator-1");
        assertThat(captured.get().toString()).doesNotContain("sensitive-id").doesNotContain("operator-1");
    }

    /**
     * 验证强约束模式会向业务调用传播审计失败。
     */
    @Test
    void shouldFailFast() {
        AuditRecorder recorder = recorder(event -> { throw new IllegalStateException("down"); },
                AuditFailureMode.FAIL_FAST, (action, failure) -> { });

        assertThatThrownBy(() -> recorder.record("order.cancel", AuditOutcome.SUCCESS))
                .isInstanceOf(AuditWriteException.class);
    }

    /**
     * 验证 BEST_EFFORT 模式通过监听器明确报告丢弃。
     */
    @Test
    void shouldNotifyBestEffortDrop() {
        AtomicReference<String> failedAction = new AtomicReference<>();
        AuditRecorder recorder = recorder(event -> { throw new IllegalStateException("down"); },
                AuditFailureMode.BEST_EFFORT, (action, failure) -> failedAction.set(action));

        recorder.record("order.cancel", AuditOutcome.FAILURE);

        assertThat(failedAction).hasValue("order.cancel");
    }

    /**
     * 创建确定性测试记录器。
     *
     * @param writer 审计 Writer
     * @param mode 失败模式
     * @param listener 失败监听器
     * @return 审计记录器
     */
    private AuditRecorder recorder(AuditWriter writer, AuditFailureMode mode, AuditFailureListener listener) {
        return new AuditRecorder(
                writer,
                () -> Optional.of("operator-1"),
                listener,
                mode,
                MuskitObservationRegistry.noop(),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                () -> "event-1");
    }
}
