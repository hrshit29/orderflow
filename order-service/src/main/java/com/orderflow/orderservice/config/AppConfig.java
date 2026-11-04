package com.orderflow.orderservice.config;

import com.orderflow.events.Topics;
import com.orderflow.orderservice.model.OrderStatus;
import com.orderflow.orderservice.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Creates every topic this service produces or consumes, so listeners start cleanly. */
    /** orderflow_orders{status="PAID"} etc.: current number of orders in each status (read from the DB on scrape). */
    @Bean
    MeterBinder orderStatusGauges(OrderRepository orders) {
        return registry -> {
            for (OrderStatus status : OrderStatus.values()) {
                Gauge.builder("orderflow.orders", orders, repo -> repo.countByStatus(status))
                        .tag("status", status.name())
                        .description("Orders currently in this status")
                        .register(registry);
            }
        };
    }

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
        // Relative server URL: "Try it out" calls whichever host served the docs (gateway on 8080, or the service directly).
        return new OpenAPI().info(new Info().title("OrderFlow — Order Service").version("v1"))
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearer",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearer"));
    }
}
