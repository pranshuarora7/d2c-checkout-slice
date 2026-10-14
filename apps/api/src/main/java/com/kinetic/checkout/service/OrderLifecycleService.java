package com.kinetic.checkout.service;

import com.kinetic.checkout.model.Order;

public interface OrderLifecycleService {
    void expireDue();

    void confirm(Order order);

    void fail(Order order);

    void paymentIssue(Order order);
}
