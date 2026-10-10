package com.cachebrowse.database.repository;

import com.cachebrowse.database.D1Client;
import com.cachebrowse.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 implementation of UserRepository.
 */
public class D1UserRepository implements UserRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1UserRepository.class);

    private final D1Client d1Client;
    private final Map<String, User> localUsers = new ConcurrentHashMap<>();

    public D1UserRepository(D1Client d1Client) {
        this.d1Client = d1Client;
    }

    @Override
    public void createUser(User user) {
        if (user == null || user.getId() == null) return;
        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "INSERT INTO users (id, email, created_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET email = excluded.email";
                List<Object> params = List.of(
                        user.getId(),
                        user.getEmail() != null ? user.getEmail() : "",
                        user.getCreatedAt() != null ? user.getCreatedAt().toString() : Instant.now().toString()
                );
                d1Client.executeChecked(sql, params);
            } catch (Exception e) {
                LOG.warn("Failed to persist verified Firebase user to D1 ({})", e.getClass().getSimpleName());
                throw new IllegalStateException("Firebase user profile was not persisted to D1", e);
            }
        }
        localUsers.put(user.getId(), user);
    }

    @Override
    public Optional<User> getUserById(String id) {
        if (id == null) return Optional.empty();

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT id, email, created_at FROM users WHERE id = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(id));
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    Instant createdAt;
                    try {
                        createdAt = Instant.parse(String.valueOf(row.get("created_at")));
                    } catch (Exception e) {
                        createdAt = Instant.now();
                    }
                    User user = new User(String.valueOf(row.get("id")), String.valueOf(row.get("email")), createdAt);
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
    public Optional<User> getUserByEmail(String email) {
        if (email == null) return Optional.empty();

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT id, email, created_at FROM users WHERE email = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(email));
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    Instant createdAt;
                    try {
                        createdAt = Instant.parse(String.valueOf(row.get("created_at")));
                    } catch (Exception e) {
                        createdAt = Instant.now();
                    }
                    User user = new User(String.valueOf(row.get("id")), String.valueOf(row.get("email")), createdAt);
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
}
