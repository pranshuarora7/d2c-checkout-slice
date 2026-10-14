package com.kinetic.checkout.controller;

import com.kinetic.checkout.dto.CheckoutDtos;
import com.kinetic.checkout.service.CheckoutService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class CheckoutController {
    private final CheckoutService checkouts;

    public CheckoutController(CheckoutService checkouts) {
        this.checkouts = checkouts;
    }

    @PostMapping("/api/checkouts")
    ResponseEntity<CheckoutDtos.Response> create(
            @RequestAttribute String userId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CheckoutDtos.Request request) {
        var result = checkouts.create(userId, request.addressId(), key);
        var order = result.order();
        var response =
                new CheckoutDtos.Response(
                        order.id(), order.status(), order.total(), order.currency());
        return result.replayed()
                ? ResponseEntity.ok(response)
                : ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(response);
    }
}
