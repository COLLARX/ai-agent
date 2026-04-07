package com.yupi.yuaiagent.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthService.AuthResult register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request.username(), request.password());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public AuthService.UserInfo me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(extractBearerToken(authorization));
    }

    @ExceptionHandler(AuthService.DuplicateUsernameException.class)
    public ResponseEntity<Void> handleDuplicateUsername() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    public ResponseEntity<Void> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(AuthService.InvalidTokenException.class)
    public ResponseEntity<Void> handleInvalidToken() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthService.InvalidTokenException("Missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new AuthService.InvalidTokenException("Missing bearer token");
        }
        return token;
    }

    public record AuthRequest(@NotBlank String username, @NotBlank String password) {
    }
}
