package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.dto.ProfileDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Profile;
import com.kinetic.checkout.repository.ProfileRepository;
import com.kinetic.checkout.service.ProfileService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ProfileServiceImpl implements ProfileService {
    private final ProfileRepository profiles;

    public ProfileServiceImpl(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Profile get(String userId) {
        return profiles.get(userId);
    }

    @Override
    public Profile update(String userId, ProfileDtos.Contact contact) {
        profiles.get(userId);
        profiles.updateContact(userId, contact);
        return profiles.get(userId);
    }

    @Override
    public Profile addAddress(String userId, ProfileDtos.AddressInput address) {
        if (profiles.get(userId).addresses().size() >= 10) {
            throw ApiException.conflict(
                    "ADDRESS_LIMIT",
                    "You can save up to 10 addresses. Remove one before adding another.");
        }
        profiles.addAddress(userId, "addr_" + UUID.randomUUID(), address);
        return profiles.get(userId);
    }

    @Override
    public Profile updateAddress(String userId, String id, ProfileDtos.AddressInput address) {
        profiles.updateAddress(userId, id, address);
        return profiles.get(userId);
    }

    @Override
    public Profile removeAddress(String userId, String id) {
        profiles.removeAddress(userId, id);
        return profiles.get(userId);
    }
}
