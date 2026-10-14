package com.kinetic.checkout.model;

public record WebhookReceipt(String payloadHash, String result) {}
