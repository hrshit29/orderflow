package com.orderflow.events;

/**
 * Who is calling, as forwarded by the API gateway after it has verified the JWT.
 * The gateway strips any X-User-* headers a client sends, then sets them from the token,
 * so services can trust them without parsing JWTs themselves.
 *
 * No headers at all means the request did not come through the gateway (service-to-service
 * call or local development), which is treated as trusted. In Kubernetes (Phase 4) only the
 * gateway is reachable from outside the cluster.
 */
public record Caller(Long userId, boolean admin) {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";

    public static Caller of(Long userId, String role) {
        return new Caller(userId, "ADMIN".equalsIgnoreCase(role));
    }

    public boolean isInternal() {
        return userId == null;
    }

    /** Admins and internal callers may see everyone's data. */
    public boolean isPrivileged() {
        return isInternal() || admin;
    }

    public boolean owns(Long ownerId) {
        return isPrivileged() || userId.equals(ownerId);
    }

    /** For list endpoints: privileged callers get the filter they asked for, customers only their own rows. */
    public Long scopeUserId(Long requestedUserId) {
        return isPrivileged() ? requestedUserId : userId;
    }
}
