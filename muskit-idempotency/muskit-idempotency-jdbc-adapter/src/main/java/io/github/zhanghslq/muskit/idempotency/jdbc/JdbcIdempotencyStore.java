package io.github.zhanghslq.muskit.idempotency.jdbc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import io.github.zhanghslq.muskit.idempotency.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.IdempotencyOwnershipLostException;
import io.github.zhanghslq.muskit.idempotency.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStoreException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 使用唯一主键和所有权条件更新实现 JDBC 幂等状态机。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private final JdbcOperations jdbcOperations;
    private final String tableName;
    private final Clock clock;

    /**
     * 使用 UTC 系统时钟创建 JDBC 幂等存储。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param tableName 幂等状态表名
     */
    public JdbcIdempotencyStore(JdbcOperations jdbcOperations, String tableName) {
        this(jdbcOperations, tableName, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建 JDBC 幂等存储。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param tableName 幂等状态表名
     * @param clock 状态机时钟
     */
    public JdbcIdempotencyStore(JdbcOperations jdbcOperations, String tableName, Clock clock) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "JdbcOperations 不能为空");
        Objects.requireNonNull(tableName, "幂等状态表名不能为空");
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("幂等状态表名只能包含字母、数字和下划线，且必须以字母开头");
        }
        this.tableName = tableName;
        this.clock = Objects.requireNonNull(clock, "状态机时钟不能为空");
    }

    /**
     * 创建幂等状态表，已存在时保持不变。
     */
    public void initializeSchema() {
        try {
            jdbcOperations.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "operation_name VARCHAR(128) NOT NULL, "
                    + "idempotency_key VARCHAR(512) NOT NULL, "
                    + "owner_token VARCHAR(64), "
                    + "status VARCHAR(16) NOT NULL, "
                    + "processing_expires_at TIMESTAMP(6), "
                    + "retention_expires_at TIMESTAMP(6), "
                    + "PRIMARY KEY (operation_name, idempotency_key))");
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException("schema-initialization", exception);
        }
    }

    /**
     * 删除当前业务键的过期状态并通过唯一主键原子竞争处理中记录。
     *
     * @param request 幂等请求
     * @return 幂等尝试结果
     */
    @Override
    public IdempotencyAttempt tryStart(IdempotencyRequest request) {
        Objects.requireNonNull(request, "幂等请求不能为空");
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                Instant now = clock.instant();
                deleteExpired(request, now);
                String ownerToken = UUID.randomUUID().toString();
                try {
                    jdbcOperations.update(
                            "INSERT INTO " + tableName
                                    + " (operation_name, idempotency_key, owner_token, status, "
                                    + "processing_expires_at, retention_expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                            request.operation(),
                            request.key(),
                            ownerToken,
                            PROCESSING,
                            Timestamp.from(now.plus(request.processingTimeout())),
                            null);
                    return IdempotencyAttempt.acquired(new IdempotencyClaim(
                            request.operation(), request.key(), ownerToken, request.retention()));
                } catch (DuplicateKeyException duplicate) {
                    IdempotencyDecision decision = findDecision(request);
                    if (decision != null) {
                        return IdempotencyAttempt.rejected(decision);
                    }
                    // 记录可能在唯一键冲突后立即过期并被其他实例清理，再尝试一次即可收敛。
                }
            }
            throw new IllegalStateException("无法读取并发变化后的幂等状态");
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(request.operation(), exception);
        }
    }

    /**
     * 仅由当前所有者将处理中记录转换为成功状态。
     *
     * @param claim 幂等所有权声明
     */
    @Override
    public void complete(IdempotencyClaim claim) {
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        try {
            int updated = jdbcOperations.update(
                    "UPDATE " + tableName + " SET status = ?, owner_token = NULL, "
                            + "processing_expires_at = NULL, retention_expires_at = ? "
                            + "WHERE operation_name = ? AND idempotency_key = ? AND status = ? AND owner_token = ? "
                            + "AND processing_expires_at > ?",
                    COMPLETED,
                    Timestamp.from(clock.instant().plus(claim.retention())),
                    claim.operation(),
                    claim.key(),
                    PROCESSING,
                    claim.ownerToken(),
                    Timestamp.from(clock.instant()));
            if (updated != 1) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        } catch (IdempotencyOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(claim.operation(), exception);
        }
    }

    /**
     * 仅由当前所有者删除失败的处理中记录。
     *
     * @param claim 幂等所有权声明
     */
    @Override
    public void release(IdempotencyClaim claim) {
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        try {
            int deleted = jdbcOperations.update(
                    "DELETE FROM " + tableName
                            + " WHERE operation_name = ? AND idempotency_key = ? AND status = ? AND owner_token = ? "
                            + "AND processing_expires_at > ?",
                    claim.operation(),
                    claim.key(),
                    PROCESSING,
                    claim.ownerToken(),
                    Timestamp.from(clock.instant()));
            if (deleted != 1) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        } catch (IdempotencyOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(claim.operation(), exception);
        }
    }

    /**
     * 删除当前操作和业务键已经过期的处理中或成功状态。
     *
     * @param request 幂等请求
     * @param now 当前时间
     */
    private void deleteExpired(IdempotencyRequest request, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        jdbcOperations.update(
                "DELETE FROM " + tableName + " WHERE operation_name = ? AND idempotency_key = ? AND "
                        + "((status = ? AND processing_expires_at <= ?) "
                        + "OR (status = ? AND retention_expires_at <= ?))",
                request.operation(), request.key(), PROCESSING, timestamp, COMPLETED, timestamp);
    }

    /**
     * 读取当前业务键的幂等状态。
     *
     * @param request 幂等请求
     * @return 已有状态判定，记录不存在时返回空
     */
    private IdempotencyDecision findDecision(IdempotencyRequest request) {
        List<String> statuses = jdbcOperations.query(
                "SELECT status FROM " + tableName + " WHERE operation_name = ? AND idempotency_key = ?",
                (resultSet, rowNumber) -> resultSet.getString(1),
                request.operation(),
                request.key());
        if (statuses.isEmpty()) {
            return null;
        }
        return COMPLETED.equals(statuses.getFirst())
                ? IdempotencyDecision.COMPLETED
                : IdempotencyDecision.IN_PROGRESS;
    }
}
