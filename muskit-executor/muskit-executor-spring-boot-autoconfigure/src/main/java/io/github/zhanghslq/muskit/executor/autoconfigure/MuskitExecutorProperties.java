package io.github.zhanghslq.muskit.executor.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.zhanghslq.muskit.executor.ExecutorType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit 受管执行器配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.executor")
public class MuskitExecutorProperties {

    private boolean enabled = true;
    private Map<String, ExecutorSpec> executors = defaultExecutors();

    /**
     * 创建默认受管执行器配置。
     */
    public MuskitExecutorProperties() {
    }

    /**
     * 返回是否启用受管执行器。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用受管执行器。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回按名称索引的执行器配置。
     *
     * @return 执行器配置
     */
    public Map<String, ExecutorSpec> getExecutors() {
        return executors;
    }

    /**
     * 设置按名称索引的执行器配置。
     *
     * @param executors 执行器配置
     */
    public void setExecutors(Map<String, ExecutorSpec> executors) {
        if (executors == null || executors.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个受管执行器");
        }
        this.executors = new LinkedHashMap<>(executors);
    }

    /**
     * 创建默认虚拟线程执行器配置。
     *
     * @return 默认配置映射
     */
    private static Map<String, ExecutorSpec> defaultExecutors() {
        Map<String, ExecutorSpec> defaults = new LinkedHashMap<>();
        defaults.put("default", new ExecutorSpec());
        return defaults;
    }

    /**
     * 单个受管执行器的可绑定配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class ExecutorSpec {

        private ExecutorType type = ExecutorType.VIRTUAL;
        private int maxConcurrency = 100;
        private int queueCapacity;
        private Duration shutdownTimeout = Duration.ofSeconds(30);

        /**
         * 创建默认执行器条目。
         */
        public ExecutorSpec() {
        }

        /**
         * 返回线程类型。
         *
         * @return 线程类型
         */
        public ExecutorType getType() {
            return type;
        }

        /**
         * 设置线程类型。
         *
         * @param type 线程类型
         */
        public void setType(ExecutorType type) {
            this.type = type;
        }

        /**
         * 返回最大并发数。
         *
         * @return 最大并发数
         */
        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        /**
         * 设置最大并发数。
         *
         * @param maxConcurrency 最大并发数
         */
        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        /**
         * 返回最大等待任务数。
         *
         * @return 等待容量
         */
        public int getQueueCapacity() {
            return queueCapacity;
        }

        /**
         * 设置最大等待任务数。
         *
         * @param queueCapacity 等待容量
         */
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        /**
         * 返回执行器单独关闭时的排空超时。
         *
         * @return 关闭超时
         */
        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        /**
         * 设置执行器单独关闭时的排空超时。
         *
         * @param shutdownTimeout 关闭超时
         */
        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }
}
