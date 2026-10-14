package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.dto.CheckoutDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Order;
import com.kinetic.checkout.repository.CartRepository;
import com.kinetic.checkout.repository.CatalogRepository;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.repository.PaymentRepository;
import com.kinetic.checkout.repository.ProfileRepository;
import com.kinetic.checkout.service.CheckoutService;
import com.kinetic.checkout.service.OrderLifecycleService;
import com.kinetic.checkout.util.Money;
import com.kinetic.checkout.util.RetryKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutServiceImpl implements CheckoutService {
    private final CartRepository carts;
    private final ProfileRepository profiles;
    private final CatalogRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderLifecycleService lifecycle;
    private final Clock clock;
    private final Duration reservationWindow;

    public CheckoutServiceImpl(
            CartRepository carts,
            ProfileRepository profiles,
            CatalogRepository products,
            OrderRepository orders,
            PaymentRepository payments,
            OrderLifecycleService lifecycle,
            Clock clock,
            @Value("${app.reservation-minutes}") long reservationMinutes) {
        this.carts = carts;
        this.profiles = profiles;
        this.products = products;
        this.orders = orders;
        this.payments = payments;
        this.lifecycle = lifecycle;
        this.clock = clock;
        this.reservationWindow = Duration.ofMinutes(reservationMinutes);
    }

    @Transactional
    @Override
    public CheckoutDtos.Result create(String userId, String addressId, String retryKey) {
        RetryKey.require(retryKey);
        lifecycle.expireDue();
        var previous = orders.byKey(userId, retryKey);
        if (previous.isPresent()) {
            if (!previous.get().addressId().equals(addressId)) {
                throw ApiException.conflict(
                        "RETRY_KEY_REUSED",
                        "That retry key was already used with another address.");
            }
            return new CheckoutDtos.Result(previous.get(), true);
        }
        orders.pending(userId)
                .ifPresent(
                        order -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "ACTIVE_ORDER",
                                    "You already have a checkout awaiting payment.",
                                    Map.of("orderId", order.id()));
                        });
        var profile = profiles.get(userId);
        var address =
                profile.addresses().stream()
                        .filter(a -> a.id().equals(addressId))
                        .findFirst()
                        .orElseThrow(() -> ApiException.notFound("Saved address"));
        var cart = carts.items(userId);
        if (cart.isEmpty()) {
            throw ApiException.conflict("EMPTY_CART", "Add an item before checking out.");
        }
        // Reserve only when entering payment, never when a shopper adds to their cart.
        // The IMMEDIATE transaction holds SQLite's writer lock through this short operation.
        // Committing releases that lock; stock_held + expires_at retain the 15-minute reservation.
        for (var item : cart) {
            if (!products.reserve(item.sku(), item.quantity())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "OUT_OF_STOCK",
                        item.name() + " no longer has enough stock. Please update your cart.",
                        Map.of("sku", item.sku()));
            }
        }
        var items =
                cart.stream()
                        .map(
                                item ->
                                        new Order.Item(
                                                item.sku(),
                                                item.name(),
                                                item.art(),
                                                item.unitPrice(),
                                                item.quantity(),
                                                item.lineTotal()))
                        .toList();
        double subtotal = Money.sum(items.stream().map(Order.Item::lineTotal).toList());
        var now = clock.instant();
        Order order =
                new Order(
                        "KIN-"
                                + UUID.randomUUID()
                                        .toString()
                                        .replace("-", "")
                                        .substring(0, 12)
                                        .toUpperCase(),
                        userId,
                        retryKey,
                        addressId,
                        new Order.Contact(profile.name(), profile.email(), profile.phone()),
                        new Order.Delivery(
                                address.addressLine(),
                                address.city(),
                                address.state(),
                                address.pinCode()),
                        items,
                        subtotal,
                        Money.HANDLING,
                        Money.sum(List.of(subtotal, Money.HANDLING)),
                        Money.CURRENCY,
                        Order.Status.PENDING,
                        true,
                        now,
                        now.plus(reservationWindow),
                        now);
        orders.insert(order);
        payments.createIntent(order);
        return new CheckoutDtos.Result(order, false);
    }
}
