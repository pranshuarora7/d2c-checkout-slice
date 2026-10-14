package com.kinetic.checkout.dto;

import com.kinetic.checkout.model.Order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record MockRequest(
            @NotNull @Pattern(regexp = "success|failure|pending|duplicate") String outcome) {}

    public record Event(
            @NotBlank @Size(max = 80) String eventId,
            @NotBlank @Size(max = 80) String intentId,
            @NotBlank @Size(max = 80) String orderId,
            @NotNull @Pattern(regexp = "payment\\.succeeded|payment\\.failed") String type,
            @NotNull Double amount,
            @NotNull @Pattern(regexp = "INR") String currency) {}

    public record Receipt(String result, String orderId, Order.Status status) {}
}
