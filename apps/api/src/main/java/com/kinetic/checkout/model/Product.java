package com.kinetic.checkout.model;

public record Product(
        String sku,
        String name,
        String category,
        String description,
        String detail,
        double price,
        int stock,
        String art) {}
