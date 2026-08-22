package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import io.github.zhanghslq.muskit.idempotency.annotation.Idempotent;
import io.github.zhanghslq.muskit.idempotency.autoconfigure.aspect.IdempotentAspect;
import io.github.zhanghslq.muskit.idempotency.autoconfigure.jdbc.MuskitIdempotencyJdbcAutoConfiguration;
import io.github.zhanghslq.muskit.idempotency.autoconfigure.redis.MuskitIdempotencyRedisAutoConfiguration;
import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyCompletedException;
import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyInProgressException;
import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyOwnershipLostException;
import io.github.zhanghslq.muskit.idempotency.jdbc.JdbcIdempotencyStore;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.redis.RedisIdempotencyStore;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Muskit 幂等自动配置与切面行为测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitIdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MuskitIdempotencyRedisAutoConfiguration.class,
                    MuskitIdempotencyJdbcAutoConfiguration.class,
                    MuskitIdempotencyAutoConfiguration.class));

    /**
     * 验证同步成功、同步失败和异步完成都严格驱动幂等状态转换。
     */
    @Test
    void shouldApplyIdempotencyLifecycleToSynchronousAndAsynchronousMethods() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            TestService service = context.getBean(TestService.class);

            assertThat(service.succeed("sync-success")).isEqualTo("sync-success");
            assertThatThrownBy(() -> service.succeed("sync-success"))
                    .isInstanceOf(IdempotencyCompletedException.class);

            assertThatThrownBy(() -> service.fail("sync-failure"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("business failure");
            assertThatThrownBy(() -> service.fail("sync-failure"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("business failure");

            CompletableFuture<String> businessResult = new CompletableFuture<>();
            assertThat(service.async("async-success", businessResult)).isNotCompleted();
            assertThatThrownBy(() -> service.async("async-success", new CompletableFuture<>()))
                    .isInstanceOf(IdempotencyInProgressException.class);
            businessResult.complete("completed");
            assertThatThrownBy(() -> service.async("async-success", new CompletableFuture<>()))
                    .isInstanceOf(IdempotencyCompletedException.class);
        });
    }

    /**
     * 验证显式禁用后不创建切面且业务调用不受幂等状态影响。
     */
    @Test
    void shouldDisableIdempotencyExplicitly() {
        contextRunner.withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("muskit.idempotency.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempotentAspect.class);
                    TestService service = context.getBean(TestService.class);
                    assertThat(service.succeed("disabled")).isEqualTo("disabled");
                    assertThat(service.succeed("disabled")).isEqualTo("disabled");
                });
    }

    /**
     * 验证选择 JDBC Provider 时自动建表并创建 JDBC 状态存储。
     */
    @Test
    void shouldConfigureJdbcProviderAndInitializeSchema() {
        contextRunner.withBean(JdbcOperations.class, this::jdbcOperations)
                .withPropertyValues(
                        "muskit.idempotency.provider=jdbc",
                        "muskit.idempotency.initialize-schema=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context.getBean(IdempotencyStore.class)).isInstanceOf(JdbcIdempotencyStore.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                    Integer tableCount = context.getBean(JdbcOperations.class).queryForObject(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'MUSKIT_IDEMPOTENCY'",
                            Integer.class);
                    assertThat(tableCount).isOne();
                });
    }

    /**
     * 验证默认 Redis Provider 使用应用提供的 RedissonClient。
     */
    @Test
    void shouldConfigureRedisProviderByDefault() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(mock(RScript.class));

        contextRunner.withBean(RedissonClient.class, () -> client).run(context -> {
            assertThat(context).hasSingleBean(IdempotencyStore.class);
            assertThat(context.getBean(IdempotencyStore.class)).isInstanceOf(RedisIdempotencyStore.class);
            assertThat(context).hasSingleBean(IdempotentAspect.class);
        });
    }

    /**
     * 验证启用功能但缺少所选后端时应用明确启动失败。
     */
    @Test
    void shouldFailStartupWhenSelectedProviderIsUnavailable() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    /**
     * 创建测试 JDBC 操作对象。
     *
     * @return 使用独立 H2 数据库的 JDBC 操作对象
     */
    private JdbcOperations jdbcOperations() {
        String databaseName = "autoconfigure_" + UUID.randomUUID().toString().replace("-", "");
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    /**
     * 自动配置测试所需的用户 Bean。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        /**
         * 创建测试配置。
         */
        TestConfiguration() {
        }

        /**
         * 创建内存幂等状态存储。
         *
         * @return 内存状态存储
         */
        @Bean
        RecordingStore recordingStore() {
            return new RecordingStore();
        }

        /**
         * 创建带幂等注解的测试服务。
         *
         * @return 测试服务
         */
        @Bean
        TestService testService() {
            return new TestService();
        }
    }

    /**
     * 用于验证切面生命周期的测试业务服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class TestService {

        /**
         * 创建测试业务服务。
         */
        TestService() {
        }

        /**
         * 返回同步业务结果。
         *
         * @param key 幂等键
         * @return 原业务结果
         */
        @Idempotent(operation = "test-sync", key = "#key")
        String succeed(String key) {
            return key;
        }

        /**
         * 模拟同步业务失败。
         *
         * @param key 幂等键
         * @return 不会正常返回
         */
        @Idempotent(operation = "test-failure", key = "#key")
        String fail(String key) {
            throw new IllegalStateException("business failure");
        }

        /**
         * 返回由测试控制完成时机的异步业务结果。
         *
         * @param key 幂等键
         * @param result 异步业务结果
         * @return 原异步业务结果
         */
        @Idempotent(operation = "test-async", key = "#key")
        CompletableFuture<String> async(String key, CompletableFuture<String> result) {
            return result;
        }
    }

    /**
     * 仅用于切面测试的线程安全内存幂等状态存储。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class RecordingStore implements IdempotencyStore {

        private static final String COMPLETED = "COMPLETED";
        private final ConcurrentMap<String, String> states = new ConcurrentHashMap<>();

        /**
         * 创建内存幂等状态存储。
         */
        RecordingStore() {
        }

        /**
         * 原子获取测试幂等所有权。
         *
         * @param request 幂等请求
         * @return 幂等尝试结果
         */
        @Override
        public IdempotencyAttempt tryStart(IdempotencyRequest request) {
            String identity = identity(request.operation(), request.key());
            String owner = UUID.randomUUID().toString();
            String current = states.putIfAbsent(identity, owner);
            if (current == null) {
                return IdempotencyAttempt.acquired(new IdempotencyClaim(
                        request.operation(), request.key(), owner, request.retention()));
            }
            return IdempotencyAttempt.rejected(
                    COMPLETED.equals(current) ? IdempotencyDecision.COMPLETED : IdempotencyDecision.IN_PROGRESS);
        }

        /**
         * 将当前测试所有权提交为成功状态。
         *
         * @param claim 幂等所有权声明
         */
        @Override
        public void complete(IdempotencyClaim claim) {
            if (!states.replace(identity(claim.operation(), claim.key()), claim.ownerToken(), COMPLETED)) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        }

        /**
         * 释放当前测试所有权。
         *
         * @param claim 幂等所有权声明
         */
        @Override
        public void release(IdempotencyClaim claim) {
            if (!states.remove(identity(claim.operation(), claim.key()), claim.ownerToken())) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        }

        /**
         * 组合测试状态的内部索引。
         *
         * @param operation 操作名称
         * @param key 幂等键
         * @return 内部索引
         */
        private String identity(String operation, String key) {
            return operation + '\0' + key;
        }
    }
}
