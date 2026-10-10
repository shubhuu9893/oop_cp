package com.cachebrowse.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads application configuration without logging values or depending on the launch directory. */
public final class EnvConfig {
    private static final Map<String, String> FILE_VALUES = loadProjectEnv();

    private EnvConfig() {}

    public static String get(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        if (value == null || value.isBlank()) value = FILE_VALUES.get(key);
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> loadProjectEnv() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                Path env = current.resolve(".env");
                Map<String, String> values = new HashMap<>();
                if (!Files.isRegularFile(env)) return values;
                try {
                    for (String raw : Files.readAllLines(env)) {
                        String line = raw.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int equals = line.indexOf('=');
                        if (equals <= 0) continue;
                        String key = line.substring(0, equals).trim();
                        String value = normalize(line.substring(equals + 1));
                        values.put(key, value);
                    }
                } catch (IOException ignored) {
                    // Missing/unreadable optional .env is handled as unconfigured.
                }
                return values;
            }
            current = current.getParent();
        }
        return Map.of();
    }

    private static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        } else if (value.startsWith("\"") || value.startsWith("'")) {
            value = value.substring(1).trim();
        } else if (value.endsWith("\"") || value.endsWith("'")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }
}
