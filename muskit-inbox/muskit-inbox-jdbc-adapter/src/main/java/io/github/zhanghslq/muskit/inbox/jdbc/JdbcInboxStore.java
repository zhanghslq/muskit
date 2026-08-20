package io.github.zhanghslq.muskit.inbox.jdbc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import io.github.zhanghslq.muskit.inbox.InboxAttempt;
import io.github.zhanghslq.muskit.inbox.InboxClaim;
import io.github.zhanghslq.muskit.inbox.InboxDecision;
import io.github.zhanghslq.muskit.inbox.InboxOwnershipLostException;
import io.github.zhanghslq.muskit.inbox.InboxRequest;
import io.github.zhanghslq.muskit.inbox.InboxStatus;
import io.github.zhanghslq.muskit.inbox.InboxStore;
import io.github.zhanghslq.muskit.inbox.InboxStoreException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 使用条件更新和有期限租约实现的 JDBC Inbox 状态存储。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class JdbcInboxStore implements InboxStore {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final JdbcOperations jdbcOperations;
    private final String tableName;
    private final Clock clock;

    /**
     * 使用 UTC 系统时钟创建 JDBC Inbox 存储。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param tableName Inbox 表名
     */
    public JdbcInboxStore(JdbcOperations jdbcOperations, String tableName) {
        this(jdbcOperations, tableName, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建 JDBC Inbox 存储。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param tableName Inbox 表名
     * @param clock 状态机时钟
     */
    public JdbcInboxStore(JdbcOperations jdbcOperations, String tableName, Clock clock) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "JdbcOperations 不能为空");
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Inbox 表名只能包含字母、数字和下划线，且必须以字母开头");
        }
        this.tableName = tableName;
        this.clock = Objects.requireNonNull(clock, "Inbox 状态机时钟不能为空");
    }

    /**
     * 创建 Inbox 表，已经存在时保持不变。
     */
    public void initializeSchema() {
        try {
            jdbcOperations.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "consumer_name VARCHAR(128) NOT NULL, "
                    + "message_id VARCHAR(512) NOT NULL, "
                    + "status VARCHAR(16) NOT NULL, "
                    + "owner_token VARCHAR(64), "
                    + "attempt_count INTEGER NOT NULL, "
                    + "processing_expires_at TIMESTAMP(6), "
                    + "available_at TIMESTAMP(6) NOT NULL, "
                    + "completed_at TIMESTAMP(6), "
                    + "expires_at TIMESTAMP(6), "
                    + "failure_code VARCHAR(128), "
                    + "PRIMARY KEY (consumer_name, message_id))");
        } catch (RuntimeException failure) {
            throw new InboxStoreException("schema-initialization", failure);
        }
    }

    /**
     * 插入新状态或通过条件更新接管过期处理、到期重试和过期成功记录。
     *
     * @param request Inbox 请求
     * @return 原子竞争结果
     */
    @Override
    public InboxAttempt tryClaim(InboxRequest request) {
        Objects.requireNonNull(request, "Inbox 请求不能为空");
        Instant now = clock.instant();
        String ownerToken = UUID.randomUUID().toString();
        DataAccessException insertFailure;
        try {
            jdbcOperations.update(
                    "INSERT INTO " + tableName
                            + " (consumer_name, message_id, status, owner_token, attempt_count, "
                            + "processing_expires_at, available_at, completed_at, expires_at, failure_code) "
                            + "VALUES (?, ?, ?, ?, 1, ?, ?, NULL, NULL, NULL)",
                    request.consumer(),
                    request.messageId(),
                    InboxStatus.PROCESSING.name(),
                    ownerToken,
                    Timestamp.from(now.plus(request.processingTimeout())),
                    Timestamp.from(now));
            return InboxAttempt.acquired(new InboxClaim(
                    request.consumer(), request.messageId(), ownerToken, 1, request.retention()));
        } catch (DataAccessException duplicateOrFailure) {
            insertFailure = duplicateOrFailure;
        }

        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                Row row = load(request.consumer(), request.messageId());
                if (row == null) {
                    throw new InboxStoreException("claim", insertFailure);
                }
                InboxAttempt decision = decide(row, request, now);
                if (decision != null) {
                    return decision;
                }
                int updated = jdbcOperations.update(
                        "UPDATE " + tableName + " SET status = ?, owner_token = ?, "
                                + "attempt_count = CASE WHEN status = ? THEN 1 ELSE attempt_count + 1 END, "
                                + "processing_expires_at = ?, available_at = ?, completed_at = NULL, "
                                + "expires_at = NULL, failure_code = NULL WHERE consumer_name = ? AND message_id = ? AND "
                                + "((status = ? AND processing_expires_at <= ?) "
                                + "OR (status = ? AND available_at <= ?) "
                                + "OR (status = ? AND expires_at <= ?))",
                        InboxStatus.PROCESSING.name(),
                        ownerToken,
                        InboxStatus.SUCCEEDED.name(),
                        Timestamp.from(now.plus(request.processingTimeout())),
                        Timestamp.from(now),
                        request.consumer(),
                        request.messageId(),
                        InboxStatus.PROCESSING.name(),
                        Timestamp.from(now),
                        InboxStatus.RETRY_WAIT.name(),
                        Timestamp.from(now),
                        InboxStatus.SUCCEEDED.name(),
                        Timestamp.from(now));
                if (updated == 1) {
                    Row claimed = load(request.consumer(), request.messageId());
                    return InboxAttempt.acquired(new InboxClaim(
                            request.consumer(),
                            request.messageId(),
                            ownerToken,
                            claimed.attemptCount(),
                            request.retention()));
                }
            }
            throw new InboxStoreException("claim-contention", insertFailure);
        } catch (InboxStoreException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new InboxStoreException("claim", failure);
        }
    }

    /**
     * 仅由当前所有者提交成功状态和保留期限。
     *
     * @param claim 处理租约
     */
    @Override
    public void complete(InboxClaim claim) {
        Objects.requireNonNull(claim, "Inbox 处理租约不能为空");
        Instant now = clock.instant();
        updateOwned(
                "UPDATE " + tableName + " SET status = ?, owner_token = NULL, processing_expires_at = NULL, "
                        + "completed_at = ?, expires_at = ?, failure_code = NULL "
                        + "WHERE consumer_name = ? AND message_id = ? AND status = ? AND owner_token = ?",
                "complete",
                InboxStatus.SUCCEEDED.name(),
                Timestamp.from(now),
                Timestamp.from(now.plus(claim.retention())),
                claim.consumer(),
                claim.messageId(),
                InboxStatus.PROCESSING.name(),
                claim.ownerToken());
    }

    /**
     * 仅由当前所有者设置下一次可处理时间。
     *
     * @param claim 处理租约
     * @param retryDelay 重试等待时间
     * @param reasonCode 低基数失败原因编码
     */
    @Override
    public void retry(InboxClaim claim, Duration retryDelay, String reasonCode) {
        validateTransition(claim, retryDelay, reasonCode);
        updateOwned(
                "UPDATE " + tableName + " SET status = ?, owner_token = NULL, processing_expires_at = NULL, "
                        + "available_at = ?, failure_code = ? "
                        + "WHERE consumer_name = ? AND message_id = ? AND status = ? AND owner_token = ?",
                "retry",
                InboxStatus.RETRY_WAIT.name(),
                Timestamp.from(clock.instant().plus(retryDelay)),
                reasonCode,
                claim.consumer(),
                claim.messageId(),
                InboxStatus.PROCESSING.name(),
                claim.ownerToken());
    }

    /**
     * 仅由当前所有者把消息标记为死信。
     *
     * @param claim 处理租约
     * @param reasonCode 低基数失败原因编码
     */
    @Override
    public void markDead(InboxClaim claim, String reasonCode) {
        validateTransition(claim, Duration.ZERO, reasonCode);
        updateOwned(
                "UPDATE " + tableName + " SET status = ?, owner_token = NULL, processing_expires_at = NULL, "
                        + "failure_code = ? WHERE consumer_name = ? AND message_id = ? AND status = ? AND owner_token = ?",
                "mark-dead",
                InboxStatus.DEAD.name(),
                reasonCode,
                claim.consumer(),
                claim.messageId(),
                InboxStatus.PROCESSING.name(),
                claim.ownerToken());
    }

    /**
     * 将死信状态恢复为立即可竞争的重试状态并重置尝试次数。
     *
     * @param consumer 消费者名称
     * @param messageId 业务消息 ID
     * @return 是否成功恢复
     */
    @Override
    public boolean replayDead(String consumer, String messageId) {
        validateIdentity(consumer, messageId);
        try {
            return jdbcOperations.update(
                    "UPDATE " + tableName + " SET status = ?, attempt_count = 0, available_at = ?, "
                            + "failure_code = NULL WHERE consumer_name = ? AND message_id = ? AND status = ?",
                    InboxStatus.RETRY_WAIT.name(),
                    Timestamp.from(clock.instant()),
                    consumer,
                    messageId,
                    InboxStatus.DEAD.name()) == 1;
        } catch (RuntimeException failure) {
            throw new InboxStoreException("replay-dead", failure);
        }
    }

    /**
     * 统计指定消费者的死信数量。
     *
     * @param consumer 消费者名称
     * @return 死信数量
     */
    @Override
    public long countDead(String consumer) {
        if (consumer == null || consumer.isBlank()) {
            throw new IllegalArgumentException("Inbox 消费者名称不能为空");
        }
        try {
            Long count = jdbcOperations.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE consumer_name = ? AND status = ?",
                    Long.class,
                    consumer,
                    InboxStatus.DEAD.name());
            return count == null ? 0L : count;
        } catch (RuntimeException failure) {
            throw new InboxStoreException("count-dead", failure);
        }
    }

    /**
     * 将已有状态转换为无需竞争或需要条件接管的判定。
     *
     * @param row 当前状态行
     * @param request Inbox 请求
     * @param now 当前时刻
     * @return 已确定的判定；需要尝试接管时返回空
     */
    private InboxAttempt decide(Row row, InboxRequest request, Instant now) {
        if (row.status() == InboxStatus.DEAD) {
            return InboxAttempt.rejected(InboxDecision.DEAD, Duration.ZERO);
        }
        if (row.status() == InboxStatus.SUCCEEDED && row.expiresAt().isAfter(now)) {
            return InboxAttempt.rejected(InboxDecision.SUCCEEDED, Duration.ZERO);
        }
        if (row.status() == InboxStatus.PROCESSING && row.processingExpiresAt().isAfter(now)) {
            return InboxAttempt.rejected(
                    InboxDecision.IN_PROGRESS,
                    Duration.between(now, row.processingExpiresAt()));
        }
        if (row.status() == InboxStatus.RETRY_WAIT && row.availableAt().isAfter(now)) {
            return InboxAttempt.rejected(
                    InboxDecision.RETRY_LATER,
                    Duration.between(now, row.availableAt()));
        }
        return null;
    }

    /**
     * 读取指定消息当前状态。
     *
     * @param consumer 消费者名称
     * @param messageId 消息 ID
     * @return 状态行，不存在时为空
     */
    private Row load(String consumer, String messageId) {
        List<Row> rows = jdbcOperations.query(
                "SELECT status, attempt_count, processing_expires_at, available_at, expires_at FROM "
                        + tableName + " WHERE consumer_name = ? AND message_id = ?",
                (resultSet, rowNumber) -> new Row(
                        InboxStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("attempt_count"),
                        instant(resultSet.getTimestamp("processing_expires_at")),
                        instant(resultSet.getTimestamp("available_at")),
                        instant(resultSet.getTimestamp("expires_at"))),
                consumer,
                messageId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * 执行带所有权条件的更新并统一转换异常。
     *
     * @param sql 更新 SQL
     * @param operation 低基数操作名称
     * @param arguments SQL 参数
     */
    private void updateOwned(String sql, String operation, Object... arguments) {
        try {
            if (jdbcOperations.update(sql, arguments) != 1) {
                throw new InboxOwnershipLostException();
            }
        } catch (InboxOwnershipLostException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new InboxStoreException(operation, failure);
        }
    }

    /**
     * 校验失败状态转换参数。
     *
     * @param claim 处理租约
     * @param retryDelay 重试时间
     * @param reasonCode 原因编码
     */
    private void validateTransition(InboxClaim claim, Duration retryDelay, String reasonCode) {
        Objects.requireNonNull(claim, "Inbox 处理租约不能为空");
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Inbox 重试等待时间不能为负数");
        }
        if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 128) {
            throw new IllegalArgumentException("Inbox 失败原因编码不能为空且长度不能超过 128");
        }
    }

    /**
     * 校验人工回放消息身份。
     *
     * @param consumer 消费者名称
     * @param messageId 消息 ID
     */
    private void validateIdentity(String consumer, String messageId) {
        if (consumer == null || consumer.isBlank() || messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Inbox 消费者名称和业务消息 ID 不能为空");
        }
    }

    /**
     * 将可空时间戳转换为时刻。
     *
     * @param timestamp JDBC 时间戳
     * @return 时刻，可为空
     */
    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * 保存竞争判定所需的持久化状态。
     *
     * @param status 状态
     * @param attemptCount 已处理次数
     * @param processingExpiresAt 处理租约截止时间
     * @param availableAt 下次可处理时间
     * @param expiresAt 成功状态失效时间
     * @author zhs
     * @since 2026-08-20
     */
    private record Row(
            InboxStatus status,
            int attemptCount,
            Instant processingExpiresAt,
            Instant availableAt,
            Instant expiresAt) {
    }
}
