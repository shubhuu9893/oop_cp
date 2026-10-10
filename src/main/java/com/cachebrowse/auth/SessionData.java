package com.cachebrowse.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing the persisted authentication session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionData {
    @JsonProperty("uid")
    private String uid;
    @JsonProperty("email")
    private String email;

    @JsonProperty("idToken")
    private String idToken;
    @JsonProperty("refreshToken")
    private String refreshToken;
    @JsonProperty("expiresAtMs")
    private long expiresAtMs;

    public SessionData() {
        // default constructor for Jackson
    }

    public SessionData(String uid, String email, String idToken, String refreshToken, long expiresAtMs) {
        this.uid = uid;
        this.email = email;
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.expiresAtMs = expiresAtMs;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getExpiresAtMs() { return expiresAtMs; }
    public void setExpiresAtMs(long expiresAtMs) { this.expiresAtMs = expiresAtMs; }
}