package com.orderflow.events;

import java.time.Instant;

public record UserRegisteredEvent(Long userId, String email, String fullName, Instant occurredAt) {
}
