package com.kinetic.checkout.repository;

import com.kinetic.checkout.dto.CartDtos;
import com.kinetic.checkout.util.Money;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CartRepository {
    private final JdbcTemplate jdbc;

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CartDtos.Item> items(String userId) {
        return jdbc.query(
                """
                SELECT p.*, c.quantity FROM cart_items c JOIN products p ON p.sku = c.sku
                WHERE c.user_id = ? ORDER BY p.rowid
                """,
                (r, n) ->
                        new CartDtos.Item(
                                r.getString("sku"),
                                r.getString("name"),
                                r.getString("detail"),
                                r.getString("art"),
                                r.getDouble("price"),
                                r.getInt("quantity"),
                                r.getInt("stock"),
                                Money.line(r.getDouble("price"), r.getInt("quantity"))),
                userId);
    }

    public void setQuantity(String userId, String sku, int quantity) {
        if (quantity == 0) {
            jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND sku = ?", userId, sku);
        } else {
            jdbc.update(
                    """
                    INSERT INTO cart_items (user_id,sku,quantity) VALUES (?,?,?)
                    ON CONFLICT(user_id,sku) DO UPDATE SET quantity = excluded.quantity
                    """,
                    userId,
                    sku,
                    quantity);
        }
    }

    public void clear(String userId) {
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", userId);
    }
}
