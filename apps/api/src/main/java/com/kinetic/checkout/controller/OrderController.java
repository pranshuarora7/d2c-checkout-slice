package com.kinetic.checkout.controller;

import com.kinetic.checkout.dto.OrderDtos;
import com.kinetic.checkout.service.OrderService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/api/orders")
    List<OrderDtos.Summary> list(@RequestAttribute String userId) {
        return orders.list(userId);
    }

    @GetMapping("/api/orders/{id}")
    OrderDtos.Response get(@RequestAttribute String userId, @PathVariable String id) {
        return orders.get(userId, id);
    }
}
