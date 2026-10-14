package com.kinetic.checkout.controller;

import com.kinetic.checkout.model.Product;
import com.kinetic.checkout.service.CatalogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CatalogController {
    private final CatalogService products;

    public CatalogController(CatalogService products) {
        this.products = products;
    }

    @GetMapping("/api/products")
    List<Product> list() {
        return products.list();
    }
}
