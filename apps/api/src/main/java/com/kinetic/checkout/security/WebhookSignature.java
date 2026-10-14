package com.kinetic.checkout.security;

import com.kinetic.checkout.exception.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class WebhookSignature {
    private final byte[] secret;
    private final Clock clock;

    public WebhookSignature(@Value("${app.webhook-secret}") String secret, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public void verify(byte[] body, String timestamp, String signature) {
        if (timestamp == null
                || !timestamp.matches("[0-9]{1,12}")
                || signature == null
                || !signature.matches("sha256=[0-9a-f]{64}")) {
            throw invalid();
        }
        long age = clock.instant().getEpochSecond() - Long.parseLong(timestamp);
        if (Math.abs(age) > 300
                || !MessageDigest.isEqual(
                        HexFormat.of().parseHex(sign(body, timestamp).substring(7)),
                        HexFormat.of().parseHex(signature.substring(7)))) {
            throw invalid();
        }
    }

    public String sign(byte[] body, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
    }

    public static String bodyHash(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private ApiException invalid() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_SIGNATURE",
                "The payment signature or timestamp is invalid.");
    }
}
