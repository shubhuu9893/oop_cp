package com.cachebrowse.adblock;

import java.io.BufferedReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Very simple ad blocker – holds a set of domain patterns.
 * <p>
 * The blocklist is read from {@code data/adblock_list.txt} if it exists, otherwise
 * the blocker falls back to an internal default list.  Each line in the file can be
 * either a full host (e.g. "ads.google.com") or a wildcard prefixed with '*'.
 */
public class AdBlocker {
    private final Set<String> blocklist = new HashSet<>();

    public AdBlocker() {
        loadDefaultList();
    }

    /** Load the list from a file or use built‑in defaults */
    private void loadDefaultList() {
        try {
            Path path = Path.of("data", "adblock_list.txt");
            if (Files.exists(path)) {
                blocklist.addAll(Files.readAllLines(path)
                        .stream()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .collect(Collectors.toSet()));
            }
        } catch (Exception ignored) {
            // ignore, use defaults
        }

        // Hardcoded fallback list – keeps the demo functional.
        blocklist.addAll(Set.of(
                "ads.google.com",
                "doubleclick.net",
                "googlesyndication.com",
                "adservice.google.com"
        ));
    }

    /** Return true if the given URL matches a blocked domain */
    public boolean isAdBlocked(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            for (String pattern : blocklist) {
                if (pattern.startsWith("*")) {
                    // wildcard at the start: ends with
                    String suffix = pattern.substring(1);
                    if (host.endsWith(suffix)) return true;
                } else {
                    if (host.equalsIgnoreCase(pattern)) return true;
                }
            }
        } catch (Exception e) {
            // Malformed URL – treat as not blocked.
        }
        return false;
    }
}