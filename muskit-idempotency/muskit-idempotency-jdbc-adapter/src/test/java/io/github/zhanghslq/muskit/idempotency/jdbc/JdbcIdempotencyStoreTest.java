package io.github.zhanghslq.muskit.idempotency.jdbc;

import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyOwnershipLostException;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JdbcIdempotencyStore 状态机集成测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class JdbcIdempotencyStoreTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    /**
     * 验证处理中、成功和释放后的重试状态转换。
     */
    @Test
    void shouldTransitionBetweenProcessingCompletedAndRetryableStates() {
        JdbcIdempotencyStore store = storeAt(BASE_TIME);
        IdempotencyRequest completedRequest = request("completed-key");
        IdempotencyAttempt acquired = store.tryStart(completedRequest);

        assertThat(acquired.decision()).isEqualTo(IdempotencyDecision.ACQUIRED);
        assertThat(store.tryStart(completedRequest).decision()).isEqualTo(IdempotencyDecision.IN_PROGRESS);
        store.complete(acquired.claim().orElseThrow());
        assertThat(store.tryStart(completedRequest).decision()).isEqualTo(IdempotencyDecision.COMPLETED);

        IdempotencyRequest retryRequest = request("retry-key");
        IdempotencyClaim retryClaim = store.tryStart(retryRequest).claim().orElseThrow();
        store.release(retryClaim);
        assertThat(store.tryStart(retryRequest).decision()).isEqualTo(IdempotencyDecision.ACQUIRED);
    }

    /**
     * 验证处理中超时后其他实例可以接管，原所有者不能再完成记录。
     */
    @Test
    void shouldReclaimExpiredProcessingEntry() {
        JdbcIdempotencyStore firstStore = storeAt(BASE_TIME);
        IdempotencyRequest request = request("expired-key");
        IdempotencyClaim original = firstStore.tryStart(request).claim().orElseThrow();
        JdbcIdempotencyStore laterStore = storeAt(BASE_TIME.plusSeconds(31));

        IdempotencyClaim replacement = laterStore.tryStart(request).claim().orElseThrow();

        assertThat(replacement.ownerToken()).isNotEqualTo(original.ownerToken());
        assertThatThrownBy(() -> laterStore.complete(original))
                .isInstanceOf(IdempotencyOwnershipLostException.class);
        laterStore.complete(replacement);
    }

    /**
     * 验证成功状态保留期结束后相同请求可以重新执行。
     */
    @Test
    void shouldExpireCompletedEntryAfterRetention() {
        JdbcIdempotencyStore firstStore = storeAt(BASE_TIME);
        IdempotencyRequest request = request("retention-key");
        firstStore.complete(firstStore.tryStart(request).claim().orElseThrow());

        JdbcIdempotencyStore laterStore = storeAt(BASE_TIME.plus(Duration.ofHours(2)));

        assertThat(laterStore.tryStart(request).decision()).isEqualTo(IdempotencyDecision.ACQUIRED);
    }

    /**
     * 创建使用独立 H2 数据库和固定时钟的 JDBC 存储。
     *
     * @param instant 固定当前时间
     * @return 已初始化表结构的 JDBC 存储
     */
    private JdbcIdempotencyStore storeAt(Instant instant) {
        String databaseName = databaseName();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", ""));
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(
                jdbcTemplate, "muskit_idempotency", Clock.fixed(instant, ZoneOffset.UTC));
        store.initializeSchema();
        return store;
    }

    /**
     * 返回同一测试方法共享的数据库名称。
     *
     * @return 数据库名称
     */
    private String databaseName() {
        StackTraceElement caller = StackWalker.getInstance()
                .walk(frames -> frames
                        .filter(frame -> frame.getClassName().equals(getClass().getName()))
                        .filter(frame -> !frame.getMethodName().equals("databaseName"))
                        .filter(frame -> !frame.getMethodName().equals("storeAt"))
                        .findFirst()
                        .orElseThrow())
                .toStackTraceElement();
        return caller.getMethodName().replaceAll("[^A-Za-z0-9]", "_");
    }

    /**
     * 创建测试幂等请求。
     *
     * @param key 业务幂等键
     * @return 测试请求
     */
    private IdempotencyRequest request(String key) {
        return new IdempotencyRequest(
                "payment", key, Duration.ofSeconds(30), Duration.ofHours(1));
    }
}
