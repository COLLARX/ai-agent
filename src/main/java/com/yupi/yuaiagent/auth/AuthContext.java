package com.yupi.yuaiagent.auth;

public final class AuthContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setCurrentUser(AuthenticatedUser authenticatedUser) {
        CURRENT_USER.set(authenticatedUser);
    }

    public static AuthenticatedUser getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return currentUser;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
