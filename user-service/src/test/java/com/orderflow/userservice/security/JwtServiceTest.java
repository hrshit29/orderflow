package com.orderflow.userservice.security;

import com.orderflow.userservice.dto.UserDtos.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private final JwtService jwtService = new JwtService(SECRET, "orderflow", Duration.ofHours(1));
    private final UserResponse user = new UserResponse(42L, "a@b.com", "Asha", null, "CUSTOMER", Instant.now());

    @Test
    void tokenCarriesUserIdAndRoleAndVerifiesWithSameSecret() {
        Jwt jwt = decoder(SECRET).decode(jwtService.issue(user));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("orderflow");   // getIssuer() expects a URL
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(3500));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = jwtService.issue(user);
        assertThatThrownBy(() -> decoder("a-different-secret-also-at-least-32-bytes").decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("too-short", "orderflow", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static NimbusJwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(JwtService.key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
