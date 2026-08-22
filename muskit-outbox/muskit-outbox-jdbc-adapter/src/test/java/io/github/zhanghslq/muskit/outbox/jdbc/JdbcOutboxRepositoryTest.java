package io.github.zhanghslq.muskit.outbox.jdbc;

import io.github.zhanghslq.muskit.outbox.model.OutboxClaim;
import io.github.zhanghslq.muskit.outbox.model.OutboxEvent;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import io.github.zhanghslq.muskit.test.outbox.OutboxRepositoryContract;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JDBC Outbox 存储测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class JdbcOutboxRepositoryTest extends OutboxRepositoryContract {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-20T00:00:00Z");

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;

    /**
     * 为每个测试创建隔离的内存数据库。
     */
    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    /**
     * 验证默认强事务模式拒绝脱离业务事务写入事件。
     */
    @Test
    void shouldRequireActiveTransactionWhenAppending() {
        JdbcOutboxRepository repository = repositoryAt(INITIAL_TIME, true);
        repository.initializeSchema();

        assertThatThrownBy(() -> repository.append(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("活动数据库事务");
    }

    /**
     * 验证业务事务回滚会同步回滚 Outbox 事件。
     */
    @Test
    void shouldRollbackEventWithBusinessTransaction() {
        JdbcOutboxRepository repository = repositoryAt(INITIAL_TIME, true);
        repository.initializeSchema();

        transactionTemplate.executeWithoutResult(status -> {
            repository.append(event());
            status.setRollbackOnly();
        });

        assertThat(repository.claimBatch("owner-a", 10, Duration.ofSeconds(30))).isEmpty();
    }

    /**
     * 验证不同发布实例只能有一个取得同一事件的租约。
     */
    @Test
    void shouldClaimEventOnlyOnceAcrossInstances() {
        JdbcOutboxRepository first = repositoryAt(INITIAL_TIME, true);
        JdbcOutboxRepository second = repositoryAt(INITIAL_TIME, true);
        first.initializeSchema();
        transactionTemplate.executeWithoutResult(status -> first.append(event()));

        List<OutboxClaim> firstClaims = first.claimBatch("owner-a", 10, Duration.ofSeconds(30));
        List<OutboxClaim> secondClaims = second.claimBatch("owner-b", 10, Duration.ofSeconds(30));

        assertThat(firstClaims).hasSize(1);
        assertThat(firstClaims.getFirst().event().headers()).containsEntry("type", "created");
        assertThat(secondClaims).isEmpty();
    }

    /**
     * 验证失败释放、延时重试、成功确认和历史清理的完整状态转换。
     */
    @Test
    void shouldReleaseRetryPublishAndCleanEvent() {
        JdbcOutboxRepository initial = repositoryAt(INITIAL_TIME, true);
        initial.initializeSchema();
        transactionTemplate.executeWithoutResult(status -> initial.append(event()));
        OutboxClaim firstClaim = initial.claimBatch("owner-a", 1, Duration.ofSeconds(30)).getFirst();

        initial.release(firstClaim, Duration.ofSeconds(10));

        assertThat(repositoryAt(INITIAL_TIME.plusSeconds(9), true)
                .claimBatch("owner-b", 1, Duration.ofSeconds(30))).isEmpty();
        JdbcOutboxRepository retrying = repositoryAt(INITIAL_TIME.plusSeconds(11), true);
        OutboxClaim retryClaim = retrying.claimBatch("owner-b", 1, Duration.ofSeconds(30)).getFirst();
        retrying.markPublished(retryClaim);

        assertThat(retryClaim.attempt()).isEqualTo(2);
        assertThat(retrying.deletePublishedBefore(INITIAL_TIME.plusSeconds(12))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM muskit_outbox", Integer.class)).isZero();
    }

    /**
     * 验证相同聚合键的后一事件会等待前一事件成功发布。
     */
    @Test
    void shouldPreserveOrderingWithinPartitionKey() {
        JdbcOutboxRepository repository = repositoryAt(INITIAL_TIME, true);
        repository.initializeSchema();
        transactionTemplate.executeWithoutResult(status -> {
            repository.append(event());
            repository.append(event());
        });

        List<OutboxClaim> firstBatch = repository.claimBatch("owner-a", 10, Duration.ofSeconds(30));

        assertThat(firstBatch).hasSize(1);
        repository.markPublished(firstBatch.getFirst());
        assertThat(repository.claimBatch("owner-b", 10, Duration.ofSeconds(30))).hasSize(1);
    }

    /**
     * 验证死信统计和人工回放会重置发布尝试次数。
     */
    @Test
    void shouldMarkDeadAndReplayManually() {
        JdbcOutboxRepository repository = repositoryAt(INITIAL_TIME, true);
        repository.initializeSchema();
        OutboxEvent event = event();
        transactionTemplate.executeWithoutResult(status -> repository.append(event));
        OutboxClaim claim = repository.claimBatch("owner-a", 1, Duration.ofSeconds(30)).getFirst();
        repository.markDead(claim, "publish-failure");

        assertThat(repository.countDead()).isEqualTo(1L);
        assertThat(repository.replayDead(event.id())).isTrue();
        assertThat(repository.claimBatch("owner-b", 1, Duration.ofSeconds(30)).getFirst().attempt())
                .isEqualTo(1);
    }

    /**
     * 创建使用固定时钟的 JDBC Outbox 存储。
     *
     * @param instant 当前时间
     * @param requireTransaction 是否要求写入事务
     * @return JDBC Outbox 存储
     */
    private JdbcOutboxRepository repositoryAt(Instant instant, boolean requireTransaction) {
        return new JdbcOutboxRepository(
                jdbcTemplate,
                "muskit_outbox",
                requireTransaction,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    /**
     * 返回第一个应用实例在指定时刻看到的 JDBC Outbox 存储。
     *
     * @param now 当前时刻
     * @return 第一个 Outbox 存储
     */
    @Override
    protected OutboxRepository firstRepositoryAt(Instant now) {
        JdbcOutboxRepository repository = repositoryAt(now, true);
        repository.initializeSchema();
        return repository;
    }

    /**
     * 返回第二个应用实例在指定时刻看到的 JDBC Outbox 存储。
     *
     * @param now 当前时刻
     * @return 第二个 Outbox 存储
     */
    @Override
    protected OutboxRepository secondRepositoryAt(Instant now) {
        JdbcOutboxRepository repository = repositoryAt(now, true);
        repository.initializeSchema();
        return repository;
    }

    /**
     * 在真实 Spring 数据库事务中追加契约测试事件。
     *
     * @param repository Outbox 存储
     * @param event 待追加事件
     */
    @Override
    protected void append(OutboxRepository repository, OutboxEvent event) {
        transactionTemplate.executeWithoutResult(status -> repository.append(event));
    }

    /**
     * 创建测试事件。
     *
     * @return 测试事件
     */
    private OutboxEvent event() {
        return new OutboxEvent(
                UUID.randomUUID(),
                "orders",
                "order-1",
                "payload".getBytes(StandardCharsets.UTF_8),
                Map.of("type", "created"),
                INITIAL_TIME);
    }
}
