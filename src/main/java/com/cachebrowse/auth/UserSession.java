package com.cachebrowse.auth;

import com.cachebrowse.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the current authenticated user session in the application.
 *
 * <p><b>Anonymous user identity:</b> When no real user is logged in the session
 * uses {@link #ANONYMOUS_USER_ID} = {@value #ANONYMOUS_USER_ID}.  This value
 * MUST match the bootstrap row that {@code DatabaseManager.initSchema()} inserts
 * into the {@code users} table so that child tables (history, bookmarks,
 * browser_settings, statistics) can satisfy their
 * {@code FOREIGN KEY (user_id) REFERENCES users(id)} constraint even when
 * no real user is authenticated.</p>
 */
public class UserSession {
    private static UserSession instance;

    /**
     * The canonical anonymous/unauthenticated user ID.  Must stay in sync with the
     * bootstrap INSERT in {@link DatabaseManager#initSchema()}.  A special entry
     * is inserted into the {@code users} table with this value so that child tables
     * (history, bookmarks, browser_settings, statistics) can satisfy their
     * {@code FOREIGN KEY (user_id) REFERENCES users(id)} constraint even when
     * no real user is authenticated.
     */
    public static final String ANONYMOUS_USER_ID    = "default-user";
    public static final String ANONYMOUS_USER_EMAIL = "default@cachebrowse.local";

    // The current authenticated user.  If null or equal to the anonymous ID,
    // then no real user is logged in.
    private User currentUser;

    /**
     * Optional Firebase auth tokens for the currently authenticated session.
     * <p>
     * {@code idToken} is the short‑lived JWT returned by the sign‑in or sign‑up
     * endpoint.  {@code refreshToken} can be used to obtain a new ID token once
     * it expires.  {@code tokenExpiryMs} stores the absolute epoch time (ms)
     * when {@link #idToken} will no longer be valid.
     */
    private String idToken;
    private String refreshToken;
    private long tokenExpiryMs;

    private final List<Consumer<User>> sessionListeners = new ArrayList<>();

    public UserSession() {
        // Anonymous session — ID matches the bootstrapped users row so FK constraints pass.
        this.currentUser = new User(ANONYMOUS_USER_ID, ANONYMOUS_USER_EMAIL);
    }

    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    /** Returns the current user's database ID. Never null — returns the anonymous ID as fallback. */
    public synchronized String getUserId() {
        return currentUser != null ? currentUser.getId() : ANONYMOUS_USER_ID;
    }

    public synchronized String getEmail() {
        return currentUser != null ? currentUser.getEmail() : ANONYMOUS_USER_EMAIL;
    }

    /**
     * Returns {@code true} only when a real (non-anonymous) user is logged in.
     * The anonymous ID is {@link #ANONYMOUS_USER_ID}; any other valid ID means logged-in.
     */
    public synchronized boolean isLoggedIn() {
        return currentUser != null && !ANONYMOUS_USER_ID.equals(currentUser.getId());
    }

    /**
     * Log in a user by their unique ID and e‑mail.  This method does not
     * touch the authentication tokens – those are set via
     * {@link #setAuthTokens(String, String, long)}.
     */
    public synchronized void login(String userId, String email) {
        String effectiveId = (userId != null && !userId.isBlank()) ? userId : java.util.UUID.randomUUID().toString();
        this.currentUser = new User(effectiveId, email);
        notifyListeners();
    }

    public synchronized void login(User user) {
        this.currentUser = user != null ? user : new User(ANONYMOUS_USER_ID, ANONYMOUS_USER_EMAIL);
        notifyListeners();
    }

    /**
     * Log out the current user.  The session is reset to the anonymous
     * placeholder and any stored authentication tokens are cleared.
     */
    public synchronized void logout() {
        // Restore to anonymous — still DB‑safe because the anonymous row exists in users.
        this.currentUser = new User(ANONYMOUS_USER_ID, ANONYMOUS_USER_EMAIL);
        clearAuthTokens();
        notifyListeners();
    }

    public synchronized void addListener(Consumer<User> listener) {
        if (listener != null) {
            sessionListeners.add(listener);
        }
    }

    public synchronized void removeListener(Consumer<User> listener) {
        sessionListeners.remove(listener);
    }

    /**
     * Notify all registered listeners about a change in the authenticated user.
     */
    private void notifyListeners() {
        for (Consumer<User> listener : new ArrayList<>(sessionListeners)) {
            try {
                listener.accept(currentUser);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Associate authentication tokens with the current session.  This method
     * should only be called immediately after a successful Firebase sign‑in.
     */
    public synchronized void setAuthTokens(String idToken, String refreshToken, long tokenExpiryMs) {
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.tokenExpiryMs = tokenExpiryMs;
    }

    /**
     * Clear any stored authentication tokens.
     */
    public synchronized void clearAuthTokens() {
        this.idToken = null;
        this.refreshToken = null;
        this.tokenExpiryMs = 0L;
    }

    /** @return the Firebase ID token or {@code null} if not authenticated. */
    public synchronized String getIdToken() {
        return idToken;
    }

    /** @return the Firebase refresh token or {@code null} if not authenticated. */
    public synchronized String getRefreshToken() {
        return refreshToken;
    }

    /** @return the epoch milliseconds when the current ID token expires, 0 if unknown. */
    public synchronized long getTokenExpiryMs() {
        return tokenExpiryMs;
    }
}
