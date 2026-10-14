package com.kinetic.checkout.model;

public record PaymentAttempt(
        String eventId,
        String userId,
        String orderId,
        String retryKey,
        String outcome,
        String payload) {}
