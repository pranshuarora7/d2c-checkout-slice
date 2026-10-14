package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.dto.PaymentDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Order;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.repository.PaymentRepository;
import com.kinetic.checkout.service.OrderLifecycleService;
import com.kinetic.checkout.service.WebhookService;
import com.kinetic.checkout.util.Money;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class WebhookServiceImpl implements WebhookService {
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;
    private final Clock clock;

    public WebhookServiceImpl(
            PaymentRepository payments,
            OrderRepository orders,
            OrderLifecycleService lifecycle,
            Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    @Override
    public PaymentDtos.Receipt process(PaymentDtos.Event event, String bodyHash) {
        var existing = payments.receipt(event.eventId());
        if (existing.isPresent()) {
            if (!existing.get().payloadHash().equals(bodyHash)) {
                throw ApiException.conflict(
                        "EVENT_ID_REUSED",
                        "This event ID was already used with a different payload.");
            }
            return new PaymentDtos.Receipt(
                    "duplicate", event.orderId(), orders.get(event.orderId()).status());
        }
        var intent =
                payments.intent(event.intentId())
                        .orElseThrow(() -> ApiException.notFound("Payment intent"));
        if (!intent.orderId().equals(event.orderId())
                || !intent.currency().equals(event.currency())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_MISMATCH",
                    "Payment intent and order do not match.");
        }
        lifecycle.expireDue();
        var order = orders.get(event.orderId());
        String result;
        if (order.status() == Order.Status.CONFIRMED) {
            result = "ignored"; // A later failure or conflicting callback must never undo a
            // confirmation.
        } else if (!Money.equal(event.amount(), intent.amount())) {
            lifecycle.paymentIssue(order);
            result = "payment_issue";
        } else if (event.type().equals("payment.succeeded")) {
            if (order.status() == Order.Status.PENDING) {
                lifecycle.confirm(order);
                result = "applied";
            } else {
                lifecycle.paymentIssue(order);
                result = "payment_issue";
            }
        } else if (order.status() == Order.Status.PENDING) {
            lifecycle.fail(order);
            result = "applied";
        } else {
            result = "ignored";
        }
        // Receipt and lifecycle change commit together. A crash cannot save only one.
        payments.saveReceipt(event.eventId(), order.id(), bodyHash, result, clock.instant());
        return new PaymentDtos.Receipt(result, order.id(), orders.get(order.id()).status());
    }
}
