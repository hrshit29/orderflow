package com.orderflow.inventoryservice.config;

import com.orderflow.events.Topics;
import com.orderflow.inventoryservice.model.Product;
import com.orderflow.inventoryservice.repository.ProductRepository;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Configuration
public class AppConfig {

    @Bean
    KafkaAdmin.NewTopics inventoryTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.ORDER_CREATED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.ORDER_CANCELLED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.STOCK_RESERVED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.STOCK_INSUFFICIENT).partitions(3).replicas(1).build());
    }

    /** Retries 3 times, 1s apart (lock timeouts, DB blips), then logs and skips. Bad payloads are not retried. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(DeserializationException.class, ConversionException.class);
        return handler;
    }

    /** Demo catalogue matching the product ids used in the README's curl examples. */
    @Bean
    @ConditionalOnProperty(name = "inventory.seed-demo-products", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedProducts(ProductRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            repo.saveAll(List.of(
                    new Product(7L, "USB-C Cable", new BigDecimal("10.00"), 100),
                    new Product(101L, "Wireless Mouse", new BigDecimal("499.00"), 50),
                    new Product(205L, "Mechanical Keyboard", new BigDecimal("1299.00"), 5)));
            log.info("Seeded demo products 7, 101, 205");
        };
    }

    @Bean
    OpenAPI openAPI() {
        // Relative server URL: "Try it out" calls whichever host served the docs (gateway on 8080, or the service directly).
        return new OpenAPI().info(new Info().title("OrderFlow — Inventory Service").version("v1"))
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearer",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearer"));
    }
}
