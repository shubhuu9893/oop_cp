package com.cachebrowse.adblock;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container holding domain, host, and pattern blocking rules.
 * Supports file persistence and exact, wildcard, and substring matching.
 */
public class BlockList {
    private static final Logger LOG = LoggerFactory.getLogger(BlockList.class);
    private static final String SNAPSHOT_MARKER = "# CacheBrowse rule snapshot v1";

    private final Set<String> rules = ConcurrentHashMap.newKeySet();
    private final Path storagePath;

    public BlockList(String filePath, Set<String> defaults) {
        this.storagePath = filePath != null ? Path.of(filePath) : null;
        if (defaults != null) {
            rules.addAll(defaults);
        }
        loadFromFile();
    }

    public void addRule(String rule) {
        if (rule == null || rule.isBlank()) return;
        String clean = rule.trim().toLowerCase(java.util.Locale.ROOT);
        rules.add(clean);
        saveToFile();
    }

    public void removeRule(String rule) {
        if (rule == null) return;
        rules.remove(rule.trim().toLowerCase(java.util.Locale.ROOT));
        saveToFile();
    }

    public void clearRules() {
        rules.clear();
        saveToFile();
    }

    public Set<String> getRules() {
        return Collections.unmodifiableSet(rules);
    }

    public int size() {
        return rules.size();
    }

    /**
     * Finds and returns the specific matching rule from this block list, or null if no rule matches.
     */
    public String findMatchingRule(String url) {
        if (url == null || url.isBlank()) return null;
        String lowerUrl = url.toLowerCase();

        String host = null;
        try {
            URI uri = URI.create(lowerUrl);
            host = uri.getHost();
        } catch (Exception ignored) {
        }

        // If URI.create failed to parse host, try simple extraction
        if (host == null) {
            host = extractHostFallback(lowerUrl);
        }

        for (String rule : rules) {
            if (rule.isBlank() || rule.startsWith("#")) continue;

            // Wildcard domain match: e.g. *.doubleclick.net
            if (rule.startsWith("*.")) {
                String domain = rule.substring(2);
                if (host != null && (host.equals(domain) || host.endsWith("." + domain))) {
                    return rule;
                }
            } else if (rule.startsWith("*")) {
                String suffix = rule.substring(1);
                if (host != null && host.endsWith(suffix)) {
                    return rule;
                }
            } else if (host != null && (host.equalsIgnoreCase(rule) || host.endsWith("." + rule))) {
                return rule;
            } else if (lowerUrl.contains(rule)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Checks if the given URL matches any rule in this block list.
     */
    public boolean matches(String url) {
        return findMatchingRule(url) != null;
    }

    private String extractHostFallback(String url) {
        try {
            int start = url.indexOf("://");
            if (start != -1) {
                start += 3;
            } else {
                start = 0;
            }
            int end = url.indexOf('/', start);
            int colon = url.indexOf(':', start);
            if (colon != -1 && (end == -1 || colon < end)) {
                end = colon;
            }
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private void loadFromFile() {
        if (storagePath == null) return;
        try {
            if (Files.exists(storagePath)) {
                var lines = Files.readAllLines(storagePath);
                if (!lines.isEmpty() && SNAPSHOT_MARKER.equals(lines.get(0).trim())) rules.clear();
                Set<String> loaded = lines.stream()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .map(line -> line.toLowerCase(java.util.Locale.ROOT))
                        .collect(Collectors.toSet());
                rules.addAll(loaded);
            }
        } catch (IOException e) {
            LOG.debug("Could not read blocklist file {}: {}", storagePath, e.getMessage());
        }
    }

    private void saveToFile() {
        if (storagePath == null) return;
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            var snapshot = new java.util.ArrayList<String>();
            snapshot.add(SNAPSHOT_MARKER);
            snapshot.addAll(rules.stream().sorted().collect(Collectors.toList()));
            Files.write(storagePath, snapshot);
        } catch (IOException e) {
            LOG.debug("Could not write blocklist file {}: {}", storagePath, e.getMessage());
        }
    }
}
