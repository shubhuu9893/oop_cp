package com.cachebrowse.model;

import java.time.Duration;

/**
 * Holds user-configurable settings for the browser.
 */
public class BrowserSettings {
    private String defaultSearchEngine = "https://www.google.com/search?q=%s";
    private String homePage = "https://www.homepage.com";
    private boolean cacheEnabled = true;
    private Duration cacheTTL = Duration.ofMinutes(5);
    private int cacheCapacity = 100;
    private boolean adBlockingEnabled = true;
    private boolean trackerBlockingEnabled = true;
    // ---------------------------------------------------------------------
    // Basic getters – the UI and proxy layer read these values. Setters are
    // intentionally simple; persistence is handled elsewhere (e.g., SettingsManager).
    // ---------------------------------------------------------------------

    public String getDefaultSearchEngine() {
        return defaultSearchEngine;
    }

    public void setDefaultSearchEngine(String engine) {
        if (engine != null && !engine.isBlank()) this.defaultSearchEngine = engine.trim();
    }

    public String getHomePage() {
        return homePage;
    }

    public void setHomePage(String url) {
        if (url != null && !url.isBlank()) this.homePage = url.trim();
    }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean enabled) { this.cacheEnabled = enabled; }

    public Duration getCacheTTL() { return cacheTTL; }
    public void setCacheTTL(Duration ttl) { if (ttl != null && !ttl.isNegative()) this.cacheTTL = ttl; }

    /**
     * Convenience getter returning TTL in milliseconds.
     */
    public long getCacheTtl() {
        return cacheTTL.toMillis();
    }

    /**
     * Convenience setter accepting TTL in milliseconds.
     */
    public void setCacheTtl(long millis) {
        if (millis > 0) this.cacheTTL = Duration.ofMillis(millis);
    }

    public int getCacheCapacity() { return cacheCapacity; }
    public void setCacheCapacity(int capacity) { if (capacity > 0) this.cacheCapacity = capacity; }

    public boolean isAdBlockingEnabled() { return adBlockingEnabled; }
    public void setAdBlockingEnabled(boolean enabled) { this.adBlockingEnabled = enabled; }

    public boolean isTrackerBlockingEnabled() { return trackerBlockingEnabled; }
    public void setTrackerBlockingEnabled(boolean enabled) { this.trackerBlockingEnabled = enabled; }
}