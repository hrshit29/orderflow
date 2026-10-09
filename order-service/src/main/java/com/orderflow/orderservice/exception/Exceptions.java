package com.orderflow.orderservice.exception;

public final class Exceptions {

    private Exceptions() {
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    /** Request is valid but conflicts with the order's current state (e.g. cancelling a PAID order). */
    public static class InvalidOrderStateException extends RuntimeException {
        public InvalidOrderStateException(String message) {
            super(message);
        }
    }
}
