package com.cachebrowse.service;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.auth.SessionData;
import com.cachebrowse.auth.SessionStorage;
import com.cachebrowse.database.repository.UserRepository;
import com.cachebrowse.auth.FirebaseAuthService;
import com.cachebrowse.model.User;
// AuthDialog is no longer used in this service.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing Firebase authentication and its local session projection.
 * Authentication is deliberately unavailable when Firebase is not configured;
 * a local user row is not proof of identity.
 */
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final UserSession userSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuthService-Worker");
        t.setDaemon(true);
        return t;
    });

    // Firebase integration – may be disabled if API key missing
    private final boolean firebaseEnabled;
    private final FirebaseAuthService firebaseAuthService;

    public AuthenticationService(UserRepository userRepository, UserSession userSession) {
        this.userRepository = userRepository;
        this.userSession = userSession != null ? userSession : UserSession.getInstance();
        // Determine whether Firebase is available
        // FirebaseAuthService resolves the project .env as well as process env.
        // Ask that same source of truth here so a key in .env does not silently
        // downgrade authentication to local fallback mode.
        if (!FirebaseAuthService.isConfigured()) {
            firebaseEnabled = false;
            firebaseAuthService = null;
        } else {
            firebaseEnabled = true;
            firebaseAuthService = new FirebaseAuthService();
        }

        // A persisted client session is only trusted when Firebase is configured.
        SessionStorage.load().ifPresent(data -> {
            if (firebaseEnabled) {
                long now = Instant.now().toEpochMilli();
                if (data.getExpiresAtMs() > now + 60_000) { // token still valid or near expiry
                    userSession.login(data.getUid(), data.getEmail());
                    userSession.setAuthTokens(data.getIdToken(), data.getRefreshToken(), data.getExpiresAtMs());
                } else {
                    try {
                        FirebaseAuthService.AuthResult refreshed = firebaseAuthService.refresh(data.getRefreshToken());
                        long expiry = Instant.now().toEpochMilli() + refreshed.expiresInSeconds * 1000;
                        userSession.login(refreshed.uid, data.getEmail());
                        userSession.setAuthTokens(refreshed.idToken, refreshed.refreshToken, expiry);
                        SessionStorage.save(new SessionData(refreshed.uid, data.getEmail(), refreshed.idToken, refreshed.refreshToken, expiry));
                    } catch (Exception e) {
                        logout();
                    }
                }
            } else SessionStorage.delete();
        });
    }

    public CompletableFuture<Boolean> loginAsync(String email, String password) {
        return CompletableFuture.supplyAsync(() -> login(email, password), executor);
    }

    public boolean login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!firebaseEnabled) {
            LOG.warn("Firebase login unavailable: Firebase API key is not configured.");
            return false;
        }
        try {
            FirebaseAuthService.AuthResult result = firebaseAuthService.signInWithPassword(cleanEmail, password);
            // Ensure user exists locally
            User localUser = new User(result.uid, result.email);
            if (!userRepository.getUserById(result.uid).isPresent()) {
                try { userRepository.createUser(localUser); } catch (Exception ignored) {}
            }
            userSession.login(result.uid, result.email);
            long expiry = Instant.now().toEpochMilli() + result.expiresInSeconds * 1000;
            userSession.setAuthTokens(result.idToken, result.refreshToken, expiry);
            SessionStorage.save(new SessionData(result.uid, result.email, result.idToken, result.refreshToken, expiry));
            return true;
        } catch (Exception e) {
            LOG.warn("Firebase login failed ({})", e instanceof java.net.http.HttpTimeoutException ? "timeout" : "provider_or_network_error");
            return false;
        }
    }

    public CompletableFuture<Boolean> registerAsync(String email, String password) {
        return CompletableFuture.supplyAsync(() -> register(email, password), executor);
    }

    public boolean register(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.length() < 6) {
            return false;
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!firebaseEnabled) {
            LOG.warn("Firebase registration unavailable: Firebase API key is not configured.");
            return false;
        }
        try {
            FirebaseAuthService.AuthResult result = firebaseAuthService.signUp(cleanEmail, password);
            User localUser = new User(result.uid, result.email);
            if (!userRepository.getUserById(result.uid).isPresent()) {
                try { userRepository.createUser(localUser); } catch (Exception ignored) {}
            }
            userSession.login(result.uid, result.email);
            long expiry = Instant.now().toEpochMilli() + result.expiresInSeconds * 1000;
            userSession.setAuthTokens(result.idToken, result.refreshToken, expiry);
            SessionStorage.save(new SessionData(result.uid, result.email, result.idToken, result.refreshToken, expiry));
            return true;
        } catch (Exception e) {
            LOG.warn("Firebase registration failed ({})", e instanceof java.net.http.HttpTimeoutException ? "timeout" : "provider_or_network_error");
            return false;
        }
    }

    public void logout() {
        userSession.logout();
        SessionStorage.delete();
    }

    public User getCurrentUser() {
        return userSession.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return userSession.isLoggedIn();
    }

    public boolean isFirebaseConfigured() { return firebaseEnabled; }

    public UserSession getUserSession() {
        return userSession;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
