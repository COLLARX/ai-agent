package com.yupi.yuaiagent.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class JwtService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final SecretKeySpec secretKeySpec;
    private final long ttlSeconds;

    public JwtService(@Value("${app.auth.jwt-secret:dev-secret-change-me}") String secret,
                      @Value("${app.auth.jwt-ttl-minutes:120}") long ttlMinutes) {
        String normalizedSecret = secret == null ? "" : secret.trim();
        if (normalizedSecret.isEmpty() || "dev-secret-change-me".equals(normalizedSecret)) {
            throw new IllegalArgumentException("JWT secret must be configured with a non-empty non-default value");
        }
        this.secretKeySpec = new SecretKeySpec(normalizedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.ttlSeconds = Math.max(1L, ttlMinutes) * 60L;
    }

    public String createToken(String userId, String username) {
        Instant issuedAt = Instant.now();
        JwtPayload payload = new JwtPayload(userId, username, issuedAt.getEpochSecond(), issuedAt.plusSeconds(ttlSeconds).getEpochSecond());
        String encodedHeader = ENCODER.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = ENCODER.encodeToString(writeJson(payload).getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedHeader + "." + encodedPayload);
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    public Claims parseAndValidate(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new AuthService.InvalidTokenException("Invalid token");
        }
        JwtPayload payload;
        try {
            String signedContent = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(signBytes(signedContent), DECODER.decode(parts[2]))) {
                throw new AuthService.InvalidTokenException("Invalid token");
            }
            payload = readJson(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8), JwtPayload.class);
        } catch (IllegalArgumentException e) {
            throw new AuthService.InvalidTokenException("Invalid token");
        }
        if (Instant.now().getEpochSecond() > payload.exp()) {
            throw new AuthService.InvalidTokenException("Token expired");
        }
        return new Claims(payload.sub(), payload.username());
    }

    private String sign(String content) {
        return ENCODER.encodeToString(signBytes(content));
    }

    private byte[] signBytes(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign token", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize token payload", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    record JwtPayload(String sub, String username, long iat, long exp) {
    }

    public record Claims(String userId, String username) {
    }
}
