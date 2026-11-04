package com.orderflow.paymentservice.config;

import com.orderflow.events.Topics;
import com.orderflow.paymentservice.kafka.PaymentFailureRecoverer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.DeserializationException;

@Configuration
public class AppConfig {

    @Bean
    KafkaAdmin.NewTopics paymentTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.STOCK_RESERVED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.ORDER_CANCELLED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.PAYMENT_SUCCESS).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.PAYMENT_FAILED).partitions(3).replicas(1).build());
    }

    /** Gateway timeouts: retry at 1s, 2s, 4s, then the recoverer fails the payment. Bad payloads are not retried. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(PaymentFailureRecoverer recoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class, ConversionException.class);
        return handler;
    }

    @Bean
    OpenAPI openAPI() {
        // Relative server URL: "Try it out" calls whichever host served the docs (gateway on 8080, or the service directly).
        return new OpenAPI().info(new Info().title("OrderFlow — Payment Service").version("v1"))
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearer",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearer"));
    }
}
