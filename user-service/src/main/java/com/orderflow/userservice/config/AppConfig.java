package com.orderflow.userservice.config;

import com.orderflow.events.Topics;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import com.orderflow.userservice.model.User;
import com.orderflow.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
public class AppConfig {

    /** Bootstraps one ADMIN account; registration only ever creates CUSTOMERs. */
    @Bean
    ApplicationRunner seedAdmin(UserRepository repo, PasswordEncoder encoder,
                                @Value("${security.admin.email}") String email,
                                @Value("${security.admin.password}") String password) {
        return args -> {
            if (repo.existsByEmailIgnoreCase(email)) return;
            repo.save(User.builder().email(email.toLowerCase()).fullName("OrderFlow Admin")
                    .passwordHash(encoder.encode(password)).role(User.Role.ADMIN).build());
            log.info("Created admin account {}", email);
        };
    }

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
        // Relative server URL: "Try it out" calls whichever host served the docs (gateway on 8080, or the service directly).
        return new OpenAPI().info(new Info().title("OrderFlow — User Service").version("v1"))
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearer",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearer"));
    }
}
