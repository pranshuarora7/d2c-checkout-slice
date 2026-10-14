package com.kinetic.checkout.controller;

import com.kinetic.checkout.dto.CartDtos;
import com.kinetic.checkout.service.CartService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {
    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping("/api/cart")
    CartDtos.Cart get(@RequestAttribute String userId) {
        return carts.get(userId);
    }

    @PutMapping("/api/cart/items/{sku}")
    CartDtos.Cart update(
            @RequestAttribute String userId,
            @PathVariable String sku,
            @Valid @RequestBody CartDtos.QuantityRequest request) {
        return carts.setQuantity(userId, sku, request.quantity());
    }
}
