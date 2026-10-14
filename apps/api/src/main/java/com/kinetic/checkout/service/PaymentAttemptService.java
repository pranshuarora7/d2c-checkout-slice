package com.kinetic.checkout.service;

import com.kinetic.checkout.model.PaymentAttempt;

public interface PaymentAttemptService {
    PaymentAttempt prepare(String userId, String orderId, String key, String outcome);

    PaymentAttempt pending(String userId, String orderId);
}
