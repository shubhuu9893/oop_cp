package com.cachebrowse.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseIdentityServiceTest {
    @Test
    void administratorIsAssignedOnlyByConfiguredVerifiedUid() {
        FirebaseIdentityService service = new FirebaseIdentityService(null, "uid-admin");
        var admin = new FirebaseIdentityService.Principal("uid-admin", "admin@example.test", "Admin", true, "password");
        var ordinary = new FirebaseIdentityService.Principal("uid-user", "admin@example.test", "Admin", true, "password");
        assertTrue(service.isAdmin(admin));
        assertFalse(service.isAdmin(ordinary));
        assertFalse(new FirebaseIdentityService(null, "").isAdmin(admin));
    }
}
