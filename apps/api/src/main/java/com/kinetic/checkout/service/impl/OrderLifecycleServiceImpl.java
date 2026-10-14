package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.model.Order;
import com.kinetic.checkout.repository.CartRepository;
import com.kinetic.checkout.repository.CatalogRepository;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.service.OrderLifecycleService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class OrderLifecycleServiceImpl implements OrderLifecycleService {
    private final OrderRepository orders;
    private final CatalogRepository products;
    private final CartRepository carts;
    private final Clock clock;

    public OrderLifecycleServiceImpl(
            OrderRepository orders, CatalogRepository products, CartRepository carts, Clock clock) {
        this.orders = orders;
        this.products = products;
        this.carts = carts;
        this.clock = clock;
    }

    @Override
    public void expireDue() {
        for (Order order : orders.pending()) {
            if (!order.expiresAt().isAfter(clock.instant())) {
                release(order);
                orders.transition(order.id(), Order.Status.EXPIRED, clock.instant());
            }
        }
    }

    @Override
    public void confirm(Order order) {
        if (order.status() != Order.Status.PENDING) return;
        // Stock was deducted when reserved. Confirmation consumes that hold, not stock again.
        orders.transition(order.id(), Order.Status.CONFIRMED, clock.instant());
        carts.clear(order.userId());
    }

    @Override
    public void fail(Order order) {
        if (order.status() != Order.Status.PENDING) return;
        release(order);
        orders.transition(order.id(), Order.Status.FAILED, clock.instant());
    }

    @Override
    public void paymentIssue(Order order) {
        if (order.status() == Order.Status.CONFIRMED
                || order.status() == Order.Status.PAYMENT_ISSUE) return;
        release(order);
        orders.transition(order.id(), Order.Status.PAYMENT_ISSUE, clock.instant());
    }

    private void release(Order order) {
        if (order.stockHeld()) {
            order.items().forEach(item -> products.release(item.sku(), item.quantity()));
        }
    }
}
