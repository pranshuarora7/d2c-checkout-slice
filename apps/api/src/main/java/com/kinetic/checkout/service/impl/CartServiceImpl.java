package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.dto.CartDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Order;
import com.kinetic.checkout.repository.CartRepository;
import com.kinetic.checkout.repository.CatalogRepository;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.service.CartService;
import com.kinetic.checkout.service.OrderLifecycleService;
import com.kinetic.checkout.util.Money;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    private final CartRepository carts;
    private final CatalogRepository products;
    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;

    public CartServiceImpl(
            CartRepository carts,
            CatalogRepository products,
            OrderRepository orders,
            OrderLifecycleService lifecycle) {
        this.carts = carts;
        this.products = products;
        this.orders = orders;
        this.lifecycle = lifecycle;
    }

    @Override
    public CartDtos.Cart get(String userId) {
        lifecycle.expireDue();
        return snapshot(userId);
    }

    @Transactional
    @Override
    public CartDtos.Cart setQuantity(String userId, String sku, int quantity) {
        lifecycle.expireDue();
        var product = products.find(sku).orElseThrow(() -> ApiException.notFound("Product"));
        if (quantity > product.stock()) {
            throw ApiException.conflict(
                    "OUT_OF_STOCK", "Only " + product.stock() + " of this item are available.");
        }
        carts.setQuantity(userId, sku, quantity);
        return snapshot(userId);
    }

    @Transactional
    @Override
    public CartDtos.Cart copyOrder(String userId, String orderId) {
        lifecycle.expireDue();
        var order = orders.owned(userId, orderId);
        if (order.status() != Order.Status.FAILED && order.status() != Order.Status.EXPIRED) {
            throw ApiException.conflict(
                    "ORDER_NOT_RETRYABLE",
                    "Only failed or expired orders can be copied to the cart.");
        }
        var quantities =
                carts.items(userId).stream()
                        .collect(Collectors.toMap(CartDtos.Item::sku, CartDtos.Item::quantity));
        for (var item : order.items()) {
            // Repeating this action is safe and never reduces quantities already in the new cart.
            int quantity = Math.max(quantities.getOrDefault(item.sku(), 0), item.quantity());
            var product =
                    products.find(item.sku()).orElseThrow(() -> ApiException.notFound("Product"));
            if (quantity > product.stock()) {
                throw ApiException.conflict(
                        "OUT_OF_STOCK",
                        item.name() + " no longer has enough stock. Your cart was not changed.");
            }
            carts.setQuantity(userId, item.sku(), quantity);
        }
        return snapshot(userId);
    }

    private CartDtos.Cart snapshot(String userId) {
        var items = carts.items(userId);
        double subtotal = Money.sum(items.stream().map(CartDtos.Item::lineTotal).toList());
        double handling = items.isEmpty() ? 0 : Money.HANDLING;
        return new CartDtos.Cart(
                items, subtotal, handling, Money.sum(List.of(subtotal, handling)), Money.CURRENCY);
    }
}
