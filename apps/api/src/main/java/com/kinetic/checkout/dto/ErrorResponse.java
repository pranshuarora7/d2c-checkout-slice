package com.kinetic.checkout.dto;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, String> details) {}
