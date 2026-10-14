package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.dto.OrderDtos;
import com.kinetic.checkout.model.PaymentAttempt;
import com.kinetic.checkout.repository.OrderRepository;
import com.kinetic.checkout.repository.PaymentRepository;
import com.kinetic.checkout.service.OrderLifecycleService;
import com.kinetic.checkout.service.OrderService;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;
    private final PaymentRepository payments;

    public OrderServiceImpl(
            OrderRepository orders, OrderLifecycleService lifecycle, PaymentRepository payments) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.payments = payments;
    }

    @Override
    public OrderDtos.Response get(String userId, String id) {
        lifecycle.expireDue();
        var order = orders.owned(userId, id);
        return OrderDtos.Response.from(
                order, payments.attemptForOrder(id).map(PaymentAttempt::outcome).orElse(null));
    }

    @Override
    public List<OrderDtos.Summary> list(String userId) {
        lifecycle.expireDue();
        return orders.list(userId);
    }
}
