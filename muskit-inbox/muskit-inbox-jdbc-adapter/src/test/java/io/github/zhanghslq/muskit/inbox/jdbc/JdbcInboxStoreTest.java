package io.github.zhanghslq.muskit.inbox.jdbc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import io.github.zhanghslq.muskit.inbox.InboxDecision;
import io.github.zhanghslq.muskit.inbox.InboxRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC Inbox 租约、重试、死信和人工回放测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class JdbcInboxStoreTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-20T00:00:00Z");
    private JdbcTemplate jdbcTemplate;

    /**
     * 为每个测试创建隔离的内存数据库。
     */
    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 验证成功状态去重并在保留期限过后允许重新处理。
     */
    @Test
    void shouldDeduplicateUntilRetentionExpires() {
        JdbcInboxStore initial = storeAt(INITIAL_TIME);
        initial.initializeSchema();
        var claim = initial.tryClaim(request()).claim().orElseThrow();
        initial.complete(claim);

        assertThat(initial.tryClaim(request()).decision()).isEqualTo(InboxDecision.SUCCEEDED);
        assertThat(storeAt(INITIAL_TIME.plusSeconds(61)).tryClaim(request()).decision())
                .isEqualTo(InboxDecision.ACQUIRED);
    }

    /**
     * 验证失败等待、到期重试、死信统计和人工回放。
     */
    @Test
    void shouldRetryDeadAndReplay() {
        JdbcInboxStore initial = storeAt(INITIAL_TIME);
        initial.initializeSchema();
        var first = initial.tryClaim(request()).claim().orElseThrow();
        initial.retry(first, Duration.ofSeconds(10), "business-failure");

        assertThat(storeAt(INITIAL_TIME.plusSeconds(9)).tryClaim(request()).decision())
                .isEqualTo(InboxDecision.RETRY_LATER);
        JdbcInboxStore retrying = storeAt(INITIAL_TIME.plusSeconds(11));
        var second = retrying.tryClaim(request()).claim().orElseThrow();
        assertThat(second.attempt()).isEqualTo(2);
        retrying.markDead(second, "business-failure");
        assertThat(retrying.countDead("order-consumer")).isEqualTo(1L);
        assertThat(retrying.replayDead("order-consumer", "message-1")).isTrue();
        assertThat(retrying.tryClaim(request()).claim().orElseThrow().attempt()).isEqualTo(1);
    }

    /**
     * 验证处理租约未过期时拒绝并发处理，过期后允许接管。
     */
    @Test
    void shouldTakeOverExpiredProcessingLease() {
        JdbcInboxStore initial = storeAt(INITIAL_TIME);
        initial.initializeSchema();
        initial.tryClaim(request());

        assertThat(storeAt(INITIAL_TIME.plusSeconds(29)).tryClaim(request()).decision())
                .isEqualTo(InboxDecision.IN_PROGRESS);
        assertThat(storeAt(INITIAL_TIME.plusSeconds(31)).tryClaim(request()).claim().orElseThrow().attempt())
                .isEqualTo(2);
    }

    /**
     * 创建指定时刻的 JDBC Inbox 存储。
     *
     * @param instant 当前时刻
     * @return Inbox 存储
     */
    private JdbcInboxStore storeAt(Instant instant) {
        return new JdbcInboxStore(
                jdbcTemplate,
                "muskit_inbox",
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    /**
     * 创建测试 Inbox 请求。
     *
     * @return Inbox 请求
     */
    private InboxRequest request() {
        return new InboxRequest(
                "order-consumer",
                "message-1",
                Duration.ofSeconds(30),
                Duration.ofSeconds(60));
    }
}
