package com.kinetic.checkout.model;

import java.util.List;

public record Profile(String name, String email, String phone, List<Address> addresses) {
    public record Address(
            String id,
            String label,
            String addressLine,
            String city,
            String state,
            String pinCode) {}
}
