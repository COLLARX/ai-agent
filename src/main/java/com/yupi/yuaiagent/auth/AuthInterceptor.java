package com.yupi.yuaiagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        AuthContext.clear();
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = extractBearerToken(request.getHeader(AUTHORIZATION_HEADER));
        if (token == null) {
            unauthorized(response);
            return false;
        }

        try {
            AuthService.UserInfo userInfo = authService.me(token);
            AuthContext.setCurrentUser(new AuthenticatedUser(userInfo.id(), userInfo.username()));
            return true;
        } catch (AuthService.InvalidTokenException ex) {
            unauthorized(response);
            return false;
        } catch (RuntimeException ex) {
            AuthContext.clear();
            throw ex;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void unauthorized(HttpServletResponse response) {
        AuthContext.clear();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
