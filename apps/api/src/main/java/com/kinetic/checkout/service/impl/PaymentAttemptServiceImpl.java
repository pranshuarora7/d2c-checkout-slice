package com.kinetic.checkout.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinetic.checkout.dto.PaymentDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Order;
import com.kinetic.checkout.model.PaymentAttempt;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.repository.PaymentRepository;
import com.kinetic.checkout.service.PaymentAttemptService;
import com.kinetic.checkout.util.RetryKey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class PaymentAttemptServiceImpl implements PaymentAttemptService {
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final ObjectMapper json;
    private final Clock clock;

    public PaymentAttemptServiceImpl(
            PaymentRepository payments, OrderRepository orders, ObjectMapper json, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    @Override
    public PaymentAttempt prepare(String userId, String orderId, String key, String outcome) {
        RetryKey.require(key);
        var order = orders.owned(userId, orderId);
        var byKey = payments.attemptByKey(userId, key);
        if (byKey.isPresent()) {
            var previous = byKey.get();
            if (!previous.orderId().equals(orderId) || !previous.outcome().equals(outcome)) {
                throw ApiException.conflict(
                        "RETRY_KEY_REUSED",
                        "That retry key was already used for another payment action.");
            }
            return previous;
        }
        var byOrder = payments.attemptForOrder(orderId);
        if (byOrder.isPresent()) {
            if (!byOrder.get().outcome().equals(outcome)) {
                throw ApiException.conflict(
                        "PAYMENT_ALREADY_STARTED",
                        "A different payment outcome has already been selected.");
            }
            // A new browser session may retry an existing attempt with a fresh key.
            // Bind it before returning so it cannot later start payment for another order.
            payments.bindKey(byOrder.get(), key);
            return byOrder.get();
        }
        if (order.status() != Order.Status.PENDING || !order.expiresAt().isAfter(clock.instant())) {
            throw ApiException.conflict(
                    "ORDER_NOT_PAYABLE",
                    "This order is no longer awaiting payment. Please check its status.");
        }
        var intent = payments.intentForOrder(orderId);
        String eventId = "evt_" + UUID.randomUUID();
        var event =
                new PaymentDtos.Event(
                        eventId,
                        intent.id(),
                        orderId,
                        outcome.equals("failure") ? "payment.failed" : "payment.succeeded",
                        intent.amount(),
                        intent.currency());
        try {
            var attempt =
                    new PaymentAttempt(
                            eventId, userId, orderId, key, outcome, json.writeValueAsString(event));
            payments.saveAttempt(attempt, clock.instant());
            return attempt;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode mock payment event", error);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentAttempt existing(String userId, String orderId) {
        orders.owned(userId, orderId);
        var attempt =
                payments.attemptForOrder(orderId)
                        .orElseThrow(
                                () ->
                                        ApiException.conflict(
                                                "PAYMENT_NOT_STARTED",
                                                "Choose a demo payment scenario first."));
        return attempt;
    }
}
