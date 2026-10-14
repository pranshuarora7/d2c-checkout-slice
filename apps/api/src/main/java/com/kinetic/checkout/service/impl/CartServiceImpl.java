package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.dto.CartDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.repository.CartRepository;
import com.kinetic.checkout.repository.CatalogRepository;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.service.CartService;
import com.kinetic.checkout.service.OrderLifecycleService;
import com.kinetic.checkout.util.Money;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
        orders.pending(userId)
                .ifPresent(
                        order -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "ACTIVE_ORDER",
                                    "Finish your current checkout before changing the cart.",
                                    Map.of("orderId", order.id()));
                        });
        var product = products.find(sku).orElseThrow(() -> ApiException.notFound("Product"));
        if (quantity > product.stock()) {
            throw ApiException.conflict(
                    "OUT_OF_STOCK", "Only " + product.stock() + " of this item are available.");
        }
        carts.setQuantity(userId, sku, quantity);
        return snapshot(userId);
    }

    private CartDtos.Cart snapshot(String userId) {
        var items = carts.items(userId);
        double subtotal = Money.sum(items.stream().map(CartDtos.Item::lineTotal).toList());
        double handling = items.isEmpty() ? 0 : Money.HANDLING;
        return new CartDtos.Cart(
                items,
                subtotal,
                handling,
                Money.sum(List.of(subtotal, handling)),
                Money.CURRENCY,
                orders.pending(userId).map(order -> order.id()).orElse(null));
    }
}
