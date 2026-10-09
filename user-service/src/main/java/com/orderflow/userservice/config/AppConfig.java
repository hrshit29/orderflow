package com.orderflow.userservice.config;

import com.orderflow.events.Topics;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    NewTopic userRegisteredTopic() {
        return TopicBuilder.name(Topics.USER_REGISTERED).partitions(3).replicas(1).build();
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("OrderFlow — User Service").version("v1"));
    }
}
