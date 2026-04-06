package com.yupi.yuaiagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JwtService jwtService = new JwtService("test-secret", 60);
    private final AuthService authService = new AuthService(jdbcTemplate, jwtService);

    @Test
    void registerShouldHashPasswordAndReturnTokenAndUserInfo() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("alice")))
                .thenReturn(0);

        AtomicReference<Object[]> insertArgs = new AtomicReference<>();
        doAnswer(invocation -> {
            insertArgs.set(invocation.getArguments());
            return 1;
        }).when(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString());

        AuthService.AuthResult result = authService.register("alice", "secret");

        assertThat(result.token()).isNotBlank();
        assertThat(result.userInfo().username()).isEqualTo("alice");
        assertThat(result.userInfo().id()).isNotBlank();

        Object[] args = insertArgs.get();
        assertThat(args).isNotNull();
        assertThat(args[1]).isEqualTo(result.userInfo().id());
        assertThat(args[2]).isEqualTo("alice");
        assertThat(args[3]).isInstanceOf(String.class);
        assertThat(args[3]).isNotEqualTo("secret");
    }

    @Test
    void registerShouldConvertDatabaseUniqueViolationToDuplicateUsername() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("alice")))
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23505")));

        assertThatThrownBy(() -> authService.register("alice", "secret"))
                .isInstanceOf(AuthService.DuplicateUsernameException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void loginShouldValidatePasswordAgainstStoredHash() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("alice")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("alice"))).thenReturn(List.of(Map.of(
                "id", "user-1",
                "username", "alice",
                "password_hash", authService.hashPassword("secret")
        )));

        AuthService.AuthResult result = authService.login("alice", "secret");

        assertThat(result.token()).isNotBlank();
        assertThat(result.userInfo()).isEqualTo(new AuthService.UserInfo("user-1", "alice"));
    }

    @Test
    void meShouldReadBearerTokenAndReturnCurrentUserInfo() {
        String token = jwtService.createToken("user-1", "alice");
        when(jdbcTemplate.queryForList(anyString(), eq("user-1"))).thenReturn(List.of(Map.of(
                "id", "user-1",
                "username", "alice",
                "password_hash", "ignored"
        )));

        AuthService.UserInfo userInfo = authService.me(token);

        assertThat(userInfo).isEqualTo(new AuthService.UserInfo("user-1", "alice"));
    }

    @Test
    void loginShouldRejectWrongPassword() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("alice")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("alice"))).thenReturn(List.of(Map.of(
                "id", "user-1",
                "username", "alice",
                "password_hash", authService.hashPassword("secret")
        )));

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(AuthService.InvalidCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void loginShouldRejectMalformedStoredHash() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("alice")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("alice"))).thenReturn(List.of(Map.of(
                "id", "user-1",
                "username", "alice",
                "password_hash", "corrupted-hash"
        )));

        assertThatThrownBy(() -> authService.login("alice", "secret"))
                .isInstanceOf(AuthService.InvalidCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void jwtServiceShouldRejectBlankSecret() {
        assertThatThrownBy(() -> new JwtService("   ", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT secret must be configured");
    }

    @Test
    void jwtServiceShouldRejectInsecureDefaultSecret() {
        assertThatThrownBy(() -> new JwtService("dev-secret-change-me", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT secret must be configured");
    }

    @Test
    void jwtServiceShouldRejectTamperedToken() {
        String token = jwtService.createToken("user-1", "alice");
        String tamperedToken = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> jwtService.parseAndValidate(tamperedToken))
                .isInstanceOf(AuthService.InvalidTokenException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void jwtServiceShouldRejectExpiredToken() {
        String token = createExpiredToken("test-secret", "user-1", "alice");

        assertThatThrownBy(() -> jwtService.parseAndValidate(token))
                .isInstanceOf(AuthService.InvalidTokenException.class)
                .hasMessageContaining("Token expired");
    }

    private String createExpiredToken(String secret, String userId, String username) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            long issuedAt = Instant.now().minusSeconds(3600).getEpochSecond();
            long expiredAt = Instant.now().minusSeconds(60).getEpochSecond();
            String payload = """
                    {"sub":"%s","username":"%s","iat":%d,"exp":%d}
                    """.formatted(userId, username, issuedAt, expiredAt).replaceAll("\\s+", "");
            String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            String signature = sign(secret, header + "." + encodedPayload);
            return header + "." + encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build expired token", e);
        }
    }

    private String sign(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
