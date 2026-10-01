package com.cachebrowse.adblock;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple tracker blocker – blocks requests that target known tracking domains.
 */
public class TrackerBlocker {
    private final Set<String> trackerList = new HashSet<>();

    public TrackerBlocker() {
        loadDefaultList();
    }

    private void loadDefaultList() {
        try {
            Path path = Path.of("data", "tracker_list.txt");
            if (Files.exists(path)) {
                trackerList.addAll(Files.readAllLines(path)
                        .stream()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .collect(Collectors.toSet()));
            }
        } catch (Exception ignored) {
            // ignore, use defaults
        }

        trackerList.addAll(Set.of(
                "stats.google.com",
                "www.facebook.com/tr/",
                "tracker.yahoo.com"
        ));
    }

    public boolean isTrackerBlocked(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            for (String pattern : trackerList) {
                if (pattern.startsWith("*")) {
                    if (host.endsWith(pattern.substring(1))) return true;
                } else {
                    if (host.contains(pattern.toLowerCase())) return true;
                }
            }
        } catch (Exception e) {
            // malformed URL
        }
        return false;
    }
}