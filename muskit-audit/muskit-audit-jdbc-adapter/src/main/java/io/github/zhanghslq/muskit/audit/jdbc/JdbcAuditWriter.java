package io.github.zhanghslq.muskit.audit.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zhanghslq.muskit.audit.AuditEvent;
import io.github.zhanghslq.muskit.audit.AuditWriteException;
import io.github.zhanghslq.muskit.audit.AuditWriter;

/**
 * 使用 JDBC 参数化 SQL 持久化审计事件的 Writer。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class JdbcAuditWriter implements AuditWriter {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,62}");

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final String insertSql;

    /**
     * 创建 JDBC 审计 Writer。
     *
     * @param dataSource JDBC 数据源
     * @param objectMapper 扩展属性 JSON 编码器
     * @param tableName 审计表名
     */
    public JdbcAuditWriter(DataSource dataSource, ObjectMapper objectMapper, String tableName) {
        this.dataSource = Objects.requireNonNull(dataSource, "审计数据源不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "审计 JSON 编码器不能为空");
        if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("审计表名只能包含字母、数字和下划线，并且必须以字母开头");
        }
        this.insertSql = "INSERT INTO " + tableName + " (event_id, occurred_at, action_name, outcome, actor_id, "
                + "subject_type, subject_id, error_code, attributes_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * 在独立 JDBC 连接中写入审计事件。
     *
     * @param event 审计事件
     */
    @Override
    public void write(AuditEvent event) {
        Objects.requireNonNull(event, "审计事件不能为空");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(insertSql)) {
            bind(statement, event);
            if (statement.executeUpdate() != 1) {
                throw new AuditWriteException(new IllegalStateException("审计写入影响行数不为一"));
            }
        } catch (SQLException | JsonProcessingException exception) {
            throw new AuditWriteException(exception);
        }
    }

    /**
     * 绑定审计事件参数，所有动态值均不拼接到 SQL。
     *
     * @param statement JDBC 语句
     * @param event 审计事件
     * @throws SQLException JDBC 参数绑定失败
     * @throws JsonProcessingException 扩展属性编码失败
     */
    private void bind(PreparedStatement statement, AuditEvent event)
            throws SQLException, JsonProcessingException {
        statement.setString(1, event.eventId());
        statement.setTimestamp(2, Timestamp.from(event.occurredAt()));
        statement.setString(3, event.action());
        statement.setString(4, event.outcome().name());
        statement.setString(5, event.actor().orElse(null));
        statement.setString(6, event.subjectType().orElse(null));
        statement.setString(7, event.subjectId().orElse(null));
        statement.setString(8, event.errorCode().orElse(null));
        statement.setString(9, objectMapper.writeValueAsString(event.attributes()));
    }
}
