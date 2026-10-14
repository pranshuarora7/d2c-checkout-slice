package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.OrderDtos;

public interface OrderService {
    OrderDtos.Response get(String userId, String id);

    java.util.List<OrderDtos.Summary> list(String userId);
}
