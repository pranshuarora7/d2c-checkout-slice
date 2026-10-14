package com.kinetic.checkout.client;

import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.PaymentAttempt;
import com.kinetic.checkout.security.WebhookSignature;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

@Component
public class MockProviderClient implements ApplicationListener<WebServerInitializedEvent> {
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final WebhookSignature signatures;
    private final Clock clock;
    private int port;

    public MockProviderClient(WebhookSignature signatures, Clock clock) {
        this.signatures = signatures;
        this.clock = clock;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
    }

    public void deliver(PaymentAttempt attempt) {
        byte[] body = attempt.payload().getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhooks/payment"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-Payment-Timestamp", timestamp)
                        .header("X-Payment-Signature", signatures.sign(body, timestamp))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        try {
            if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200)
                throw unavailable();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException error) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_UPDATE_DELAYED",
                "The payment update has not arrived. Retry the same action or check your order"
                        + " status.");
    }
}
