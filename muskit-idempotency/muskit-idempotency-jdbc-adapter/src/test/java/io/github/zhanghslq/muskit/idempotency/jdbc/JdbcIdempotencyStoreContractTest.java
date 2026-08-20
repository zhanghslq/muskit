package io.github.zhanghslq.muskit.idempotency.jdbc;

import java.util.UUID;

import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import io.github.zhanghslq.muskit.test.idempotency.IdempotencyStoreContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * JDBC 幂等状态存储契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class JdbcIdempotencyStoreContractTest extends IdempotencyStoreContract {

    private final JdbcIdempotencyStore firstStore;
    private final JdbcIdempotencyStore secondStore;

    /**
     * 创建共享同一 H2 数据库的两个 JDBC 存储实例。
     */
    JdbcIdempotencyStoreContractTest() {
        String databaseName = "contract_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1";
        JdbcTemplate firstJdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        JdbcTemplate secondJdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        this.firstStore = new JdbcIdempotencyStore(firstJdbc, "muskit_idempotency");
        this.secondStore = new JdbcIdempotencyStore(secondJdbc, "muskit_idempotency");
        this.firstStore.initializeSchema();
    }

    /**
     * 返回第一个 JDBC 存储实例。
     *
     * @return 第一个 JDBC 存储实例
     */
    @Override
    protected IdempotencyStore firstStore() {
        return firstStore;
    }

    /**
     * 返回第二个 JDBC 存储实例。
     *
     * @return 第二个 JDBC 存储实例
     */
    @Override
    protected IdempotencyStore secondStore() {
        return secondStore;
    }
}
