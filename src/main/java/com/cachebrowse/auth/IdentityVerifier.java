package com.cachebrowse.auth;

/** Testable authentication boundary used by protected REST handlers. */
public interface IdentityVerifier {
    FirebaseIdentityService.Principal verify(String idToken) throws Exception;
}
