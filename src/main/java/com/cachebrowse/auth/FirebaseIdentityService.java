package com.cachebrowse.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserInfo;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Verifies Firebase ID tokens and exposes least-privilege Firebase user-directory operations. */
public final class FirebaseIdentityService implements IdentityVerifier {
    public record Principal(String uid, String email, String displayName,
                            boolean emailVerified, String provider) { }
    public record DirectoryUser(String uid, String email, String displayName,
                                long createdAt, boolean disabled, boolean emailVerified,
                                List<String> providers) { }
    public record UserPage(List<DirectoryUser> users, String nextPageToken) { }

    private final FirebaseAuth auth;
    private final String adminUid;

    public FirebaseIdentityService(FirebaseAuth auth, String adminUid) {
        this.auth = auth;
        this.adminUid = adminUid == null ? "" : adminUid.trim();
    }

    public static FirebaseIdentityService fromEnvironment() {
        String projectId = com.cachebrowse.config.EnvConfig.get("FIREBASE_PROJECT_ID");
        String adminUid = com.cachebrowse.config.EnvConfig.get("CACHEBROWSE_ADMIN_UID");
        if (projectId == null || projectId.isBlank()) return null;
        try {
            String credentialPath = com.cachebrowse.config.EnvConfig.get("GOOGLE_APPLICATION_CREDENTIALS");
            if (!credentialPath.isBlank()) System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", credentialPath);
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirebaseApp app;
            synchronized (FirebaseIdentityService.class) {
                app = FirebaseApp.getApps().stream()
                        .filter(candidate -> candidate.getName().equals("cachebrowse-admin"))
                        .findFirst().orElseGet(() -> {
                            try {
                                return FirebaseApp.initializeApp(FirebaseOptions.builder()
                                        .setCredentials(credentials).setProjectId(projectId).build(),
                                        "cachebrowse-admin");
                            } catch (RuntimeException error) { throw error; }
                        });
            }
            return new FirebaseIdentityService(FirebaseAuth.getInstance(app), adminUid);
        } catch (IOException | RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(FirebaseIdentityService.class)
                    .warn("Firebase Admin is not available; protected API access is disabled ({})",
                            error.getClass().getSimpleName());
            return null;
        }
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

    public boolean isAdmin(Principal principal) {
        return principal != null && !adminUid.isBlank() && adminUid.equals(principal.uid());
    }

    public UserPage listUsers(int pageSize, String pageToken) throws Exception {
        ListUsersPage page = auth.listUsers(pageToken, pageSize);
        List<DirectoryUser> users = new java.util.ArrayList<>();
        for (UserRecord user : page.getValues()) users.add(toDirectoryUser(user));
        return new UserPage(users, page.getNextPageToken());
    }

    public Optional<DirectoryUser> getUser(String uid) throws Exception {
        try { return Optional.of(toDirectoryUser(auth.getUser(uid))); }
        catch (com.google.firebase.auth.FirebaseAuthException error) {
            if ("user-not-found".equals(error.getErrorCode())) return Optional.empty();
            throw error;
        }
    }

    private DirectoryUser toDirectoryUser(UserRecord user) {
        return new DirectoryUser(user.getUid(), user.getEmail(), user.getDisplayName(),
                user.getUserMetadata() == null ? 0 : user.getUserMetadata().getCreationTimestamp(),
                user.isDisabled(), user.isEmailVerified(),
                java.util.Arrays.stream(user.getProviderData()).map(UserInfo::getProviderId).toList());
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text ? text : null;
    }
}
