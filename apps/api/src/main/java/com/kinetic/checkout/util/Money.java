package com.kinetic.checkout.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class Money {
    public static final double HANDLING = 29.0;
    public static final String CURRENCY = "INR";

    private Money() {}

    public static double line(double unitPrice, int quantity) {
        return decimal(unitPrice)
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double sum(List<Double> amounts) {
        return amounts.stream()
                .map(Money::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static boolean equal(double first, double second) {
        return valid(first) && valid(second) && decimal(first).compareTo(decimal(second)) == 0;
    }

    public static boolean valid(double amount) {
        return Double.isFinite(amount)
                && amount >= 0
                && amount <= 10_000_000
                && BigDecimal.valueOf(amount).stripTrailingZeros().scale() <= 2;
    }

    private static BigDecimal decimal(double amount) {
        return BigDecimal.valueOf(amount);
    }
}
