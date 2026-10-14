package com.cachebrowse.adblock;

import java.util.Set;

/**
 * Tracker blocker that blocks URLs matching telemetry, analytics, and tracking domains.
 */
public class TrackerBlocker {
    private final BlockList blockList;
    private volatile boolean enabled = true;

    public TrackerBlocker() {
        Set<String> defaultTrackers = Set.of(
                "stats.google.com",
                "google-analytics.com",
                "*.google-analytics.com",
                "analytics.google.com",
                "hotjar.com",
                "*.hotjar.com",
                "pixel.facebook.com",
                "www.facebook.com/tr/",
                "connect.facebook.net",
                "tracker.yahoo.com",
                "segment.io",
                "*.segment.io",
                "segment.com",
                "*.segment.com",
                "mixpanel.com",
                "*.mixpanel.com",
                "scorecardresearch.com",
                "*.scorecardresearch.com",
                "quantserve.com",
                "*.quantserve.com",
                "telemetry.mozilla.org",
                "clarity.ms",
                "*.clarity.ms",
                "amplitude.com",
                "*.amplitude.com",
                "heapanalytics.com",
                "crazyegg.com",
                "mouseflow.com",
                "fullstory.com",
                "newrelic.com",
                "bam.nr-data.net",
                "sentry.io",
                "bugsnag.com",
                "branch.io",
                "appsflyer.com",
                "adjust.com",
                "kochava.com",
                "singular.net",
                "optimizely.com",
                "loggly.com",
                "matomo.cloud",
                "statcounter.com",
                "kissmetrics.io",
                "chartbeat.com",
                "*.chartbeat.com",
                "parsely.com",
                "snowplowanalytics.com",
                "/telemetry",
                "/analytics/tracker",
                "/ping?analytics"
        );
        this.blockList = new BlockList("data/tracker_rules.txt", defaultTrackers);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrackerBlocked(String url) {
        if (!enabled || url == null || url.isBlank()) {
            return false;
        }
        return blockList.matches(url);
    }

    public String findMatchingRule(String url) {
        if (!enabled || url == null || url.isBlank()) {
            return null;
        }
        return blockList.findMatchingRule(url);
    }

    public void addRule(String rule) {
        blockList.addRule(rule);
    }

    public void removeRule(String rule) {
        blockList.removeRule(rule);
    }

    public void clearRules() {
        blockList.clearRules();
    }

    public Set<String> getRules() {
        return blockList.getRules();
    }

    public int getRuleCount() {
        return blockList.size();
    }
}