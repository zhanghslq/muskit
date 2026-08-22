package io.github.zhanghslq.muskit.cache.autoconfigure;

import io.github.zhanghslq.muskit.cache.model.CacheRecord;
import io.github.zhanghslq.muskit.cache.service.CacheTemplate;
import io.github.zhanghslq.muskit.cache.service.ReliableCache;
import io.github.zhanghslq.muskit.cache.spi.CachePolicyResolver;
import io.github.zhanghslq.muskit.cache.spi.CacheStore;
import io.github.zhanghslq.muskit.executor.model.ExecutorType;
import io.github.zhanghslq.muskit.executor.model.ManagedExecutorConfig;
import io.github.zhanghslq.muskit.executor.service.ManagedExecutorRegistry;
import io.github.zhanghslq.muskit.executor.service.ManagedTaskExecutor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可靠缓存主自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitCacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, MuskitCacheAutoConfiguration.class);

    /**
     * 验证存在存储和受管执行器时创建策略、缓存和模板。
     */
    @Test
    void shouldConfigureReliableCache() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CachePolicyResolver.class);
            assertThat(context).hasSingleBean(ReliableCache.class);
            assertThat(context).hasSingleBean(CacheTemplate.class);
        });
    }

    /**
     * 验证类型安全策略属性可以绑定。
     */
    @Test
    void shouldBindCachePolicy() {
        contextRunner.withPropertyValues("muskit.cache.policies.default.ttl=45s")
                .run(context -> assertThat(context.getBean(CachePolicyResolver.class)
                        .resolve("default").ttl()).isEqualTo(Duration.ofSeconds(45)));
    }

    /**
     * 验证可以显式关闭缓存模块。
     */
    @Test
    void shouldDisableCache() {
        contextRunner.withPropertyValues("muskit.cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReliableCache.class));
    }

    /**
     * 自动配置测试所需的缓存存储和受管执行器。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        /**
         * 创建空缓存存储。
         *
         * @return 缓存存储
         */
        @Bean
        CacheStore cacheStore() {
            return new CacheStore() {
                /** {@inheritDoc} */
                @Override
                public Optional<CacheRecord> get(String cacheName, String key) { return Optional.empty(); }

                /** {@inheritDoc} */
                @Override
                public void put(String cacheName, String key, CacheRecord record, Duration retention) { }

                /** {@inheritDoc} */
                @Override
                public void delete(String cacheName, String key) { }
            };
        }

        /**
         * 创建单个测试受管执行器。
         *
         * @return 执行器注册表
         */
        @Bean(destroyMethod = "close")
        ManagedExecutorRegistry managedExecutorRegistry() {
            return new ManagedExecutorRegistry(List.of(new ManagedTaskExecutor(
                    new ManagedExecutorConfig(
                            "default", ExecutorType.VIRTUAL, 2, 1, Duration.ofSeconds(1)))));
        }
    }
}
