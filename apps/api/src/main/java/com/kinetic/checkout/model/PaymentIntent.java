package com.kinetic.checkout.model;

public record PaymentIntent(String id, String orderId, double amount, String currency) {}
