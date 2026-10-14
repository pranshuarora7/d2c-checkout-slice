package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.CartDtos;

public interface CartService {
    CartDtos.Cart get(String userId);

    CartDtos.Cart setQuantity(String userId, String sku, int quantity);
}
