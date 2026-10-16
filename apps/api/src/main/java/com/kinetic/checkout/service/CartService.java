package com.kinetic.checkout.service;

import com.kinetic.checkout.dto.CartDtos;

public interface CartService {
    CartDtos.Cart get(String userId);

    CartDtos.Cart copyOrder(String userId, String orderId);

    CartDtos.Cart setQuantity(String userId, String sku, int quantity);
}
