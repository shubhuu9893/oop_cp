package com.cachebrowse.model;

import java.time.Instant;

/**
 * Model representing an application user profile, mapped to the underlying database record.
 */
public class User {
    private String id;
    private String firebaseUid;
    private String email;
    private Instant createdAt;

    public User() {
        this.createdAt = Instant.now();
    }

    public User(String id, String email, Instant createdAt) {
        this(id, id, email, createdAt);
    }

    public User(String id, String email) {
        this(id, id, email, Instant.now());
    }

    public User(String id, String firebaseUid, String email, Instant createdAt) {
        this.id = id;
        this.firebaseUid = firebaseUid != null ? firebaseUid : id;
        this.email = email;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
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
