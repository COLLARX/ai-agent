package com.yupi.yuaiagent.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private AuthService authService;

    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() {
        authInterceptor = new AuthInterceptor(authService);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void preHandleShouldRejectMissingBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rag/upload-md");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = authInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(AuthContext.getCurrentUser()).isNull();
    }

    @Test
    void preHandleShouldRejectMalformedBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rag/upload-md");
        request.addHeader("Authorization", "Token token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = authInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(AuthContext.getCurrentUser()).isNull();
    }

    @Test
    void preHandleShouldRejectInvalidJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rag/upload-md");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authService.me("invalid-token"))
                .thenThrow(new AuthService.InvalidTokenException("Invalid token"));

        boolean allowed = authInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(AuthContext.getCurrentUser()).isNull();
    }

    @Test
    void preHandleShouldPopulateAuthContextForValidJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rag/upload-md");
        request.addHeader("Authorization", "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authService.me("token-1"))
                .thenReturn(new AuthService.UserInfo("user-1", "alice"));

        boolean allowed = authInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(AuthContext.getCurrentUser()).isEqualTo(new AuthenticatedUser("user-1", "alice"));
    }

    @Test
    void afterCompletionShouldClearAuthContext() {
        AuthContext.setCurrentUser(new AuthenticatedUser("user-1", "alice"));

        authInterceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(AuthContext.getCurrentUser()).isNull();
    }

    @Test
    void preHandleShouldClearAuthContextWhenUnexpectedFailureOccurs() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rag/upload-md");
        request.addHeader("Authorization", "Bearer token-3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthContext.setCurrentUser(new AuthenticatedUser("stale-user", "stale"));

        when(authService.me("token-3"))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> authInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(AuthContext.getCurrentUser()).isNull();
    }
}
