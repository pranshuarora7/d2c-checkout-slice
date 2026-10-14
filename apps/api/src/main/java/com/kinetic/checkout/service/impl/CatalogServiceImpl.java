package com.kinetic.checkout.service.impl;

import com.kinetic.checkout.model.Product;
import com.kinetic.checkout.repository.CatalogRepository;
import com.kinetic.checkout.service.CatalogService;

import org.springframework.stereotype.Service;

@Service
public class CatalogServiceImpl implements CatalogService {
    private final CatalogRepository products;

    public CatalogServiceImpl(CatalogRepository products) {
        this.products = products;
    }

    @Override
    public java.util.List<Product> list() {
        return products.all();
    }
}
