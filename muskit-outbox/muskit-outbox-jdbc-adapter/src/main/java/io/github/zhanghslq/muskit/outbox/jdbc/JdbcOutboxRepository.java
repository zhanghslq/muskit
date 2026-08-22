package io.github.zhanghslq.muskit.outbox.jdbc;

import io.github.zhanghslq.muskit.outbox.exception.OutboxOwnershipLostException;
import io.github.zhanghslq.muskit.outbox.exception.OutboxRepositoryException;
import io.github.zhanghslq.muskit.outbox.model.OutboxClaim;
import io.github.zhanghslq.muskit.outbox.model.OutboxEvent;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 使用条件更新和有期限租约实现的 JDBC Transactional Outbox 存储。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class JdbcOutboxRepository implements OutboxRepository {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DEAD = "DEAD";
    private static final int MAX_HEADER_COUNT = 64;
    private static final int MAX_HEADER_BYTES = 1_048_576;

    private final JdbcOperations jdbcOperations;
    private final String tableName;
    private final boolean requireTransaction;
    private final Clock clock;

    /**
     * 使用 UTC 系统时钟创建 JDBC Outbox 存储。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param tableName Outbox 表名
     * @param requireTransaction 追加事件时是否强制要求活动事务
     */
    public JdbcOutboxRepository(
            JdbcOperations jdbcOperations,
            String tableName,
            boolean requireTransaction) {
        this(jdbcOperations, tableName, requireTransaction, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建 JDBC Outbox 存储。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param tableName Outbox 表名
     * @param requireTransaction 追加事件时是否强制要求活动事务
     * @param clock 状态机时钟
     */
    public JdbcOutboxRepository(
            JdbcOperations jdbcOperations,
            String tableName,
            boolean requireTransaction,
            Clock clock) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "JdbcOperations 不能为空");
        Objects.requireNonNull(tableName, "Outbox 表名不能为空");
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Outbox 表名只能包含字母、数字和下划线，且必须以字母开头");
        }
        this.tableName = tableName;
        this.requireTransaction = requireTransaction;
        this.clock = Objects.requireNonNull(clock, "Outbox 状态机时钟不能为空");
    }

    /**
     * 创建 Outbox 表，已存在时保持不变。
     */
    public void initializeSchema() {
        try {
            jdbcOperations.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "event_id VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "destination VARCHAR(255) NOT NULL, "
                    + "partition_key VARCHAR(512), "
                    + "payload BLOB NOT NULL, "
                    + "headers_data BLOB NOT NULL, "
                    + "status VARCHAR(16) NOT NULL, "
                    + "owner_token VARCHAR(64), "
                    + "attempt_count INTEGER NOT NULL, "
                    + "created_at TIMESTAMP(6) NOT NULL, "
                    + "available_at TIMESTAMP(6) NOT NULL, "
                    + "lease_expires_at TIMESTAMP(6), "
                    + "published_at TIMESTAMP(6), "
                    + "failure_code VARCHAR(128))");
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("schema-initialization", exception);
        }
    }

    /**
     * 在当前业务事务中追加待发布事件。
     *
     * @param event Outbox 事件
     */
    @Override
    public void append(OutboxEvent event) {
        Objects.requireNonNull(event, "Outbox 事件不能为空");
        if (requireTransaction && !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("写入 Outbox 事件必须处于活动数据库事务中");
        }
        try {
            jdbcOperations.update(
                    "INSERT INTO " + tableName
                            + " (event_id, destination, partition_key, payload, headers_data, status, "
                            + "owner_token, attempt_count, created_at, available_at, lease_expires_at, published_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, NULL, 0, ?, ?, NULL, NULL)",
                    event.id().toString(),
                    event.destination(),
                    event.key().isBlank() ? null : event.key(),
                    event.payload(),
                    encodeHeaders(event.headers()),
                    PENDING,
                    Timestamp.from(event.createdAt()),
                    Timestamp.from(event.createdAt()));
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("append", exception);
        }
    }

    /**
     * 使用标准 JDBC 最大行数限制候选扫描，并通过条件更新逐条竞争租约。
     *
     * @param ownerToken 发布实例令牌
     * @param batchSize 最大批量大小
     * @param leaseTime 发布租约时间
     * @return 当前实例取得的发布租约
     */
    @Override
    public List<OutboxClaim> claimBatch(String ownerToken, int batchSize, Duration leaseTime) {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Outbox 发布实例令牌不能为空");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox 批量大小必须大于 0");
        }
        requirePositive(leaseTime, "Outbox 发布租约必须大于 0");
        Instant now = clock.instant();
        Timestamp nowTimestamp = Timestamp.from(now);
        try {
            int scanLimit = (int) Math.min(10_000L, Math.max((long) batchSize, (long) batchSize * 4L));
            List<Candidate> candidates = jdbcOperations.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT o.event_id, o.destination, o.partition_key, o.payload, o.headers_data, "
                                + "o.attempt_count, o.created_at FROM " + tableName + " o "
                                + "WHERE ((o.status = ? AND o.available_at <= ?) "
                                + "OR (o.status = ? AND o.lease_expires_at <= ?)) "
                                + "AND (o.partition_key IS NULL OR NOT EXISTS (SELECT 1 FROM " + tableName + " older "
                                + "WHERE older.partition_key = o.partition_key AND older.status <> ? AND "
                                + "(older.created_at < o.created_at OR "
                                + "(older.created_at = o.created_at AND older.event_id < o.event_id)))) "
                                + "ORDER BY o.created_at, o.event_id");
                statement.setString(1, PENDING);
                statement.setTimestamp(2, nowTimestamp);
                statement.setString(3, PROCESSING);
                statement.setTimestamp(4, nowTimestamp);
                statement.setString(5, PUBLISHED);
                statement.setMaxRows(scanLimit);
                return statement;
            }, (resultSet, rowNumber) -> new Candidate(
                    new OutboxEvent(
                            UUID.fromString(resultSet.getString("event_id")),
                            resultSet.getString("destination"),
                            resultSet.getString("partition_key"),
                            resultSet.getBytes("payload"),
                            decodeHeaders(resultSet.getBytes("headers_data")),
                            resultSet.getTimestamp("created_at").toInstant()),
                    resultSet.getInt("attempt_count")));

            List<OutboxClaim> claimed = new ArrayList<>(batchSize);
            for (Candidate candidate : candidates) {
                if (claimed.size() >= batchSize) {
                    break;
                }
                int updated = jdbcOperations.update(
                        "UPDATE " + tableName + " SET status = ?, owner_token = ?, lease_expires_at = ?, "
                                + "attempt_count = attempt_count + 1 WHERE event_id = ? AND "
                                + "((status = ? AND available_at <= ?) OR "
                                + "(status = ? AND lease_expires_at <= ?))",
                        PROCESSING,
                        ownerToken,
                        Timestamp.from(now.plus(leaseTime)),
                        candidate.event.id().toString(),
                        PENDING,
                        nowTimestamp,
                        PROCESSING,
                        nowTimestamp);
                if (updated == 1) {
                    claimed.add(new OutboxClaim(candidate.event, ownerToken, candidate.attemptCount + 1));
                }
            }
            return List.copyOf(claimed);
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("claim", exception);
        }
    }

    /**
     * 仅由当前租约所有者将事件标记为已发布。
     *
     * @param claim 发布租约
     */
    @Override
    public void markPublished(OutboxClaim claim) {
        Objects.requireNonNull(claim, "Outbox 发布租约不能为空");
        try {
            int updated = jdbcOperations.update(
                    "UPDATE " + tableName + " SET status = ?, owner_token = NULL, lease_expires_at = NULL, "
                            + "published_at = ? WHERE event_id = ? AND status = ? AND owner_token = ?",
                    PUBLISHED,
                    Timestamp.from(clock.instant()),
                    claim.event().id().toString(),
                    PROCESSING,
                    claim.ownerToken());
            if (updated != 1) {
                throw new OutboxOwnershipLostException();
            }
        } catch (OutboxOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("mark-published", exception);
        }
    }

    /**
     * 仅由当前租约所有者释放失败事件并延后可用时间。
     *
     * @param claim 发布租约
     * @param retryDelay 再次发布前等待时间
     */
    @Override
    public void release(OutboxClaim claim, Duration retryDelay) {
        Objects.requireNonNull(claim, "Outbox 发布租约不能为空");
        requireNonNegative(retryDelay, "Outbox 重试等待时间不能为负数");
        try {
            int updated = jdbcOperations.update(
                    "UPDATE " + tableName + " SET status = ?, owner_token = NULL, lease_expires_at = NULL, "
                            + "available_at = ? WHERE event_id = ? AND status = ? AND owner_token = ?",
                    PENDING,
                    Timestamp.from(clock.instant().plus(retryDelay)),
                    claim.event().id().toString(),
                    PROCESSING,
                    claim.ownerToken());
            if (updated != 1) {
                throw new OutboxOwnershipLostException();
            }
        } catch (OutboxOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("release", exception);
        }
    }

    /**
     * 统计待发布和正在租约中的事件数量。
     *
     * @return 尚未成功发布的事件数量
     */
    @Override
    public long countPending() {
        try {
            Long count = jdbcOperations.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE status IN (?, ?)",
                    Long.class,
                    PENDING,
                    PROCESSING);
            return count == null ? 0L : count;
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("count-pending", exception);
        }
    }

    /**
     * 仅由当前所有者把达到最大尝试次数的事件标记为死信。
     *
     * @param claim 发布租约
     * @param reasonCode 低基数失败原因编码
     */
    @Override
    public void markDead(OutboxClaim claim, String reasonCode) {
        Objects.requireNonNull(claim, "Outbox 发布租约不能为空");
        if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 128) {
            throw new IllegalArgumentException("Outbox 死信原因编码不能为空且长度不能超过 128");
        }
        try {
            int updated = jdbcOperations.update(
                    "UPDATE " + tableName + " SET status = ?, owner_token = NULL, lease_expires_at = NULL, "
                            + "failure_code = ? WHERE event_id = ? AND status = ? AND owner_token = ?",
                    DEAD,
                    reasonCode,
                    claim.event().id().toString(),
                    PROCESSING,
                    claim.ownerToken());
            if (updated != 1) {
                throw new OutboxOwnershipLostException();
            }
        } catch (OutboxOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("mark-dead", exception);
        }
    }

    /**
     * 将指定死信事件恢复为立即可发布状态并重置尝试次数。
     *
     * @param eventId Outbox 事件标识
     * @return 是否成功恢复
     */
    @Override
    public boolean replayDead(UUID eventId) {
        Objects.requireNonNull(eventId, "Outbox 事件标识不能为空");
        try {
            return jdbcOperations.update(
                    "UPDATE " + tableName + " SET status = ?, attempt_count = 0, available_at = ?, "
                            + "failure_code = NULL WHERE event_id = ? AND status = ?",
                    PENDING,
                    Timestamp.from(clock.instant()),
                    eventId.toString(),
                    DEAD) == 1;
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("replay-dead", exception);
        }
    }

    /**
     * 统计死信事件数量。
     *
     * @return 死信数量
     */
    @Override
    public long countDead() {
        try {
            Long count = jdbcOperations.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE status = ?",
                    Long.class,
                    DEAD);
            return count == null ? 0L : count;
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("count-dead", exception);
        }
    }

    /**
     * 删除指定时间之前已经发布的历史记录。
     *
     * @param cutoff 发布时间截止点
     * @return 删除数量
     */
    @Override
    public int deletePublishedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "Outbox 清理截止时间不能为空");
        try {
            return jdbcOperations.update(
                    "DELETE FROM " + tableName + " WHERE status = ? AND published_at < ?",
                    PUBLISHED,
                    Timestamp.from(cutoff));
        } catch (RuntimeException exception) {
            throw new OutboxRepositoryException("delete-published", exception);
        }
    }

    /**
     * 将消息头编码为有界二进制格式。
     *
     * @param headers 消息头
     * @return 编码字节
     */
    private byte[] encodeHeaders(Map<String, String> headers) {
        if (headers.size() > MAX_HEADER_COUNT) {
            throw new IllegalArgumentException("Outbox 消息头数量不能超过 " + MAX_HEADER_COUNT);
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(headers.size());
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    writeString(output, header.getKey());
                    writeString(output, header.getValue());
                }
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码 Outbox 消息头", exception);
        }
    }

    /**
     * 解码持久化消息头。
     *
     * @param encoded 编码字节
     * @return 消息头
     */
    private Map<String, String> decodeHeaders(byte[] encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int count = input.readInt();
            if (count < 0 || count > MAX_HEADER_COUNT) {
                throw new IllegalArgumentException("Outbox 消息头数量无效");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                headers.put(readString(input), readString(input));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Outbox 消息头编码包含多余数据");
            }
            return Map.copyOf(headers);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法解码 Outbox 消息头", exception);
        }
    }

    /**
     * 写入带长度的 UTF-8 消息头字段。
     *
     * @param output 二进制输出
     * @param value 字段值
     * @throws IOException 写入失败
     */
    private void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_HEADER_BYTES) {
            throw new IllegalArgumentException("Outbox 消息头字段过长");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    /**
     * 读取带长度的 UTF-8 消息头字段。
     *
     * @param input 二进制输入
     * @return 字段值
     * @throws IOException 读取失败
     */
    private String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_HEADER_BYTES) {
            throw new IllegalArgumentException("Outbox 消息头字段长度无效");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Outbox 消息头字段不完整");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 校验正数时间。
     *
     * @param duration 时间长度
     * @param message 校验消息
     */
    private void requirePositive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验非负时间。
     *
     * @param duration 时间长度
     * @param message 校验消息
     */
    private void requireNonNegative(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 保存候选事件及竞争前尝试次数。
     *
     * @param event 候选事件
     * @param attemptCount 已尝试次数
     * @author zhs
     * @since 2026-08-20
     */
    private record Candidate(OutboxEvent event, int attemptCount) {
    }
}
