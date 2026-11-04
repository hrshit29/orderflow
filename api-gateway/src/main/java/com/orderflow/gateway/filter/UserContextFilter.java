package com.orderflow.gateway.filter;

import com.orderflow.events.Caller;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Turns the verified JWT into X-User-Id / X-User-Role headers for downstream services.
 * Any such headers sent by the client are removed first, so they can't be spoofed.
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(Caller.USER_ID_HEADER);
                    h.remove(Caller.ROLE_HEADER);
                }))
                .build();

        return stripped.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> stripped.mutate()
                        .request(r -> r.headers(h -> {
                            h.set(Caller.USER_ID_HEADER, auth.getToken().getSubject());
                            h.set(Caller.ROLE_HEADER, auth.getToken().getClaimAsString("role"));
                        }))
                        .build())
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
