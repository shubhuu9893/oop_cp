package com.cachebrowse.database.repository;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.database.D1Client;
import com.cachebrowse.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 implementation of UserRepository.
 * Handles mapping between verified Firebase UIDs and database primary key IDs,
 * ensuring referential integrity for child tables (history, bookmarks, settings, statistics).
 */
public class D1UserRepository implements UserRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1UserRepository.class);

    private final D1Client d1Client;
    private final Map<String, User> localUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> syncLocks = new ConcurrentHashMap<>();

    public D1UserRepository(D1Client d1Client) {
        this.d1Client = d1Client;
        // Pre-populate canonical anonymous user in local cache
        User defaultUser = new User(
                UserSession.ANONYMOUS_USER_ID,
                UserSession.ANONYMOUS_USER_ID,
                UserSession.ANONYMOUS_USER_EMAIL,
                Instant.now()
        );
        localUsers.put(UserSession.ANONYMOUS_USER_ID, defaultUser);
    }

    @Override
    public void createUser(User user) {
        if (user == null || user.getId() == null) return;
        String uid = user.getFirebaseUid() != null ? user.getFirebaseUid() : user.getId();
        String email = user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : (user.getId() + "@cachebrowse.local");

        if (d1Client != null && d1Client.isConfigured() && d1Client.isAvailable()) {
            try {
                String sql = "INSERT INTO users (id, firebase_uid, email, created_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET firebase_uid = excluded.firebase_uid, email = excluded.email";
                List<Object> params = List.of(
                        user.getId(),
                        uid,
                        email,
                        user.getCreatedAt() != null ? user.getCreatedAt().toString() : Instant.now().toString()
                );
                d1Client.executeChecked(sql, params);
            } catch (Exception e) {
                LOG.warn("Failed to persist user to D1 ({}); local fallback active", e.getMessage());
            }
        }
        localUsers.put(user.getId(), user);
        if (user.getFirebaseUid() != null) {
            localUsers.put(user.getFirebaseUid(), user);
        }
    }

    @Override
    public Optional<User> getUserById(String id) {
        if (id == null) return Optional.empty();

        if (d1Client != null && d1Client.isConfigured() && d1Client.isAvailable()) {
            try {
                String sql = "SELECT id, firebase_uid, email, created_at FROM users WHERE id = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(id));
                if (!rows.isEmpty()) {
                    User user = mapRowToUser(rows.get(0));
                    localUsers.put(id, user);
                    return Optional.of(user);
                }
            } catch (Exception e) {
                LOG.warn("Failed to query user by id from D1: {}", e.getMessage());
            }
        }

        return Optional.ofNullable(localUsers.get(id));
    }

    @Override
    public Optional<User> getUserByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) return Optional.empty();

        if (d1Client != null && d1Client.isConfigured() && d1Client.isAvailable()) {
            try {
                String sql = "SELECT id, firebase_uid, email, created_at FROM users WHERE firebase_uid = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(firebaseUid));
                if (!rows.isEmpty()) {
                    User user = mapRowToUser(rows.get(0));
                    localUsers.put(user.getId(), user);
                    localUsers.put(firebaseUid, user);
                    return Optional.of(user);
                }
            } catch (Exception e) {
                LOG.warn("Failed to query user by firebase_uid from D1: {}", e.getMessage());
            }
        }

        return localUsers.values().stream()
                .filter(u -> firebaseUid.equals(u.getFirebaseUid()) || firebaseUid.equals(u.getId()))
                .findFirst();
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();

        if (d1Client != null && d1Client.isConfigured() && d1Client.isAvailable()) {
            try {
                String sql = "SELECT id, firebase_uid, email, created_at FROM users WHERE email = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(email));
                if (!rows.isEmpty()) {
                    User user = mapRowToUser(rows.get(0));
                    localUsers.put(user.getId(), user);
                    return Optional.of(user);
                }
            } catch (Exception e) {
                LOG.warn("Failed to query user by email from D1: {}", e.getMessage());
            }
        }

        return localUsers.values().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    @Override
    public User resolveOrCreateByFirebaseUid(String firebaseUid, String email) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return localUsers.get(UserSession.ANONYMOUS_USER_ID);
        }

        String cleanUid = firebaseUid.trim();
        String cleanEmail = (email != null && !email.isBlank()) ? email.trim() : null;

        Object lock = syncLocks.computeIfAbsent(cleanUid, k -> new Object());
        synchronized (lock) {
            try {
                // 1. Check local cache
                for (User u : localUsers.values()) {
                    if (cleanUid.equals(u.getFirebaseUid())) {
                        return u;
                    }
                }

                // 2. Query D1 if configured and available
                if (d1Client != null && d1Client.isConfigured() && d1Client.isAvailable()) {
                    try {
                        // Check by firebase_uid
                        String sql = "SELECT id, firebase_uid, email, created_at FROM users WHERE firebase_uid = ? LIMIT 1";
                        List<Map<String, Object>> rows = d1Client.query(sql, List.of(cleanUid));
                        if (!rows.isEmpty()) {
                            User user = mapRowToUser(rows.get(0));
                            localUsers.put(user.getId(), user);
                            localUsers.put(cleanUid, user);
                            return user;
                        }

                        // Check legacy records where id was the firebase UID
                        sql = "SELECT id, firebase_uid, email, created_at FROM users WHERE id = ? LIMIT 1";
                        rows = d1Client.query(sql, List.of(cleanUid));
                        if (!rows.isEmpty()) {
                            User user = mapRowToUser(rows.get(0));
                            if (user.getFirebaseUid() == null || !cleanUid.equals(user.getFirebaseUid())) {
                                user.setFirebaseUid(cleanUid);
                                try {
                                    d1Client.execute("UPDATE users SET firebase_uid = ? WHERE id = ?", List.of(cleanUid, user.getId()));
                                } catch (Exception ignored) {}
                            }
                            localUsers.put(user.getId(), user);
                            localUsers.put(cleanUid, user);
                            return user;
                        }

                        // Check by email if present
                        if (cleanEmail != null) {
                            sql = "SELECT id, firebase_uid, email, created_at FROM users WHERE email = ? LIMIT 1";
                            rows = d1Client.query(sql, List.of(cleanEmail));
                            if (!rows.isEmpty()) {
                                User user = mapRowToUser(rows.get(0));
                                if (user.getFirebaseUid() == null || !cleanUid.equals(user.getFirebaseUid())) {
                                    user.setFirebaseUid(cleanUid);
                                    try {
                                        d1Client.execute("UPDATE users SET firebase_uid = ? WHERE id = ?", List.of(cleanUid, user.getId()));
                                    } catch (Exception ignored) {}
                                }
                                localUsers.put(user.getId(), user);
                                localUsers.put(cleanUid, user);
                                return user;
                            }
                        }

                        // User record does not exist in D1. Create and synchronize.
                        String dbId = UUID.randomUUID().toString();
                        String effectiveEmail = cleanEmail != null ? cleanEmail : (cleanUid + "@cachebrowse.local");
                        Instant now = Instant.now();

                        String insertSql = "INSERT INTO users (id, firebase_uid, email, created_at) VALUES (?, ?, ?, ?) "
                                + "ON CONFLICT(firebase_uid) DO UPDATE SET email = excluded.email";
                        d1Client.executeChecked(insertSql, List.of(dbId, cleanUid, effectiveEmail, now.toString()));

                        // Query the resolved row to guarantee primary key consistency under concurrent inserts
                        rows = d1Client.query("SELECT id, firebase_uid, email, created_at FROM users WHERE firebase_uid = ? LIMIT 1", List.of(cleanUid));
                        if (!rows.isEmpty()) {
                            User user = mapRowToUser(rows.get(0));
                            localUsers.put(user.getId(), user);
                            localUsers.put(cleanUid, user);
                            return user;
                        }

                        User newUser = new User(dbId, cleanUid, effectiveEmail, now);
                        localUsers.put(dbId, newUser);
                        localUsers.put(cleanUid, newUser);
                        return newUser;
                    } catch (Exception e) {
                        LOG.warn("Failed to synchronize user in D1 ({}): {}", cleanUid, e.getMessage());
                    }
                }

                // In-memory fallback
                User fallback = new User(cleanUid, cleanUid, cleanEmail != null ? cleanEmail : cleanUid + "@cachebrowse.local", Instant.now());
                localUsers.put(cleanUid, fallback);
                return fallback;
            } finally {
                syncLocks.remove(cleanUid);
            }
        }
    }

    private User mapRowToUser(Map<String, Object> row) {
        String id = String.valueOf(row.get("id"));
        Object fbUidObj = row.get("firebase_uid");
        String firebaseUid = fbUidObj != null && !String.valueOf(fbUidObj).isBlank() && !"null".equals(String.valueOf(fbUidObj))
                ? String.valueOf(fbUidObj) : id;
        String email = row.get("email") != null ? String.valueOf(row.get("email")) : "";
        Instant createdAt;
        try {
            createdAt = Instant.parse(String.valueOf(row.get("created_at")));
        } catch (Exception e) {
            createdAt = Instant.now();
        }
        return new User(id, firebaseUid, email, createdAt);
    }
}
