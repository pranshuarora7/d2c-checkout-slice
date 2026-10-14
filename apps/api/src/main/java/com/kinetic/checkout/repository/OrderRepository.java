package com.kinetic.checkout.repository;

import com.kinetic.checkout.dto.OrderDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order o) {
        jdbc.update(
                """
INSERT INTO orders (id,user_id,retry_key,address_id,customer_name,customer_email,customer_phone,
  address_line,city,state,pin_code,subtotal,handling,total,currency,status,stock_held,created_at,expires_at,updated_at)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
                o.id(),
                o.userId(),
                o.retryKey(),
                o.addressId(),
                o.customer().name(),
                o.customer().email(),
                o.customer().phone(),
                o.delivery().addressLine(),
                o.delivery().city(),
                o.delivery().state(),
                o.delivery().pinCode(),
                o.subtotal(),
                o.handling(),
                o.total(),
                o.currency(),
                o.status().value(),
                o.stockHeld() ? 1 : 0,
                o.createdAt().toString(),
                o.expiresAt().toString(),
                o.updatedAt().toString());
        for (Order.Item item : o.items()) {
            jdbc.update(
                    "INSERT INTO order_items VALUES (?,?,?,?,?,?,?)",
                    o.id(),
                    item.sku(),
                    item.name(),
                    item.art(),
                    item.unitPrice(),
                    item.quantity(),
                    item.lineTotal());
        }
    }

    public Order get(String id) {
        return find("SELECT * FROM orders WHERE id = ?", id)
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    public Order owned(String userId, String id) {
        return find("SELECT * FROM orders WHERE user_id = ? AND id = ?", userId, id)
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    public Optional<Order> byKey(String userId, String key) {
        return find("SELECT * FROM orders WHERE user_id = ? AND retry_key = ?", userId, key);
    }

    public Optional<Order> pending(String userId) {
        return find("SELECT * FROM orders WHERE user_id = ? AND status = 'pending'", userId);
    }

    public List<Order> pending() {
        return jdbc.query("SELECT * FROM orders WHERE status = 'pending'", this::map);
    }

    public List<OrderDtos.Summary> list(String userId) {
        return jdbc.query(
                """
SELECT o.*, (SELECT SUM(quantity) FROM order_items WHERE order_id=o.id) AS item_count
FROM orders o WHERE user_id = ? ORDER BY created_at DESC LIMIT 100
""",
                (r, n) ->
                        new OrderDtos.Summary(
                                r.getString("id"),
                                Order.Status.parse(r.getString("status")),
                                r.getDouble("total"),
                                r.getString("currency"),
                                Instant.parse(r.getString("created_at")),
                                r.getInt("item_count")),
                userId);
    }

    public void transition(String id, Order.Status status, Instant now) {
        jdbc.update(
                "UPDATE orders SET status=?, stock_held=0, updated_at=? WHERE id=?",
                status.value(),
                now.toString(),
                id);
    }

    private Optional<Order> find(String sql, Object... args) {
        return jdbc.query(sql, this::map, args).stream().findFirst();
    }

    private Order map(ResultSet r, int index) throws SQLException {
        String id = r.getString("id");
        List<Order.Item> items =
                jdbc.query(
                        "SELECT * FROM order_items WHERE order_id=? ORDER BY rowid",
                        (i, n) ->
                                new Order.Item(
                                        i.getString("sku"),
                                        i.getString("name"),
                                        i.getString("art"),
                                        i.getDouble("unit_price"),
                                        i.getInt("quantity"),
                                        i.getDouble("line_total")),
                        id);
        return new Order(
                id,
                r.getString("user_id"),
                r.getString("retry_key"),
                r.getString("address_id"),
                new Order.Contact(
                        r.getString("customer_name"),
                        r.getString("customer_email"),
                        r.getString("customer_phone")),
                new Order.Delivery(
                        r.getString("address_line"),
                        r.getString("city"),
                        r.getString("state"),
                        r.getString("pin_code")),
                items,
                r.getDouble("subtotal"),
                r.getDouble("handling"),
                r.getDouble("total"),
                r.getString("currency"),
                Order.Status.parse(r.getString("status")),
                r.getInt("stock_held") == 1,
                Instant.parse(r.getString("created_at")),
                Instant.parse(r.getString("expires_at")),
                Instant.parse(r.getString("updated_at")));
    }
}
