package com.cachebrowse.database.repository;

import com.cachebrowse.model.User;
import java.util.Optional;

/**
 * Repository interface for managing user data.
 */
public interface UserRepository {
    void createUser(User user);
    Optional<User> getUserById(String id);
    Optional<User> getUserByEmail(String email);
}
