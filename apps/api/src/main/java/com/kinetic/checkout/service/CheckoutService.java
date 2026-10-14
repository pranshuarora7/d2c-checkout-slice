package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.CheckoutDtos;

public interface CheckoutService {
    CheckoutDtos.Result create(String userId, String addressId, String retryKey);
}
