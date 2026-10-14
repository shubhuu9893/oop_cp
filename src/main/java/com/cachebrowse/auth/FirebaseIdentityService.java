package com.cachebrowse.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Verifies Firebase ID tokens server-side for user-scoped API access. */
public final class FirebaseIdentityService implements IdentityVerifier {
    public record Principal(String uid, String email, String displayName,
                            boolean emailVerified, String provider) { }

    private final FirebaseAuth auth;
    private static volatile String initializationIssue = "not_initialized";

    public FirebaseIdentityService(FirebaseAuth auth) {
        this.auth = auth;
    }

    public static FirebaseIdentityService fromEnvironment() {
        String projectId = com.cachebrowse.config.EnvConfig.get("FIREBASE_PROJECT_ID");
        if (projectId.isBlank()) {
            initializationIssue = "missing_firebase_project_id";
            logUnavailable(initializationIssue);
            return null;
        }
        try {
            String credentialPath = com.cachebrowse.config.EnvConfig.get("GOOGLE_APPLICATION_CREDENTIALS");
            GoogleCredentials credentials;
            if (!credentialPath.isBlank()) {
                Path resolved = com.cachebrowse.config.EnvConfig.resolveProjectPath(credentialPath).toAbsolutePath();
                if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
                    initializationIssue = "service_account_file_missing_or_unreadable";
                    logUnavailable(initializationIssue);
                    return null;
                }
                // ADC reads the process environment, not Java system
                // properties. Load an explicitly configured file directly so
                // .env-based configuration works reliably from Maven/IDE
                // launches as well.
                try (InputStream stream = Files.newInputStream(resolved)) {
                    credentials = ServiceAccountCredentials.fromStream(stream);
                }
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }
            if (credentials instanceof ServiceAccountCredentials serviceAccount
                    && serviceAccount.getProjectId() != null
                    && !projectId.equals(serviceAccount.getProjectId())) {
                initializationIssue = "service_account_project_mismatch";
                logUnavailable(initializationIssue);
                return null;
            }
            FirebaseApp app;
            synchronized (FirebaseIdentityService.class) {
                app = FirebaseApp.getApps().stream()
                        .filter(candidate -> candidate.getName().equals("cachebrowse-token-verifier"))
                        .findFirst().orElseGet(() -> {
                            try {
                                return FirebaseApp.initializeApp(FirebaseOptions.builder()
                                        .setCredentials(credentials).setProjectId(projectId).build(),
                                        "cachebrowse-token-verifier");
                            } catch (RuntimeException error) { throw error; }
                        });
            }
            initializationIssue = "none";
            org.slf4j.LoggerFactory.getLogger(FirebaseIdentityService.class)
                    .info("Firebase ID-token verification initialized for configured project.");
            return new FirebaseIdentityService(FirebaseAuth.getInstance(app));
        } catch (IOException | RuntimeException error) {
            String credentialPath = com.cachebrowse.config.EnvConfig.get("GOOGLE_APPLICATION_CREDENTIALS");
            initializationIssue = credentialPath.isBlank()
                    ? "application_default_credentials_unavailable"
                    : "service_account_credentials_invalid";
            org.slf4j.LoggerFactory.getLogger(FirebaseIdentityService.class)
                    .warn("Firebase Admin initialization failed ({})", initializationIssue);
            logUnavailable(initializationIssue);
            return null;
        }
    }

    public static String getInitializationIssue() { return initializationIssue; }

    private static void logUnavailable(String issue) {
        org.slf4j.LoggerFactory.getLogger(FirebaseIdentityService.class)
                .warn("Firebase ID-token verification is unavailable ({}); protected API access remains disabled", issue);
    }

    public Principal verify(String idToken) throws Exception {
        FirebaseToken decoded = auth.verifyIdToken(idToken, true);
        Map<String, Object> claims = decoded.getClaims();
        Object firebaseClaim = claims.get("firebase");
        String provider = "unknown";
        if (firebaseClaim instanceof Map<?, ?> firebase) {
            Object signIn = firebase.get("sign_in_provider");
            if (signIn instanceof String value) provider = value;
        }
        return new Principal(decoded.getUid(), stringClaim(claims, "email"),
                stringClaim(claims, "name"), Boolean.TRUE.equals(claims.get("email_verified")), provider);
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text ? text : null;
    }
}
