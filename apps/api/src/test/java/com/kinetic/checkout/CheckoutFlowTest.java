package com.kinetic.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CheckoutFlowTest.TimeConfig.class)
class CheckoutFlowTest {
    private static final String USER = "demo-customer-1";
    private static final String OTHER = "demo-test-2";
    private static final String SECRET = "test-webhook-secret";
    private static final Path DB = temporaryDatabase();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("app.database", DB::toString);
        registry.add(
                "app.sample-database", () -> DB.resolveSibling("absent-sample.sqlite").toString());
        registry.add("app.expiry.enabled", () -> "false");
        registry.add("app.test-token", () -> OTHER);
        registry.add("app.webhook-secret", () -> SECRET);
    }

    @BeforeEach
    void reset() {
        clock.now = Instant.parse("2026-10-14T00:00:00Z");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_receipt");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_intent");
        for (String table :
                List.of(
                        "webhook_events",
                        "payment_attempt_keys",
                        "payment_attempts",
                        "payment_intents",
                        "order_items",
                        "orders",
                        "cart_items",
                        "addresses",
                        "profiles",
                        "products")) {
            jdbc.update("DELETE FROM " + table);
        }
        new ResourceDatabasePopulator(new ClassPathResource("db/seed.sql"))
                .execute(jdbc.getDataSource());
        jdbc.update(
                "INSERT INTO profiles VALUES ('user_test_2','Second"
                        + " shopper','other@example.test','9000000001')");
        jdbc.update(
                "INSERT INTO addresses (id,user_id,label,address_line,city,state,pin_code) VALUES"
                        + " ('addr_other','user_test_2','Home','1 Test"
                        + " Lane','Pune','Maharashtra','411001')");
    }

    @Test
    void completeFlowUsesServerCartProfileAndActualHttpWebhook() {
        assertThat(get("/api/products", null).getBody()).hasSize(5);
        quantity(USER, "TRAINING-MAT", 1);
        quantity(USER, "RESISTANCE-LOOP", 1);
        String key = key();
        var created = checkout(USER, "addr_home", key);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String id = created.getBody().get("orderId").asText();
        assertThat(created.getBody().get("total").asDouble()).isEqualTo(1827.0);
        assertThat(created.getBody().has("intentId")).isFalse();
        var replay = checkout(USER, "addr_home", key);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody().get("orderId").asText()).isEqualTo(id);
        assertThat(stock("TRAINING-MAT")).isEqualTo(7);

        var paid = pay(USER, id, "success", key());
        assertThat(paid.getStatusCode().value()).isEqualTo(200);
        assertThat(paid.getBody().get("status").asText()).isEqualTo("confirmed");
        assertThat(paid.getBody().at("/customer/email").asText()).isEqualTo("demo@example.test");
        assertThat(paid.getBody().at("/delivery/pinCode").asText()).isEqualTo("560038");
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
        assertThat(get("/api/orders", USER).getBody().get(0).get("id").asText()).isEqualTo(id);
        assertThat(count("webhook_events")).isEqualTo(1);
        assertThat(count("payment_attempts")).isEqualTo(1);
    }

    @Test
    void profileAndAddressEditingAreOwnedAndDoNotRewritePlacedOrders() {
        String order = createOrder();
        var contact =
                Map.of(
                        "name",
                        " Updated Shopper ",
                        "email",
                        "updated@example.test",
                        "phone",
                        "9876543210");
        assertThat(
                        request(HttpMethod.PUT, "/api/profile", USER, contact, null)
                                .getBody()
                                .get("name")
                                .asText())
                .isEqualTo("Updated Shopper");
        var address =
                Map.of(
                        "label",
                        "Studio",
                        "addressLine",
                        "42 Demo Road",
                        "city",
                        "Pune",
                        "state",
                        "Maharashtra",
                        "pinCode",
                        "411001");
        var created = request(HttpMethod.POST, "/api/profile/addresses", USER, address, null);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String addressId = created.getBody().get("addresses").get(2).get("id").asText();
        assertThat(
                        request(
                                        HttpMethod.PUT,
                                        "/api/profile/addresses/" + addressId,
                                        OTHER,
                                        address,
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(404);
        assertThat(
                        request(
                                        HttpMethod.DELETE,
                                        "/api/profile/addresses/" + addressId,
                                        OTHER,
                                        null,
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(404);
        assertThat(
                        request(
                                        HttpMethod.PUT,
                                        "/api/profile/addresses/addr_home",
                                        USER,
                                        address,
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        assertThat(
                        request(
                                        HttpMethod.DELETE,
                                        "/api/profile/addresses/addr_home",
                                        USER,
                                        null,
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        var snapshot = get("/api/orders/" + order, USER).getBody();
        assertThat(snapshot.at("/customer/name").asText()).isEqualTo("Demo Shopper");
        assertThat(snapshot.at("/delivery/pinCode").asText()).isEqualTo("560038");
        pay(USER, order, "failure", key());
        assertThat(copyOrder(USER, order).getStatusCode().value()).isEqualTo(200);
        assertThat(checkout(USER, "addr_home", key()).getStatusCode().value()).isEqualTo(404);
        String fresh = checkout(USER, addressId, key()).getBody().get("orderId").asText();
        assertThat(get("/api/orders/" + fresh, USER).getBody().at("/customer/name").asText())
                .isEqualTo("Updated Shopper");
        assertThat(get("/api/orders/" + fresh, USER).getBody().at("/delivery/pinCode").asText())
                .isEqualTo("411001");
    }

    @Test
    void profileValidationAndEmptyAddressBookAreHandled() {
        assertThat(
                        request(
                                        HttpMethod.PUT,
                                        "/api/profile",
                                        USER,
                                        Map.of("name", "  ", "email", "bad", "phone", "123"),
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        request(
                                        HttpMethod.POST,
                                        "/api/profile/addresses",
                                        USER,
                                        Map.of(
                                                "label",
                                                "Home",
                                                "addressLine",
                                                "Test",
                                                "city",
                                                "Pune",
                                                "state",
                                                "Maharashtra",
                                                "pinCode",
                                                "000000"),
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        request(HttpMethod.DELETE, "/api/profile/addresses/addr_home", USER, null, null);
        request(HttpMethod.DELETE, "/api/profile/addresses/addr_work", USER, null, null);
        assertThat(get("/api/profile", USER).getBody().get("addresses")).isEmpty();
        quantity(USER, "TRAINING-MAT", 1);
        assertThat(checkout(USER, "addr_home", key()).getStatusCode().value()).isEqualTo(404);
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
    }

    @Test
    void pendingScenarioWaitsForExplicitCallbackAndCompletionIsIdempotent() {
        String id = createOrder();
        var waiting = pay(USER, id, "pending", key());
        assertThat(waiting.getBody().get("status").asText()).isEqualTo("pending");
        assertThat(get("/api/orders/" + id, USER).getBody().get("paymentScenario").asText())
                .isEqualTo("pending");
        assertThat(count("webhook_events")).isZero();
        String path = "/api/orders/" + id + "/mock-payment/complete";
        assertThat(request(HttpMethod.POST, path, OTHER, null, null).getStatusCode().value())
                .isEqualTo(404);
        assertThat(
                        request(HttpMethod.POST, path, USER, null, null)
                                .getBody()
                                .get("status")
                                .asText())
                .isEqualTo("confirmed");
        assertThat(request(HttpMethod.POST, path, USER, null, null).getStatusCode().value())
                .isEqualTo(200);
        assertThat(count("webhook_events")).isEqualTo(1);
        assertThat(stock("TRAINING-MAT")).isEqualTo(7);
    }

    @Test
    void duplicateScenarioSendsTwoCallbacksButRecordsOneReceipt() {
        String id = createOrder();
        assertThat(pay(USER, id, "duplicate", key()).getBody().get("status").asText())
                .isEqualTo("confirmed");
        assertThat(count("webhook_events")).isEqualTo(1);
        assertThat(count("payment_attempts")).isEqualTo(1);
        assertThat(stock("TRAINING-MAT")).isEqualTo(7);
    }

    @Test
    void completingPendingScenarioAfterExpiryNeverConfirms() {
        String id = createOrder();
        pay(USER, id, "pending", key());
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        var response =
                request(
                        HttpMethod.POST,
                        "/api/orders/" + id + "/mock-payment/complete",
                        USER,
                        null,
                        null);
        assertThat(response.getBody().get("status").asText()).isEqualTo("payment_issue");
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
    }

    @Test
    void concurrentDoubleSubmitCreatesOneOrderAndOneReservation() throws Exception {
        quantity(USER, "TRAINING-MAT", 1);
        String key = key();
        var responses =
                race(
                        () -> checkout(USER, "addr_home", key),
                        () -> checkout(USER, "addr_home", key));
        assertThat(responses.stream().map(r -> r.getStatusCode().value()))
                .containsExactlyInAnyOrder(201, 200);
        assertThat(responses.get(0).getBody().get("orderId"))
                .isEqualTo(responses.get(1).getBody().get("orderId"));
        assertThat(count("orders")).isEqualTo(1);
        assertThat(stock("TRAINING-MAT")).isEqualTo(7);
    }

    @Test
    void twoBuyersRaceForLastUnitOnlyOneWins() throws Exception {
        jdbc.update("UPDATE products SET stock=1 WHERE sku='TRAINING-MAT'");
        quantity(USER, "TRAINING-MAT", 1);
        quantity(OTHER, "TRAINING-MAT", 1);
        assertThat(stock("TRAINING-MAT")).isEqualTo(1);
        assertThat(count("orders")).isZero();
        System.out.println(
                "STOCK RACE 1/4: both carts contain the last mat; stock is still 1 (no cart"
                        + " reservation).");
        var responses =
                race(
                        () -> checkout(USER, "addr_home", key()),
                        () -> checkout(OTHER, "addr_other", key()));
        assertThat(responses.stream().map(r -> r.getStatusCode().value()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(stock("TRAINING-MAT")).isZero();
        assertThat(count("orders")).isEqualTo(1);
        int winner = responses.get(0).getStatusCode().value() == 201 ? 0 : 1;
        var tokens = List.of(USER, OTHER);
        var addresses = List.of("addr_home", "addr_other");
        String orderId = responses.get(winner).getBody().get("orderId").asText();
        assertThat(responses.get(1 - winner).getBody().get("code").asText())
                .isEqualTo("OUT_OF_STOCK");
        assertThat(count("payment_intents")).isEqualTo(1);
        assertThat(count("payment_attempts")).isZero();
        System.out.printf(
                "STOCK RACE 2/4: simultaneous payment-page entry -> HTTP %d and %d; one order,"
                        + " stock 0.%n",
                responses.get(0).getStatusCode().value(), responses.get(1).getStatusCode().value());
        assertThat(
                        pay(tokens.get(winner), orderId, "failure", key())
                                .getBody()
                                .get("status")
                                .asText())
                .isEqualTo("failed");
        assertThat(stock("TRAINING-MAT")).isEqualTo(1);
        System.out.println(
                "STOCK RACE 3/4: winner's payment fails; reservation released, stock 1.");
        var retry = checkout(tokens.get(1 - winner), addresses.get(1 - winner), key());
        assertThat(retry.getStatusCode().value()).isEqualTo(201);
        assertThat(
                        pay(
                                        tokens.get(1 - winner),
                                        retry.getBody().get("orderId").asText(),
                                        "success",
                                        key())
                                .getBody()
                                .get("status")
                                .asText())
                .isEqualTo("confirmed");
        assertThat(stock("TRAINING-MAT")).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM orders WHERE status='confirmed'",
                                Integer.class))
                .isEqualTo(1);
        System.out.println(
                "STOCK RACE 4/4: other shopper retries and pays; exactly one confirmed order, stock"
                        + " 0.");
    }

    @Test
    void failedReservationRollsBackEarlierLines() {
        quantity(USER, "TRAINING-MAT", 1);
        quantity(USER, "RESISTANCE-LOOP", 1);
        jdbc.update("UPDATE products SET stock=0 WHERE sku='RESISTANCE-LOOP'");
        assertThat(checkout(USER, "addr_home", key()).getStatusCode().value()).isEqualTo(409);
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
        assertThat(count("orders")).isZero();
        assertThat(count("payment_intents")).isZero();
    }

    @Test
    void ownerValidationAndInputBoundariesAreEnforced() {
        assertThat(get("/api/profile", null).getStatusCode().value()).isEqualTo(401);
        assertThat(quantity(USER, "TRAINING-MAT", -1).getStatusCode().value()).isEqualTo(400);
        assertThat(quantity(USER, "TRAINING-MAT", 11).getStatusCode().value()).isEqualTo(400);
        assertThat(
                        request(
                                        HttpMethod.PUT,
                                        "/api/cart/items/TRAINING-MAT",
                                        USER,
                                        Map.of("quantity", 1.5),
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        request(
                                        HttpMethod.PUT,
                                        "/api/cart/items/TRAINING-MAT",
                                        USER,
                                        Map.of("quantity", "1"),
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        quantity(USER, "TRAINING-MAT", 1);
        assertThat(checkout(USER, "addr_other", key()).getStatusCode().value()).isEqualTo(404);
        var forged =
                request(
                        HttpMethod.POST,
                        "/api/checkouts",
                        USER,
                        Map.of(
                                "addressId",
                                "addr_home",
                                "items",
                                List.of(),
                                "userId",
                                "user_test_2",
                                "total",
                                1),
                        key());
        assertThat(forged.getStatusCode().value()).isEqualTo(400);
        String id = createOrder();
        assertThat(get("/api/orders/" + id, OTHER).getStatusCode().value()).isEqualTo(404);
        assertThat(pay(OTHER, id, "success", key()).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/orders", OTHER).getBody()).isEmpty();
        assertThat(get("/api/cart", OTHER).getBody().get("items")).isEmpty();
    }

    @Test
    void checkoutSnapshotClearsCartAndChangedAddressCannotReuseKey() {
        quantity(USER, "TRAINING-MAT", 1);
        String key = key();
        String first = checkout(USER, "addr_home", key).getBody().get("orderId").asText();
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
        var changed = checkout(USER, "addr_work", key);
        assertThat(changed.getBody().get("code").asText()).isEqualTo("RETRY_KEY_REUSED");
        assertThat(changed.getBody().at("/details/orderId").asText()).isEqualTo(first);
        assertThat(checkout(USER, "addr_home", key()).getBody().get("code").asText())
                .isEqualTo("EMPTY_CART");
        assertThat(quantity(USER, "TRAINING-MAT", 2).getStatusCode().value()).isEqualTo(200);
        assertThat(checkout(USER, "addr_work", key()).getStatusCode().value()).isEqualTo(201);
        assertThat(count("orders")).isEqualTo(2);
        assertThat(
                        get("/api/orders/" + first, USER)
                                .getBody()
                                .get("items")
                                .get(0)
                                .get("quantity")
                                .asInt())
                .isEqualTo(1);
    }

    @Test
    void failedPaymentReleasesStockOnceAndCanStartFreshCheckout() {
        String id = createOrder();
        String key = key();
        assertThat(pay(USER, id, "failure", key).getBody().get("status").asText())
                .isEqualTo("failed");
        assertThat(pay(USER, id, "failure", key).getStatusCode().value()).isEqualTo(200);
        assertThat(pay(USER, id, "failure", key()).getStatusCode().value()).isEqualTo(200);
        assertThat(pay(USER, id, "success", key).getStatusCode().value()).isEqualTo(409);
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
        assertThat(count("webhook_events")).isEqualTo(1);
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
        assertThat(copyOrder(USER, id).getStatusCode().value()).isEqualTo(200);
        assertThat(checkout(USER, "addr_home", key()).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void badSignaturesTamperingAndStaleOrFutureTimestampsNeverConfirm() throws Exception {
        String id = createOrder();
        String body = event(id, "evt_bad", "payment.succeeded", 1328.0);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        assertThat(webhook(body, timestamp, "sha256=" + "0".repeat(64)).getStatusCode().value())
                .isEqualTo(401);
        String stale = Long.toString(clock.instant().minusSeconds(301).getEpochSecond());
        assertThat(webhook(body, stale, sign(body, stale)).getStatusCode().value()).isEqualTo(401);
        String future = Long.toString(clock.instant().plusSeconds(301).getEpochSecond());
        assertThat(webhook(body, future, sign(body, future)).getStatusCode().value())
                .isEqualTo(401);
        assertThat(webhook(body + " ", timestamp, sign(body, timestamp)).getStatusCode().value())
                .isEqualTo(401);
        assertThat(status(id)).isEqualTo("pending");
        assertThat(count("webhook_events")).isZero();
    }

    @Test
    void webhookReplayIsNoOpAndChangedPayloadCannotReuseEventId() throws Exception {
        String id = createOrder();
        String body = event(id, "evt_once", "payment.succeeded", 1328.0);
        assertThat(signed(body).getBody().get("result").asText()).isEqualTo("applied");
        quantity(USER, "STEEL-BOTTLE", 1);
        assertThat(signed(body).getBody().get("result").asText()).isEqualTo("duplicate");
        assertThat(signed(event(id, "evt_once", "payment.succeeded", 1.0)).getStatusCode().value())
                .isEqualTo(409);
        assertThat(
                        signed(event(id, "evt_late_failure", "payment.failed", 1328.0))
                                .getBody()
                                .get("result")
                                .asText())
                .isEqualTo("ignored");
        assertThat(status(id)).isEqualTo("confirmed");
        assertThat(stock("TRAINING-MAT")).isEqualTo(7);
        assertThat(get("/api/cart", USER).getBody().get("items")).hasSize(1);
    }

    @Test
    void underpaidEventFlagsIssueAndDoesNotConsumeStock() throws Exception {
        String id = createOrder();
        assertThat(
                        signed(event(id, "evt_under", "payment.succeeded", 1300.0))
                                .getBody()
                                .get("status")
                                .asText())
                .isEqualTo("payment_issue");
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
        assertThat(
                        signed(event(id, "evt_later_full", "payment.succeeded", 1328.0))
                                .getBody()
                                .get("status")
                                .asText())
                .isEqualTo("payment_issue");
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
    }

    @Test
    void expiryReleasesHoldAndLateSuccessCannotConfirm() throws Exception {
        String id = createOrder();
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThat(status(id)).isEqualTo("expired");
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
        assertThat(pay(USER, id, "success", key()).getStatusCode().value()).isEqualTo(409);
        String late = event(id, "evt_late", "payment.succeeded", 1328.0);
        assertThat(signed(late).getBody().get("status").asText()).isEqualTo("payment_issue");
        assertThat(signed(late).getBody().get("result").asText()).isEqualTo("duplicate");
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
    }

    @Test
    void receiptAndOrderTransitionRollbackTogether() throws Exception {
        String id = createOrder();
        String body = event(id, "evt_atomic", "payment.succeeded", 1328.0);
        jdbc.execute(
                "CREATE TRIGGER reject_receipt BEFORE INSERT ON webhook_events BEGIN SELECT"
                        + " RAISE(ABORT,'test failure'); END");
        assertThat(signed(body).getStatusCode().value()).isEqualTo(503);
        assertThat(status(id)).isEqualTo("pending");
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
        assertThat(get("/api/orders/" + id, USER).getBody().get("items")).hasSize(1);
        assertThat(count("webhook_events")).isZero();
        jdbc.execute("DROP TRIGGER reject_receipt");
        assertThat(signed(body).getBody().get("status").asText()).isEqualTo("confirmed");
    }

    @Test
    void everyAcceptedPaymentKeyStaysBoundToItsOriginalRequest() {
        String orderA = createOrder();
        String originalKey = key();
        String anotherKey = key();
        assertThat(pay(USER, orderA, "failure", originalKey).getStatusCode().value())
                .isEqualTo(200);
        assertThat(pay(USER, orderA, "failure", anotherKey).getStatusCode().value()).isEqualTo(200);
        assertThat(pay(USER, orderA, "failure", anotherKey).getStatusCode().value()).isEqualTo(200);
        assertThat(pay(USER, orderA, "success", anotherKey).getBody().get("code").asText())
                .isEqualTo("RETRY_KEY_REUSED");
        assertThat(count("payment_attempts")).isEqualTo(1);
        assertThat(count("payment_attempt_keys")).isEqualTo(2);
        assertThat(count("webhook_events")).isEqualTo(1);
        String orderB = createOrder();
        for (String used : List.of(originalKey, anotherKey)) {
            var reused = pay(USER, orderB, "failure", used);
            assertThat(reused.getStatusCode().value()).isEqualTo(409);
            assertThat(reused.getBody().get("code").asText()).isEqualTo("RETRY_KEY_REUSED");
        }
        assertThat(status(orderB)).isEqualTo("pending");
        assertThat(pay(USER, orderB, "success", key()).getBody().get("status").asText())
                .isEqualTo("confirmed");
    }

    @Test
    void simultaneousFreshKeysReuseOneAttemptAndBindBothKeys() throws Exception {
        String id = createOrder();
        String first = key();
        String second = key();
        var responses =
                race(() -> pay(USER, id, "pending", first), () -> pay(USER, id, "pending", second));
        assertThat(responses.stream().map(r -> r.getStatusCode().value())).containsOnly(200);
        assertThat(count("payment_attempts")).isEqualTo(1);
        assertThat(count("payment_attempt_keys")).isEqualTo(2);
        assertThat(count("webhook_events")).isZero();
        assertThat(pay(USER, id, "success", key()).getStatusCode().value()).isEqualTo(409);
        assertThat(count("payment_attempt_keys")).isEqualTo(2);
    }

    @Test
    void differentShoppersCanUseTheSamePaymentKey() {
        String sharedKey = key();
        String firstOrder = createOrder();
        quantity(OTHER, "TRAINING-MAT", 1);
        String secondOrder = checkout(OTHER, "addr_other", key()).getBody().get("orderId").asText();
        assertThat(pay(USER, firstOrder, "pending", sharedKey).getStatusCode().value())
                .isEqualTo(200);
        assertThat(pay(OTHER, secondOrder, "pending", sharedKey).getStatusCode().value())
                .isEqualTo(200);
        assertThat(count("payment_attempt_keys")).isEqualTo(2);
    }

    @Test
    void newBrowserKeyCanRecoverPersistedAttemptAfterWebhookDeliveryFailure() {
        String id = createOrder();
        String originalKey = key();
        jdbc.execute(
                "CREATE TRIGGER reject_receipt BEFORE INSERT ON webhook_events BEGIN SELECT"
                        + " RAISE(ABORT,'test failure'); END");
        assertThat(pay(USER, id, "success", originalKey).getStatusCode().value()).isEqualTo(503);
        assertThat(count("payment_attempts")).isEqualTo(1);
        assertThat(status(id)).isEqualTo("pending");
        assertThat(get("/api/orders/" + id, USER).getBody().get("paymentScenario").asText())
                .isEqualTo("success");
        jdbc.execute("DROP TRIGGER reject_receipt");
        assertThat(pay(USER, id, "success", key()).getBody().get("status").asText())
                .isEqualTo("confirmed");
        assertThat(count("payment_attempts")).isEqualTo(1);
        assertThat(count("webhook_events")).isEqualTo(1);
        System.out.println(
                "REVIEW: a fresh browser key currently recovers the saved attempt after delivery"
                        + " failure.");
    }

    @Test
    void processingOrderDoesNotBlockAnotherOrderOrChangeTheNewCart() {
        String first = createOrder();
        pay(USER, first, "pending", key());
        assertThat(quantity(USER, "RESISTANCE-LOOP", 2).getStatusCode().value()).isEqualTo(200);
        String second = checkout(USER, "addr_work", key()).getBody().get("orderId").asText();
        pay(USER, second, "pending", key());
        quantity(USER, "STEEL-BOTTLE", 1);
        var cartBefore = get("/api/cart", USER).getBody();
        var completed =
                request(
                        HttpMethod.POST,
                        "/api/orders/" + first + "/mock-payment/complete",
                        USER,
                        null,
                        null);
        assertThat(completed.getBody().get("status").asText()).isEqualTo("confirmed");
        assertThat(get("/api/cart", USER).getBody()).isEqualTo(cartBefore);
        assertThat(status(second)).isEqualTo("pending");
        assertThat(
                        get("/api/orders/" + first, USER)
                                .getBody()
                                .get("items")
                                .get(0)
                                .get("sku")
                                .asText())
                .isEqualTo("TRAINING-MAT");
        assertThat(
                        get("/api/orders/" + second, USER)
                                .getBody()
                                .get("items")
                                .get(0)
                                .get("sku")
                                .asText())
                .isEqualTo("RESISTANCE-LOOP");
        assertThat(get("/api/orders", USER).getBody()).hasSize(2);
        assertThat(get("/api/orders", USER).getBody())
                .anySatisfy(row -> assertThat(row.get("paymentStarted").asBoolean()).isTrue());
    }

    @Test
    void oldFailureAndExpiryLeaveNewCartUntouched() {
        String failed = createOrder();
        quantity(USER, "STEEL-BOTTLE", 1);
        var newCart = get("/api/cart", USER).getBody();
        pay(USER, failed, "failure", key());
        assertThat(get("/api/cart", USER).getBody()).isEqualTo(newCart);
        String expiring = checkout(USER, "addr_work", key()).getBody().get("orderId").asText();
        quantity(USER, "RESISTANCE-LOOP", 2);
        newCart = get("/api/cart", USER).getBody();
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThat(status(expiring)).isEqualTo("expired");
        assertThat(get("/api/cart", USER).getBody()).isEqualTo(newCart);
    }

    @Test
    void copyingFailedOrderIsOwnedRepeatableAndPreservesNewItemsAndQuantities() {
        String id = createOrder();
        assertThat(copyOrder(USER, id).getStatusCode().value()).isEqualTo(409);
        pay(USER, id, "failure", key());
        quantity(USER, "TRAINING-MAT", 2);
        quantity(USER, "STEEL-BOTTLE", 1);
        assertThat(copyOrder(OTHER, id).getStatusCode().value()).isEqualTo(404);
        var copied = copyOrder(USER, id);
        assertThat(copied.getStatusCode().value()).isEqualTo(200);
        assertThat(copied.getBody().get("items")).hasSize(2);
        assertThat(copyOrder(USER, id).getBody()).isEqualTo(copied.getBody());
        assertThat(get("/api/cart", USER).getBody().get("items").get(0).get("quantity").asInt())
                .isEqualTo(2);
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
    }

    @Test
    void copyingAnOrderRollsBackAllItemsWhenOneIsUnavailable() {
        quantity(USER, "TRAINING-MAT", 1);
        quantity(USER, "RESISTANCE-LOOP", 1);
        String id = checkout(USER, "addr_home", key()).getBody().get("orderId").asText();
        pay(USER, id, "failure", key());
        jdbc.update("UPDATE products SET stock=0 WHERE sku='RESISTANCE-LOOP'");
        var result = copyOrder(USER, id);
        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody().get("code").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
    }

    @Test
    void statusCheckCanDeliverAnExistingAttemptAfterCallbackFailure() {
        String id = createOrder();
        jdbc.execute(
                "CREATE TRIGGER reject_receipt BEFORE INSERT ON webhook_events BEGIN SELECT"
                        + " RAISE(ABORT,'test failure'); END");
        assertThat(pay(USER, id, "success", key()).getStatusCode().value()).isEqualTo(503);
        jdbc.execute("DROP TRIGGER reject_receipt");
        var result =
                request(
                        HttpMethod.POST,
                        "/api/orders/" + id + "/mock-payment/complete",
                        USER,
                        null,
                        null);
        assertThat(result.getBody().get("status").asText()).isEqualTo("confirmed");
        assertThat(count("payment_attempts")).isEqualTo(1);
        assertThat(count("webhook_events")).isEqualTo(1);
    }

    @Test
    void concurrentCheckoutsWithDifferentKeysConsumeTheCartOnlyOnce() throws Exception {
        quantity(USER, "TRAINING-MAT", 1);
        var responses =
                race(
                        () -> checkout(USER, "addr_home", key()),
                        () -> checkout(USER, "addr_home", key()));
        assertThat(responses.stream().map(r -> r.getStatusCode().value()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(stock("TRAINING-MAT")).isEqualTo(7);
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
    }

    @Test
    void failedCheckoutTransactionKeepsTheCartAndRestoresStock() {
        quantity(USER, "TRAINING-MAT", 1);
        jdbc.execute(
                "CREATE TRIGGER reject_intent BEFORE INSERT ON payment_intents BEGIN SELECT"
                    + " RAISE(ABORT,'test intent failure'); END");
        assertThat(checkout(USER, "addr_home", key()).getStatusCode().value()).isEqualTo(503);
        assertThat(count("orders")).isZero();
        assertThat(stock("TRAINING-MAT")).isEqualTo(8);
        assertThat(get("/api/cart", USER).getBody().get("items")).hasSize(1);
        jdbc.execute("DROP TRIGGER reject_intent");
        assertThat(checkout(USER, "addr_home", key()).getStatusCode().value()).isEqualTo(201);
        assertThat(get("/api/cart", USER).getBody().get("items")).isEmpty();
    }

    private ResponseEntity<JsonNode> copyOrder(String token, String id) {
        return request(HttpMethod.PUT, "/api/cart/from-orders/" + id, token, null, null);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return request(HttpMethod.GET, path, token, null, null);
    }

    private ResponseEntity<JsonNode> quantity(String token, String sku, int value) {
        return request(
                HttpMethod.PUT, "/api/cart/items/" + sku, token, Map.of("quantity", value), null);
    }

    private ResponseEntity<JsonNode> checkout(String token, String address, String key) {
        return request(HttpMethod.POST, "/api/checkouts", token, Map.of("addressId", address), key);
    }

    private ResponseEntity<JsonNode> pay(String token, String id, String outcome, String key) {
        return request(
                HttpMethod.POST,
                "/api/orders/" + id + "/mock-payment",
                token,
                Map.of("outcome", outcome),
                key);
    }

    private ResponseEntity<JsonNode> request(
            HttpMethod method, String path, String token, Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        if (key != null) headers.set("Idempotency-Key", key);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String createOrder() {
        quantity(USER, "TRAINING-MAT", 1);
        return checkout(USER, "addr_home", key()).getBody().get("orderId").asText();
    }

    private String status(String id) {
        return get("/api/orders/" + id, USER).getBody().get("status").asText();
    }

    private int stock(String sku) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE sku=?", Integer.class, sku);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private String event(String id, String eventId, String type, double amount) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "eventId",
                        eventId,
                        "intentId",
                        jdbc.queryForObject(
                                "SELECT id FROM payment_intents WHERE order_id=?",
                                String.class,
                                id),
                        "orderId",
                        id,
                        "type",
                        type,
                        "amount",
                        amount,
                        "currency",
                        "INR"));
    }

    private ResponseEntity<JsonNode> signed(String body) throws Exception {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        return webhook(body, timestamp, sign(body, timestamp));
    }

    private ResponseEntity<JsonNode> webhook(String body, String timestamp, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Payment-Timestamp", timestamp);
        headers.set("X-Payment-Signature", signature);
        return http.postForEntity(
                "/webhooks/payment", new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String sign(String body, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256="
                + HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private List<ResponseEntity<JsonNode>> race(
            java.util.concurrent.Callable<ResponseEntity<JsonNode>> first,
            java.util.concurrent.Callable<ResponseEntity<JsonNode>> second)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a =
                    pool.submit(
                            () -> {
                                start.await();
                                return first.call();
                            });
            var b =
                    pool.submit(
                            () -> {
                                start.await();
                                return second.call();
                            });
            start.countDown();
            return List.of(a.get(), b.get());
        }
    }

    private static Path temporaryDatabase() {
        try {
            return Files.createTempDirectory("kinetic-api-test-").resolve("checkout.sqlite");
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-14T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
