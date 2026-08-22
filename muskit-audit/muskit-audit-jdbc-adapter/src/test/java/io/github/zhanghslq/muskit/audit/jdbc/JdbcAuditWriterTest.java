package io.github.zhanghslq.muskit.audit.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zhanghslq.muskit.audit.model.AuditEvent;
import io.github.zhanghslq.muskit.audit.model.AuditOutcome;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JDBC 审计 Writer 测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class JdbcAuditWriterTest {

    /**
     * 验证审计事件通过参数化语句写入。
     *
     * @throws Exception JDBC 模拟异常
     */
    @Test
    void shouldWriteParameterizedEvent() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.startsWith("INSERT INTO muskit_audit")))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        JdbcAuditWriter writer = new JdbcAuditWriter(dataSource, new ObjectMapper(), "muskit_audit");
        AuditEvent event = new AuditEvent("event-1", Instant.parse("2026-08-20T00:00:00Z"),
                "order.cancel", AuditOutcome.SUCCESS, null, "order", "id-1", null, Map.of());

        writer.write(event);

        verify(statement).setString(7, "id-1");
        verify(statement).executeUpdate();
    }

    /**
     * 验证不可信表名被拒绝。
     */
    @Test
    void shouldRejectUnsafeTableName() {
        assertThatThrownBy(() -> new JdbcAuditWriter(
                mock(DataSource.class), new ObjectMapper(), "audit; drop table users"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
