package com.kinetic.checkout.dto;

import com.kinetic.checkout.model.Order;

import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record Response(
            String id,
            Order.Status status,
            Order.Contact customer,
            Order.Delivery delivery,
            List<Order.Item> items,
            double subtotal,
            double handling,
            double total,
            String currency,
            Instant createdAt,
            Instant expiresAt,
            Instant updatedAt,
            String paymentScenario) {
        public static Response from(Order order, String paymentScenario) {
            return new Response(
                    order.id(),
                    order.status(),
                    order.customer(),
                    order.delivery(),
                    order.items(),
                    order.subtotal(),
                    order.handling(),
                    order.total(),
                    order.currency(),
                    order.createdAt(),
                    order.expiresAt(),
                    order.updatedAt(),
                    paymentScenario);
        }
    }

    public record Summary(
            String id,
            Order.Status status,
            double total,
            String currency,
            Instant createdAt,
            int itemCount,
            boolean paymentStarted) {}
}
