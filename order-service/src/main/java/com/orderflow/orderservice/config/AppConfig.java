package com.orderflow.orderservice.config;

import com.orderflow.events.Topics;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class AppConfig {

    /** Creates every topic this service produces or consumes, so listeners start cleanly. */
    @Bean
    KafkaAdmin.NewTopics orderTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.ORDER_CREATED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.ORDER_CANCELLED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.STOCK_RESERVED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.STOCK_INSUFFICIENT).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.PAYMENT_SUCCESS).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.PAYMENT_FAILED).partitions(3).replicas(1).build());
    }

    /** Retries a failed record 3 times, 1s apart (e.g. optimistic-lock conflicts), then logs and skips. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("OrderFlow — Order Service").version("v1"));
    }
}
