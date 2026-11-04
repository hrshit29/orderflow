package com.orderflow.userservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.orderflow.userservice.dto.UserDtos.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues HS256 access tokens. sub = user id, role = CUSTOMER | ADMIN.
 * The API gateway verifies them with the same secret.
 * Upgrade path: RS256 with a JWKS endpoint, so the gateway only holds the public key.
 */
@Component
public class JwtService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.issuer:orderflow}") String issuer,
                      @Value("${security.jwt.ttl:1h}") Duration ttl) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public String issue(UserResponse user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("role", user.role())
                .claim("email", user.email())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public static SecretKey key(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
