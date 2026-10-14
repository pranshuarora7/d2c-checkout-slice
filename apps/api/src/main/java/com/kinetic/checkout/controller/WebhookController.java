package com.kinetic.checkout.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinetic.checkout.dto.PaymentDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.security.WebhookSignature;
import com.kinetic.checkout.service.WebhookService;
import com.kinetic.checkout.util.Money;

import jakarta.validation.Validator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class WebhookController {
    private final WebhookSignature signature;
    private final WebhookService webhooks;
    private final ObjectMapper json;
    private final Validator validator;

    public WebhookController(
            WebhookSignature signature,
            WebhookService webhooks,
            ObjectMapper json,
            Validator validator) {
        this.signature = signature;
        this.webhooks = webhooks;
        this.json = json;
        this.validator = validator;
    }

    @PostMapping(value = "/webhooks/payment", consumes = "application/json")
    PaymentDtos.Receipt receive(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Payment-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Payment-Signature", required = false)
                    String suppliedSignature) {
        if (body.length > 16_384) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE",
                    "Payment payload is too large.");
        }
        signature.verify(body, timestamp, suppliedSignature);
        PaymentDtos.Event event;
        try {
            event = json.readValue(body, PaymentDtos.Event.class);
        } catch (IOException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Payment event JSON is invalid.");
        }
        if (event == null || !validator.validate(event).isEmpty() || !Money.valid(event.amount())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Payment event fields are invalid.");
        }
        return webhooks.process(event, WebhookSignature.bodyHash(body));
    }
}
