package com.kinetic.checkout.dto;

import com.kinetic.checkout.model.Order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CheckoutDtos {
    private CheckoutDtos() {}

    public record Request(@NotBlank @Size(max = 80) String addressId) {}

    public record Response(String orderId, Order.Status status, double total, String currency) {}

    public record Result(Order order, boolean replayed) {}
}
