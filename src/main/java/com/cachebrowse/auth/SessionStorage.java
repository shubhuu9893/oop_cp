package com.cachebrowse.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persistently store a {@link SessionData} instance in the user's home
 * directory.  The file name is {@code .cachebrowse.session}.  This class does not
 * depend on any other project components, making it easy to test.
 */
public final class SessionStorage {
    private static final Path SESSION_FILE = java.nio.file.Paths.get(System.getProperty("user.home"), ".cachebrowse.session");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionStorage() {}

    public static Optional<SessionData> load() {
        if (!Files.exists(SESSION_FILE)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(SESSION_FILE);
            SessionData data = MAPPER.readValue(bytes, SessionData.class);
            return Optional.ofNullable(data);
        } catch (IOException e) {
            System.err.println("Failed to read session file: " + e.getMessage());
            return Optional.empty();
        }
    }

    public static void save(SessionData data) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(data);
            Files.createDirectories(SESSION_FILE.getParent());
            Files.write(SESSION_FILE, bytes);
        } catch (IOException e) {
            System.err.println("Failed to write session file: " + e.getMessage());
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException ignored) {}
    }
}