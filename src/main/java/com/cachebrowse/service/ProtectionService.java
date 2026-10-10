package com.cachebrowse.service;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.statistics.CacheStatistics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/** Owns user allowlist rules and reports real block events received from Chromium. */
public final class ProtectionService {
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;
    private final CacheStatistics statistics;
    private final Set<String> allowlist = new ConcurrentSkipListSet<>();
    private final Path allowlistFile;

    public ProtectionService(AdBlocker adBlocker, TrackerBlocker trackerBlocker,
                             CacheStatistics statistics) {
        this(adBlocker, trackerBlocker, statistics, Path.of("data", "protection_allowlist.txt"));
    }

    public ProtectionService(AdBlocker adBlocker, TrackerBlocker trackerBlocker,
                             CacheStatistics statistics, Path allowlistFile) {
        this.adBlocker = adBlocker;
        this.trackerBlocker = trackerBlocker;
        this.statistics = statistics;
        this.allowlistFile = allowlistFile;
        loadAllowlist();
    }

    public boolean isAllowed(String url) {
        if (url == null) return false;
        String host = hostOf(url);
        return allowlist.stream().anyMatch(rule -> matches(rule, url, host));
    }

    public void addAllowlistRule(String rule) {
        String clean = cleanDomain(rule);
        if (clean == null) throw new IllegalArgumentException("A valid domain is required");
        allowlist.add(clean);
        saveAllowlist();
    }

    public void removeAllowlistRule(String rule) {
        if (rule == null) return;
        allowlist.remove(rule.trim().toLowerCase(java.util.Locale.ROOT));
        saveAllowlist();
    }

    public void addBlocklistRule(String category, String rule) {
        String clean = cleanRule(rule);
        if (clean == null) throw new IllegalArgumentException("A valid domain or URL pattern is required");
        switch (category == null ? "" : category.toLowerCase(java.util.Locale.ROOT)) {
            case "ad", "ads" -> adBlocker.addRule(clean);
            case "tracker", "trackers" -> trackerBlocker.addRule(clean);
            default -> throw new IllegalArgumentException("category must be 'ad' or 'tracker'");
        }
    }

    public void removeBlocklistRule(String category, String rule) {
        if (rule == null) return;
        switch (category == null ? "" : category.toLowerCase(java.util.Locale.ROOT)) {
            case "ad", "ads" -> adBlocker.removeRule(rule);
            case "tracker", "trackers" -> trackerBlocker.removeRule(rule);
            default -> throw new IllegalArgumentException("category must be 'ad' or 'tracker'");
        }
    }

    public List<String> getAllowlist() { return List.copyOf(allowlist); }
    public List<String> getAdRules() { return adBlocker.getRules().stream().sorted().toList(); }
    public List<String> getTrackerRules() { return trackerBlocker.getRules().stream().sorted().toList(); }

    /** Called only after Electron cancels an actual Chromium request. */
    public void recordBlockedRequest(String category) {
        if (statistics == null) return;
        if ("ad".equalsIgnoreCase(category)) statistics.incAdsBlocked();
        else if ("tracker".equalsIgnoreCase(category)) statistics.incTrackersBlocked();
        else throw new IllegalArgumentException("category must be 'ad' or 'tracker'");
    }

    private static String cleanRule(String rule) {
        if (rule == null || rule.isBlank()) return null;
        String clean = rule.trim().toLowerCase(java.util.Locale.ROOT);
        if (clean.length() > 2048 || clean.chars().anyMatch(Character::isISOControl)) return null;
        return clean;
    }

    private static String cleanDomain(String value) {
        String clean = cleanRule(value);
        if (clean == null) return null;
        String domain = clean.startsWith("*.") ? clean.substring(2) : clean;
        if (domain.length() > 253 || !domain.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) return null;
        for (String label : domain.split("\\.")) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return null;
        }
        return clean;
    }

    private void loadAllowlist() {
        if (!Files.exists(allowlistFile)) return;
        try {
            Files.readAllLines(allowlistFile).stream().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(java.util.Locale.ROOT)).forEach(allowlist::add);
        } catch (IOException ignored) {
            // Browsing remains available when local preference storage is read-only.
        }
    }

    private void saveAllowlist() {
        try {
            if (allowlistFile.getParent() != null) Files.createDirectories(allowlistFile.getParent());
            Files.write(allowlistFile, allowlist.stream().sorted().collect(Collectors.toList()));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not persist the protection allowlist", ex);
        }
    }

    private static String hostOf(String value) {
        try { return java.net.URI.create(value).getHost(); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static boolean matches(String rule, String url, String host) {
        if (rule.startsWith("*.")) {
            String domain = rule.substring(2);
            return host != null && (host.equals(domain) || host.endsWith("." + domain));
        }
        if (rule.startsWith("*")) return host != null && host.endsWith(rule.substring(1));
        return (host != null && (host.equals(rule) || host.endsWith("." + rule)))
                || url.toLowerCase(java.util.Locale.ROOT).contains(rule);
    }
}
