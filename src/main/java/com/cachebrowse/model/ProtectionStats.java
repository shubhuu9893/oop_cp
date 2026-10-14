package com.cachebrowse.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtectionStats {
    private long totalAdsBlocked;
    private long totalTrackersBlocked;
    private long totalBlocked;
    private long totalAllowed;
    private long totalErrors;

    private String sessionId;
    private long sessionAdsBlocked;
    private long sessionTrackersBlocked;
    private long sessionBlocked;
    private long sessionAllowed;

    private String tabId;
    private long tabAdsBlocked;
    private long tabTrackersBlocked;
    private long tabBlocked;
    private long tabAllowed;

    private boolean diagnosticLogging;

    public ProtectionStats() {}

    public long getTotalAdsBlocked() { return totalAdsBlocked; }
    public void setTotalAdsBlocked(long totalAdsBlocked) { this.totalAdsBlocked = totalAdsBlocked; }

    public long getTotalTrackersBlocked() { return totalTrackersBlocked; }
    public void setTotalTrackersBlocked(long totalTrackersBlocked) { this.totalTrackersBlocked = totalTrackersBlocked; }

    public long getTotalBlocked() { return totalBlocked; }
    public void setTotalBlocked(long totalBlocked) { this.totalBlocked = totalBlocked; }

    public long getTotalAllowed() { return totalAllowed; }
    public void setTotalAllowed(long totalAllowed) { this.totalAllowed = totalAllowed; }

    public long getTotalErrors() { return totalErrors; }
    public void setTotalErrors(long totalErrors) { this.totalErrors = totalErrors; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public long getSessionAdsBlocked() { return sessionAdsBlocked; }
    public void setSessionAdsBlocked(long sessionAdsBlocked) { this.sessionAdsBlocked = sessionAdsBlocked; }

    public long getSessionTrackersBlocked() { return sessionTrackersBlocked; }
    public void setSessionTrackersBlocked(long sessionTrackersBlocked) { this.sessionTrackersBlocked = sessionTrackersBlocked; }

    public long getSessionBlocked() { return sessionBlocked; }
    public void setSessionBlocked(long sessionBlocked) { this.sessionBlocked = sessionBlocked; }

    public long getSessionAllowed() { return sessionAllowed; }
    public void setSessionAllowed(long sessionAllowed) { this.sessionAllowed = sessionAllowed; }

    public String getTabId() { return tabId; }
    public void setTabId(String tabId) { this.tabId = tabId; }

    public long getTabAdsBlocked() { return tabAdsBlocked; }
    public void setTabAdsBlocked(long tabAdsBlocked) { this.tabAdsBlocked = tabAdsBlocked; }

    public long getTabTrackersBlocked() { return tabTrackersBlocked; }
    public void setTabTrackersBlocked(long tabTrackersBlocked) { this.tabTrackersBlocked = tabTrackersBlocked; }

    public long getTabBlocked() { return tabBlocked; }
    public void setTabBlocked(long tabBlocked) { this.tabBlocked = tabBlocked; }

    public long getTabAllowed() { return tabAllowed; }
    public void setTabAllowed(long tabAllowed) { this.tabAllowed = tabAllowed; }

    public boolean isDiagnosticLogging() { return diagnosticLogging; }
    public void setDiagnosticLogging(boolean diagnosticLogging) { this.diagnosticLogging = diagnosticLogging; }
}
