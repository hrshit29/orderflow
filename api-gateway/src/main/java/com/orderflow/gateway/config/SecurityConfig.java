package com.orderflow.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Authentication and coarse-grained authorization for every route.
 * Fine-grained "is this your order?" checks live in each service (see Caller).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)          // stateless bearer tokens, no cookies
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        // public
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/register", "/api/v1/users/login").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus", "/swagger-ui.html", "/swagger-ui/**",
                                "/webjars/**", "/docs/**", "/v3/api-docs/**").permitAll()
                        // admin only
                        .pathMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/products/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/reservations/**", "/api/v1/payments/gateway/**").hasRole("ADMIN")
                        // everything else: any logged-in user (services enforce ownership)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())))
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret,
                                  @Value("${security.jwt.issuer}") String issuer) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes for HS256");
        }
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));   // signature + exp + iss
        return decoder;
    }

    /** "role": "ADMIN" claim -> ROLE_ADMIN authority, so hasRole("ADMIN") works. */
    private static ReactiveJwtAuthenticationConverterAdapter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
