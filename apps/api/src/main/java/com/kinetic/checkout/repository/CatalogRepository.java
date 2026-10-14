package com.kinetic.checkout.repository;

import com.kinetic.checkout.model.Product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> all() {
        return jdbc.query("SELECT * FROM products ORDER BY rowid", this::map);
    }

    public Optional<Product> find(String sku) {
        return jdbc.query("SELECT * FROM products WHERE sku = ?", this::map, sku).stream()
                .findFirst();
    }

    public boolean reserve(String sku, int quantity) {
        return jdbc.update(
                        "UPDATE products SET stock = stock - ? WHERE sku = ? AND stock >= ?",
                        quantity,
                        sku,
                        quantity)
                == 1;
    }

    public void release(String sku, int quantity) {
        jdbc.update("UPDATE products SET stock = stock + ? WHERE sku = ?", quantity, sku);
    }

    private Product map(ResultSet row, int index) throws SQLException {
        return new Product(
                row.getString("sku"),
                row.getString("name"),
                row.getString("category"),
                row.getString("description"),
                row.getString("detail"),
                row.getDouble("price"),
                row.getInt("stock"),
                row.getString("art"));
    }
}
