package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.OrderDtos;

public interface PaymentService {
    OrderDtos.Response pay(String userId, String id, String key, String outcome);

    OrderDtos.Response complete(String userId, String id);
}
