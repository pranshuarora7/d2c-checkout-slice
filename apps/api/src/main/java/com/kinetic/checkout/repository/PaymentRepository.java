package com.kinetic.checkout.repository;

import com.kinetic.checkout.model.Order;
import com.kinetic.checkout.model.PaymentAttempt;
import com.kinetic.checkout.model.PaymentIntent;
import com.kinetic.checkout.model.WebhookReceipt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createIntent(Order order) {
        jdbc.update(
                "INSERT INTO payment_intents VALUES (?,?,?,?)",
                "pi_" + UUID.randomUUID(),
                order.id(),
                order.total(),
                order.currency());
    }

    public Optional<PaymentIntent> intent(String id) {
        return jdbc
                .query(
                        "SELECT * FROM payment_intents WHERE id = ?",
                        (r, n) ->
                                new PaymentIntent(
                                        r.getString("id"),
                                        r.getString("order_id"),
                                        r.getDouble("expected_amount"),
                                        r.getString("currency")),
                        id)
                .stream()
                .findFirst();
    }

    public PaymentIntent intentForOrder(String orderId) {
        return jdbc
                .query(
                        "SELECT * FROM payment_intents WHERE order_id = ?",
                        (r, n) ->
                                new PaymentIntent(
                                        r.getString("id"),
                                        r.getString("order_id"),
                                        r.getDouble("expected_amount"),
                                        r.getString("currency")),
                        orderId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Order has no payment intent"));
    }

    public Optional<PaymentAttempt> attemptByKey(String userId, String key) {
        return attempt(
                "SELECT * FROM payment_attempts WHERE user_id = ? AND retry_key = ?", userId, key);
    }

    public Optional<PaymentAttempt> attemptForOrder(String orderId) {
        return attempt("SELECT * FROM payment_attempts WHERE order_id = ?", orderId);
    }

    public void saveAttempt(PaymentAttempt attempt, Instant now) {
        jdbc.update(
                "INSERT INTO payment_attempts VALUES (?,?,?,?,?,?,?)",
                attempt.eventId(),
                attempt.userId(),
                attempt.orderId(),
                attempt.retryKey(),
                attempt.outcome(),
                attempt.payload(),
                now.toString());
    }

    public Optional<WebhookReceipt> receipt(String eventId) {
        return jdbc
                .query(
                        "SELECT * FROM webhook_events WHERE event_id = ?",
                        (r, n) ->
                                new WebhookReceipt(
                                        r.getString("payload_hash"), r.getString("result")),
                        eventId)
                .stream()
                .findFirst();
    }

    public void saveReceipt(
            String eventId, String orderId, String hash, String result, Instant now) {
        jdbc.update(
                "INSERT INTO webhook_events VALUES (?,?,?,?,?)",
                eventId,
                orderId,
                hash,
                result,
                now.toString());
    }

    private Optional<PaymentAttempt> attempt(String sql, Object... args) {
        return jdbc
                .query(
                        sql,
                        (r, n) ->
                                new PaymentAttempt(
                                        r.getString("event_id"),
                                        r.getString("user_id"),
                                        r.getString("order_id"),
                                        r.getString("retry_key"),
                                        r.getString("outcome"),
                                        r.getString("payload")),
                        args)
                .stream()
                .findFirst();
    }
}
