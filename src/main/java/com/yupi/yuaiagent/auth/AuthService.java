package com.yupi.yuaiagent.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.sql.SQLException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;

    public AuthService(JdbcTemplate jdbcTemplate, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
    }

    public AuthResult register(String username, String password) {
        ensureUsernameAvailable(username);
        String id = UUID.randomUUID().toString();
        String passwordHash = hashPassword(password);
        try {
            jdbcTemplate.update("""
                    insert into app_user (id, username, password_hash)
                    values (?, ?, ?)
                    """, id, username, passwordHash);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueUsernameViolation(e)) {
                throw new DuplicateUsernameException("Username already exists");
            }
            throw e;
        }
        return new AuthResult(jwtService.createToken(id, username), new UserInfo(id, username));
    }

    public AuthResult login(String username, String password) {
        AuthUser user = findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        if (!matchesPassword(password, user.passwordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        return new AuthResult(jwtService.createToken(user.id(), user.username()), new UserInfo(user.id(), user.username()));
    }

    public UserInfo me(String token) {
        JwtService.Claims claims = jwtService.parseAndValidate(token);
        AuthUser user = findById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));
        return new UserInfo(user.id(), user.username());
    }

    String hashPassword(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    boolean matchesPassword(String rawPassword, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 3) {
                return false;
            }
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);
            byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
            return constantTimeEquals(expectedHash, actualHash);
        } catch (RuntimeException e) {
            return false;
        }
    }

    Optional<AuthUser> findByUsername(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select id, username, password_hash
                from app_user
                where username = ?
                limit 1
                """, username);
        return rows.stream().findFirst().map(this::toAuthUser);
    }

    Optional<AuthUser> findById(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select id, username, password_hash
                from app_user
                where id = ?
                limit 1
                """, id);
        return rows.stream().findFirst().map(this::toAuthUser);
    }

    private void ensureUsernameAvailable(String username) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from app_user
                where username = ?
                """, Integer.class, username);
        if (count != null && count > 0) {
            throw new DuplicateUsernameException("Username already exists");
        }
    }

    private AuthUser toAuthUser(Map<String, Object> row) {
        return new AuthUser(
                String.valueOf(row.get("id")),
                String.valueOf(row.get("username")),
                String.valueOf(row.get("password_hash"))
        );
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    private boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length; i++) {
            diff |= expected[i] ^ actual[i];
        }
        return diff == 0;
    }

    private boolean isUniqueUsernameViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record AuthResult(String token, UserInfo userInfo) {
    }

    public record UserInfo(String id, String username) {
    }

    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String message) {
            super(message);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
