package com.orderflow.gateway.filter;

import com.orderflow.events.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextFilterTest {

    private final UserContextFilter filter = new UserContextFilter();

    @Test
    void setsHeadersFromTokenAndOverwritesSpoofedOnes() {
        ServerWebExchange forwarded = run(MockServerHttpRequest.get("/api/v1/orders")
                .header(Caller.USER_ID_HEADER, "999").header(Caller.ROLE_HEADER, "ADMIN").build(), token("42", "CUSTOMER"));

        assertThat(forwarded.getRequest().getHeaders().get(Caller.USER_ID_HEADER)).containsExactly("42");
        assertThat(forwarded.getRequest().getHeaders().get(Caller.ROLE_HEADER)).containsExactly("CUSTOMER");
    }

    @Test
    void anonymousRequestGetsSpoofedHeadersRemoved() {
        ServerWebExchange forwarded = run(MockServerHttpRequest.get("/api/v1/products")
                .header(Caller.USER_ID_HEADER, "999").header(Caller.ROLE_HEADER, "ADMIN").build(), null);

        assertThat(forwarded.getRequest().getHeaders().containsKey(Caller.USER_ID_HEADER)).isFalse();
        assertThat(forwarded.getRequest().getHeaders().containsKey(Caller.ROLE_HEADER)).isFalse();
    }

    private ServerWebExchange run(MockServerHttpRequest request, Principal principal) {
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        if (principal != null) {
            exchange = exchange.mutate().principal(Mono.just(principal)).build();
        }
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return captured.get();
    }

    private static JwtAuthenticationToken token(String sub, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(sub).claim("role", role).build();
        return new JwtAuthenticationToken(jwt);
    }
}
