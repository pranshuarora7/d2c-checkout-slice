package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.PaymentDtos;

public interface WebhookService {
    PaymentDtos.Receipt process(PaymentDtos.Event event, String bodyHash);
}
