package com.orderflow.notificationservice.config;

import com.orderflow.events.Topics;
import com.orderflow.notificationservice.kafka.DeadLetterRecoverer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Bean
    KafkaAdmin.NewTopics notificationTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.USER_REGISTERED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.ORDER_CREATED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.ORDER_CANCELLED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.PAYMENT_SUCCESS).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.PAYMENT_FAILED).partitions(3).replicas(1).build(),
                TopicBuilder.name(Topics.NOTIFICATION_DLQ).partitions(1).replicas(1).build());
    }

    /**
     * Template used only for dead-lettering. Poison messages that failed deserialization arrive
     * as raw byte[] and are forwarded untouched; everything else is written as JSON.
     */
    @Bean
    KafkaTemplate<Object, Object> deadLetterTemplate(KafkaProperties props) {
        Map<Class<?>, org.apache.kafka.common.serialization.Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(Object.class, new JsonSerializer<>());
        @SuppressWarnings({"unchecked", "rawtypes"})
        DefaultKafkaProducerFactory<Object, Object> pf = new DefaultKafkaProducerFactory<>(
                props.buildProducerProperties(null),
                (org.apache.kafka.common.serialization.Serializer) new StringSerializer(),
                new DelegatingByTypeSerializer(delegates, true));
        return new KafkaTemplate<>(pf);
    }

    /**
     * Retry with exponential backoff (1s, 2s, 4s), then hand off to the recoverer, which records
     * a FAILED row in notification_log and publishes the record to notification-dlq.
     * Bad payloads (deserialization errors) skip retries and go straight to the DLQ.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(DeadLetterRecoverer recoverer) {
        ExponentialBackOff backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class, ConversionException.class,
                IllegalArgumentException.class);
        return handler;
    }
}
