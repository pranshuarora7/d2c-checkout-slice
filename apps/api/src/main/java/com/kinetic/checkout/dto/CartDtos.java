package com.kinetic.checkout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class CartDtos {
    private CartDtos() {}

    public record QuantityRequest(@NotNull @Min(0) @Max(10) Integer quantity) {}

    public record Item(
            String sku,
            String name,
            String detail,
            String art,
            double unitPrice,
            int quantity,
            int available,
            double lineTotal) {}

    public record Cart(
            List<Item> items,
            double subtotal,
            double handling,
            double total,
            String currency,
            String activeOrderId) {}
}
