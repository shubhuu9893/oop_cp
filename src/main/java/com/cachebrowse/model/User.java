package com.cachebrowse.model;

import java.time.Instant;

/**
 * Model representing a user profile.
 */
public class User {
    private String id;
    private String email;
    private Instant createdAt;

    public User() {
        this.createdAt = Instant.now();
    }

    public User(String id, String email, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public User(String id, String email) {
        this(id, email, Instant.now());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
