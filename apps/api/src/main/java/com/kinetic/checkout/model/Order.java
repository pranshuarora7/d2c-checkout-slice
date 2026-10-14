package com.kinetic.checkout.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record Order(
        String id,
        String userId,
        String retryKey,
        String addressId,
        Contact customer,
        Delivery delivery,
        List<Item> items,
        double subtotal,
        double handling,
        double total,
        String currency,
        Status status,
        boolean stockHeld,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt) {
    public enum Status {
        PENDING,
        CONFIRMED,
        FAILED,
        EXPIRED,
        PAYMENT_ISSUE;

        @JsonValue
        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Status parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public record Contact(String name, String email, String phone) {}

    public record Delivery(String addressLine, String city, String state, String pinCode) {}

    public record Item(
            String sku,
            String name,
            String art,
            double unitPrice,
            int quantity,
            double lineTotal) {}
}
