package com.kinetic.checkout.controller;

import com.kinetic.checkout.dto.OrderDtos;
import com.kinetic.checkout.dto.PaymentDtos;
import com.kinetic.checkout.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockPaymentController {
    private final PaymentService payments;

    public MockPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/api/orders/{id}/mock-payment")
    OrderDtos.Response pay(
            @RequestAttribute String userId,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentDtos.MockRequest request) {
        return payments.pay(userId, id, key, request.outcome());
    }

    @PostMapping("/api/orders/{id}/mock-payment/complete")
    OrderDtos.Response complete(@RequestAttribute String userId, @PathVariable String id) {
        return payments.complete(userId, id);
    }
}
