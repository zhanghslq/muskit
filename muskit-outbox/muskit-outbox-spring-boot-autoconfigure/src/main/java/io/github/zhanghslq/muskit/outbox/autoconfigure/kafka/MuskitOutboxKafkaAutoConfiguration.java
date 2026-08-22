package io.github.zhanghslq.muskit.outbox.autoconfigure.kafka;

import io.github.zhanghslq.muskit.outbox.kafka.KafkaOutboxPublisher;
import io.github.zhanghslq.muskit.outbox.spi.OutboxPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka Outbox 发布器自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, KafkaOutboxPublisher.class})
@ConditionalOnBean(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "muskit.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MuskitOutboxKafkaAutoConfiguration {

    /**
     * 创建 Kafka Outbox 发布器自动配置。
     */
    public MuskitOutboxKafkaAutoConfiguration() {
    }

    /**
     * 创建等待 broker 确认的 Kafka Outbox 发布器。
     *
     * @param kafkaTemplate Kafka 发送模板
     * @return Outbox 发布器
     */
    @Bean
    @ConditionalOnMissingBean(OutboxPublisher.class)
    public OutboxPublisher muskitOutboxPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        return new KafkaOutboxPublisher(kafkaTemplate);
    }
}
