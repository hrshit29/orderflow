package com.orderflow.events;

/** Single source of truth for Kafka topic names. */
public final class Topics {
    public static final String USER_REGISTERED = "user-registered";
    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String STOCK_RESERVED = "stock-reserved";
    public static final String STOCK_INSUFFICIENT = "stock-insufficient";
    public static final String PAYMENT_SUCCESS = "payment-success";
    public static final String PAYMENT_FAILED = "payment-failed";
    public static final String NOTIFICATION_DLQ = "notification-dlq";

    private Topics() {
    }
}
