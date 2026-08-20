package io.github.zhanghslq.muskit.test.idempotency;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import io.github.zhanghslq.muskit.idempotency.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.IdempotencyOwnershipLostException;
import io.github.zhanghslq.muskit.idempotency.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.IdempotencyResult;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 所有幂等状态存储实现都应通过的状态机契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class IdempotencyStoreContract {

    /**
     * 创建幂等状态存储契约测试基类。
     */
    protected IdempotencyStoreContract() {
    }

    /**
     * 返回代表第一个应用实例的幂等状态存储。
     *
     * @return 第一个幂等状态存储
     */
    protected abstract IdempotencyStore firstStore();

    /**
     * 返回代表第二个应用实例的幂等状态存储。
     *
     * @return 第二个幂等状态存储
     */
    protected abstract IdempotencyStore secondStore();

    /**
     * 验证不同实例看到相同的处理中和已完成状态。
     */
    @Test
    void shouldShareProcessingAndCompletedStatesAcrossInstances() {
        IdempotencyRequest request = request("complete");

        IdempotencyAttempt acquired = firstStore().tryStart(request);

        assertEquals(IdempotencyDecision.ACQUIRED, acquired.decision());
        assertEquals(IdempotencyDecision.IN_PROGRESS, secondStore().tryStart(request).decision());
        firstStore().complete(acquired.claim().orElseThrow());
        assertEquals(IdempotencyDecision.COMPLETED, secondStore().tryStart(request).decision());
    }

    /**
     * 验证失败释放后另一个实例可以重新取得所有权。
     */
    @Test
    void shouldAllowRetryAfterRelease() {
        IdempotencyRequest request = request("release");
        IdempotencyClaim claim = firstStore().tryStart(request).claim().orElseThrow();

        firstStore().release(claim);

        assertEquals(IdempotencyDecision.ACQUIRED, secondStore().tryStart(request).decision());
    }

    /**
     * 验证一个实例保存的可重放结果可以被另一个实例读取。
     */
    @Test
    void shouldShareCompletedResultAcrossInstances() {
        IdempotencyRequest request = request("result");
        IdempotencyClaim claim = firstStore().tryStart(request).claim().orElseThrow();
        IdempotencyResult result = new IdempotencyResult(
                201,
                "application/json",
                Map.of("Location", "/orders/42"),
                "{\"orderId\":42}".getBytes(StandardCharsets.UTF_8));

        firstStore().complete(claim, result);

        IdempotencyResult loaded = secondStore().findCompletedResult(request).orElseThrow();
        assertEquals(result.statusCode(), loaded.statusCode());
        assertEquals(result.contentType(), loaded.contentType());
        assertEquals(result.headers(), loaded.headers());
        assertEquals("{\"orderId\":42}", new String(loaded.body(), StandardCharsets.UTF_8));
    }

    /**
     * 验证续期保持当前所有权，其他实例仍然只能看到处理中状态。
     */
    @Test
    void shouldRenewCurrentOwner() {
        IdempotencyRequest request = request("renew");
        IdempotencyClaim claim = firstStore().tryStart(request).claim().orElseThrow();

        firstStore().renew(claim, Duration.ofSeconds(30));

        assertEquals(IdempotencyDecision.IN_PROGRESS, secondStore().tryStart(request).decision());
    }

    /**
     * 验证伪造或过期的所有权令牌不能完成、续期或释放当前状态。
     */
    @Test
    void shouldRejectStaleOwnerToken() {
        IdempotencyRequest request = request("stale-owner");
        IdempotencyClaim current = firstStore().tryStart(request).claim().orElseThrow();
        IdempotencyClaim stale = new IdempotencyClaim(
                current.operation(),
                current.key(),
                UUID.randomUUID().toString(),
                current.retention());

        assertThrows(IdempotencyOwnershipLostException.class, () -> secondStore().complete(stale));
        assertThrows(
                IdempotencyOwnershipLostException.class,
                () -> secondStore().renew(stale, Duration.ofSeconds(30)));
        assertThrows(IdempotencyOwnershipLostException.class, () -> secondStore().release(stale));
        firstStore().release(current);
    }

    /**
     * 创建带随机键的幂等请求，避免不同契约测试之间共享状态。
     *
     * @param scenario 测试场景
     * @return 幂等请求
     */
    private IdempotencyRequest request(String scenario) {
        return new IdempotencyRequest(
                "contract",
                scenario + '-' + UUID.randomUUID(),
                Duration.ofSeconds(30),
                Duration.ofHours(1));
    }
}
