package com.kinetic.checkout.controller;

import com.kinetic.checkout.dto.ProfileDtos;
import com.kinetic.checkout.model.Profile;
import com.kinetic.checkout.service.ProfileService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/profile")
    Profile get(@RequestAttribute String userId) {
        return service.get(userId);
    }

    @PutMapping("/api/profile")
    Profile update(
            @RequestAttribute String userId, @Valid @RequestBody ProfileDtos.Contact contact) {
        return service.update(userId, contact);
    }

    @PostMapping("/api/profile/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    Profile addAddress(
            @RequestAttribute String userId, @Valid @RequestBody ProfileDtos.AddressInput address) {
        return service.addAddress(userId, address);
    }

    @PutMapping("/api/profile/addresses/{id}")
    Profile updateAddress(
            @RequestAttribute String userId,
            @PathVariable String id,
            @Valid @RequestBody ProfileDtos.AddressInput address) {
        return service.updateAddress(userId, id, address);
    }

    @DeleteMapping("/api/profile/addresses/{id}")
    Profile removeAddress(@RequestAttribute String userId, @PathVariable String id) {
        return service.removeAddress(userId, id);
    }
}
