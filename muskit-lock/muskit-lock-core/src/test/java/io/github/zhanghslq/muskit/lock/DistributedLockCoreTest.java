package io.github.zhanghslq.muskit.lock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分布式锁核心值对象和本地降级锁测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class DistributedLockCoreTest {

    /**
     * 验证锁请求描述不会泄露业务锁键。
     */
    @Test
    void shouldHideBusinessKeyFromRequestDescription() {
        DistributedLockRequest request = request("sensitive-order-id");

        assertThat(request.toString())
                .contains("order-submit")
                .doesNotContain("sensitive-order-id");
    }

    /**
     * 验证锁请求拒绝负数等待时间和负数租约时间。
     */
    @Test
    void shouldValidateDurations() {
        assertThatThrownBy(() -> new DistributedLockRequest(
                "order-submit", "", Duration.ofMillis(-1), Duration.ZERO, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("等待时间不能为负数");
        assertThatThrownBy(() -> new DistributedLockRequest(
                "order-submit", "", Duration.ZERO, Duration.ofMillis(-1), false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租约时间不能为负数");
    }

    /**
     * 验证本地降级锁按业务键隔离并支持跨线程幂等释放。
     *
     * @throws Exception 异步释放失败
     */
    @Test
    void shouldIsolateAndReleaseLocalLocks() throws Exception {
        LocalDistributedLockProvider provider = new LocalDistributedLockProvider();
        DistributedLockHandle first = provider.tryAcquire(request("A")).orElseThrow();

        assertThat(provider.tryAcquire(request("A"))).isEmpty();
        DistributedLockHandle otherKey = provider.tryAcquire(request("B")).orElseThrow();

        Thread.ofVirtual().start(first::close).join();
        first.close();
        assertThat(provider.tryAcquire(request("A"))).isPresent().hasValueSatisfying(DistributedLockHandle::close);
        otherKey.close();
    }

    /**
     * 验证 fencing token 作用域嵌套恢复且本地锁明确拒绝 fencing。
     */
    @Test
    void shouldScopeAndRejectLocalFencing() {
        try (FencingTokenContext.Scope ignored = FencingTokenContext.open(10)) {
            assertThat(FencingTokenContext.current()).hasValue(10);
            try (FencingTokenContext.Scope ignoredNested = FencingTokenContext.open(20)) {
                assertThat(FencingTokenContext.current()).hasValue(20);
            }
            assertThat(FencingTokenContext.current()).hasValue(10);
        }
        assertThat(FencingTokenContext.current()).isEmpty();

        DistributedLockRequest fenced = new DistributedLockRequest(
                "order-submit", "A", Duration.ZERO, Duration.ZERO, false, true, true);
        assertThatThrownBy(() -> new LocalDistributedLockProvider().tryAcquire(fenced))
                .isInstanceOf(FencingTokenUnavailableException.class);
    }

    /**
     * 创建测试锁请求。
     *
     * @param key 业务锁键
     * @return 测试锁请求
     */
    private DistributedLockRequest request(String key) {
        return new DistributedLockRequest("order-submit", key, Duration.ZERO, Duration.ZERO, false, true);
    }
}
