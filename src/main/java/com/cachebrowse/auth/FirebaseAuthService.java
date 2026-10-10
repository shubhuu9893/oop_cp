package com.cachebrowse.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import com.cachebrowse.config.EnvConfig;

/**
 * Lightweight wrapper around the Firebase Identity Toolkit REST API.
 */
public class FirebaseAuthService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // Environment variables or .env values
    private static final String API_KEY;
    static {
        API_KEY = Boolean.parseBoolean(EnvConfig.get("CACHEBROWSE_DISABLE_FIREBASE"))
                ? "" : EnvConfig.get("FIREBASE_API_KEY");
    }

    /** True when Firebase authentication is configured through the environment or project .env. */
    public static boolean isConfigured() {
        return API_KEY != null && !API_KEY.isBlank();
    }

    /** Simple container for authentication results. */
    public static class AuthResult {
        public final String uid;
        public final String email;
        public final String idToken;
        public final String refreshToken;
        public final long expiresInSeconds; // from Firebase response

        public AuthResult(String uid, String email, String idToken, String refreshToken, long expiresInSeconds) {
            this.uid = uid;
            this.email = email;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    /** Sign in with email/password. */
    public AuthResult signInWithPassword(String email, String password) throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new IllegalStateException("Missing Firebase API key.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw firebaseError("sign-in", response.body());
        }
        JsonNode node = MAPPER.readTree(response.body());
        return new AuthResult(
                node.get("localId").asText(),
                node.get("email").asText(),
                node.get("idToken").asText(),
                node.get("refreshToken").asText(),
                node.get("expiresIn").asLong());
    }

    /** Register a new user with email/password. */
    public AuthResult signUp(String email, String password) throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new IllegalStateException("Missing Firebase API key.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + API_KEY))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw firebaseError("sign-up", response.body());
        }
        JsonNode node = MAPPER.readTree(response.body());
        return new AuthResult(
                node.get("localId").asText(),
                node.get("email").asText(),
                node.get("idToken").asText(),
                node.get("refreshToken").asText(),
                node.get("expiresIn").asLong());
    }

    /** Refresh an expired ID token using the refresh token. */
    public AuthResult refresh(String refreshToken) throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new IllegalStateException("Missing Firebase API key.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("grant_type", "refresh_token");
        payload.put("refresh_token", refreshToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://securetoken.googleapis.com/v1/token?key=" + API_KEY))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw firebaseError("token refresh", response.body());
        }
        JsonNode node = MAPPER.readTree(response.body());
        return new AuthResult(
                node.get("user_id").asText(), // user_id is the UID
                null,
                node.get("id_token").asText(),
                node.get("refresh_token").asText(),
                node.get("expires_in").asLong());
    }

    private IOException firebaseError(String action, String responseBody) {
        String code = "UNKNOWN";
        try {
            JsonNode parsed = MAPPER.readTree(responseBody);
            code = parsed.path("error").path("message").asText("UNKNOWN");
        } catch (Exception ignored) { }
        // Firebase error bodies can contain request context; expose only the provider error code.
        return new IOException("Firebase " + action + " failed (" + code + ")");
    }
}
