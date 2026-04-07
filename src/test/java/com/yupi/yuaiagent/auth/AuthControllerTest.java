package com.yupi.yuaiagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.yupi.yuaiagent.auth.AuthInterceptor;
import com.yupi.yuaiagent.config.WebMvcAuthConfig;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({WebMvcAuthConfig.class, AuthInterceptor.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void registerShouldReturnTokenAndUserInfo() throws Exception {
        when(authService.register("alice", "secret"))
                .thenReturn(new AuthService.AuthResult("token-1", new AuthService.UserInfo("user-1", "alice")));

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-1"))
                .andExpect(jsonPath("$.userInfo.id").value("user-1"))
                .andExpect(jsonPath("$.userInfo.username").value("alice"));
    }

    @Test
    void registerShouldReturnConflictWhenUsernameExists() throws Exception {
        when(authService.register("alice", "secret"))
                .thenThrow(new AuthService.DuplicateUsernameException("Username already exists"));

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginShouldReturnTokenAndUserInfo() throws Exception {
        when(authService.login("alice", "secret"))
                .thenReturn(new AuthService.AuthResult("token-2", new AuthService.UserInfo("user-1", "alice")));

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-2"))
                .andExpect(jsonPath("$.userInfo.id").value("user-1"))
                .andExpect(jsonPath("$.userInfo.username").value("alice"));
    }

    @Test
    void loginShouldReturnUnauthorizedForBadCredentials() throws Exception {
        when(authService.login("alice", "secret"))
                .thenThrow(new AuthService.InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnCurrentUserInfoFromBearerToken() throws Exception {
        when(authService.me("token-3"))
                .thenReturn(new AuthService.UserInfo("user-3", "carol"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer token-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-3"))
                .andExpect(jsonPath("$.username").value("carol"));

        verify(authService).me(eq("token-3"));
    }

    @Test
    void meShouldReturnUnauthorizedWhenBearerTokenIsMissing() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnUnauthorizedWhenBearerTokenIsMalformed() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Token token-3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnUnauthorizedWhenTokenIsExpired() throws Exception {
        when(authService.me("expired-token"))
                .thenThrow(new AuthService.InvalidTokenException("Token expired"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }
}
