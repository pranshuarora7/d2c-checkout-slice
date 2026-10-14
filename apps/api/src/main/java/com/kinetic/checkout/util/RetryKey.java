package com.kinetic.checkout.util;

import com.kinetic.checkout.exception.ApiException;

import org.springframework.http.HttpStatus;

public final class RetryKey {
    private RetryKey() {}

    public static String require(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,100}")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RETRY_KEY",
                    "Idempotency-Key must be 16–100 letters, digits, hyphens or underscores.");
        }
        return value;
    }
}
