package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.client.MockProviderClient;
import com.kinetic.checkout.dto.OrderDtos;
import com.kinetic.checkout.service.OrderLifecycleService;
import com.kinetic.checkout.service.OrderService;
import com.kinetic.checkout.service.PaymentAttemptService;
import com.kinetic.checkout.service.PaymentService;

import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final PaymentAttemptService attempts;
    private final MockProviderClient provider;
    private final OrderService orders;
    private final OrderLifecycleService lifecycle;

    public PaymentServiceImpl(
            PaymentAttemptService attempts,
            MockProviderClient provider,
            OrderService orders,
            OrderLifecycleService lifecycle) {
        this.attempts = attempts;
        this.provider = provider;
        this.orders = orders;
        this.lifecycle = lifecycle;
    }

    @Override
    public OrderDtos.Response pay(String userId, String id, String key, String outcome) {
        lifecycle.expireDue();
        var attempt = attempts.prepare(userId, id, key, outcome);
        // The preparation transaction commits before the HTTP webhook opens its own transaction.
        if (!outcome.equals("pending")) provider.deliver(attempt);
        if (outcome.equals("duplicate")) provider.deliver(attempt);
        return orders.get(userId, id);
    }

    @Override
    public OrderDtos.Response complete(String userId, String id) {
        lifecycle.expireDue();
        // Reuses the persisted event across refreshes/restarts; duplicate completions are safe.
        provider.deliver(attempts.existing(userId, id));
        return orders.get(userId, id);
    }
}
