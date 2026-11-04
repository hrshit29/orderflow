package com.orderflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;

/** Adding the common-outbox dependency is enough: this registers the store and the relay. */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, KafkaAutoConfiguration.class,
        JacksonAutoConfiguration.class, TransactionAutoConfiguration.class})
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxStore outboxStore(JdbcOperations jdbc, ObjectMapper objectMapper) {
        return new JdbcOutboxStore(jdbc, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRelay outboxRelay(OutboxStore store, KafkaOperations<String, Object> kafka, ObjectMapper objectMapper,
                            TransactionOperations tx,
                            @Value("${outbox.batch-size:100}") int batchSize,
                            @Value("${outbox.retention:7d}") Duration retention) {
        return new OutboxRelay(store, kafka, objectMapper, tx, batchSize, retention);
    }

    /** outbox_pending gauge, registered only when Micrometer is on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class OutboxMetrics {
        @Bean
        io.micrometer.core.instrument.binder.MeterBinder outboxPendingGauge(OutboxStore store) {
            return registry -> io.micrometer.core.instrument.Gauge
                    .builder("outbox.pending", store, s -> s.countPending())
                    .description("Outbox events not yet published to Kafka")
                    .register(registry);
        }
    }
}
