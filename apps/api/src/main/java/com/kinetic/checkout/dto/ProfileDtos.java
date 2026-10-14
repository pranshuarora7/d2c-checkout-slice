package com.kinetic.checkout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record Contact(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank
                    @Pattern(
                            regexp = "[6-9][0-9]{9}",
                            message = "Enter a valid 10-digit Indian mobile number.")
                    String phone) {
        public Contact {
            name = trim(name);
            email = trim(email);
            phone = trim(phone);
        }
    }

    public record AddressInput(
            @NotBlank @Size(max = 30) String label,
            @NotBlank @Size(max = 180) String addressLine,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank
                    @Pattern(regexp = "[1-9][0-9]{5}", message = "Enter a valid 6-digit PIN code.")
                    String pinCode) {
        public AddressInput {
            label = trim(label);
            addressLine = trim(addressLine);
            city = trim(city);
            state = trim(state);
            pinCode = trim(pinCode);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
