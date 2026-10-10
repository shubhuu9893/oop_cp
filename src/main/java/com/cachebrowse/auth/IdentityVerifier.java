package com.cachebrowse.auth;

import java.util.Optional;

/** Testable authentication boundary used by protected REST handlers. */
public interface IdentityVerifier {
    FirebaseIdentityService.Principal verify(String idToken) throws Exception;
    boolean isAdmin(FirebaseIdentityService.Principal principal);
    FirebaseIdentityService.UserPage listUsers(int pageSize, String pageToken) throws Exception;
    Optional<FirebaseIdentityService.DirectoryUser> getUser(String uid) throws Exception;
}
