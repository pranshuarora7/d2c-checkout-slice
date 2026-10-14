package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.ProfileDtos;
import com.kinetic.checkout.model.Profile;

public interface ProfileService {
    Profile get(String userId);

    Profile update(String userId, ProfileDtos.Contact contact);

    Profile addAddress(String userId, ProfileDtos.AddressInput address);

    Profile updateAddress(String userId, String id, ProfileDtos.AddressInput address);

    Profile removeAddress(String userId, String id);
}
