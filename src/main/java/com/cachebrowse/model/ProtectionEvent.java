package com.cachebrowse.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured model representing a single real ad-blocking, tracker-blocking,
 * or allowed network protection event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtectionEvent {
    private String eventId;
    private String timestamp;
    private String sessionId;
    private String tabId;
    private String pageUrl;
    private String pageHostname;
    private String resourceUrl;
    private String resourceHostname;
    private ProtectionEventType eventType;
    private ProtectionAction action;
    private String rule;
    private String reason;
    private String resourceType;

    public ProtectionEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toString();
    }

    public ProtectionEvent(String eventId, String timestamp, String sessionId, String tabId,
                           String pageUrl, String pageHostname, String resourceUrl,
                           String resourceHostname, ProtectionEventType eventType,
                           ProtectionAction action, String rule, String reason,
                           String resourceType) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.timestamp = timestamp != null ? timestamp : Instant.now().toString();
        this.sessionId = sessionId;
        this.tabId = tabId;
        this.pageUrl = pageUrl;
        this.pageHostname = pageHostname;
        this.resourceUrl = resourceUrl;
        this.resourceHostname = resourceHostname;
        this.eventType = eventType;
        this.action = action;
        this.rule = rule;
        this.reason = reason;
        this.resourceType = resourceType;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTabId() { return tabId; }
    public void setTabId(String tabId) { this.tabId = tabId; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getPageHostname() { return pageHostname; }
    public void setPageHostname(String pageHostname) { this.pageHostname = pageHostname; }

    public String getResourceUrl() { return resourceUrl; }
    public void setResourceUrl(String resourceUrl) { this.resourceUrl = resourceUrl; }

    public String getResourceHostname() { return resourceHostname; }
    public void setResourceHostname(String resourceHostname) { this.resourceHostname = resourceHostname; }

    public ProtectionEventType getEventType() { return eventType; }
    public void setEventType(ProtectionEventType eventType) { this.eventType = eventType; }

    public ProtectionAction getAction() { return action; }
    public void setAction(ProtectionAction action) { this.action = action; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProtectionEvent that = (ProtectionEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "ProtectionEvent{" +
                "eventId='" + eventId + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", tabId='" + tabId + '\'' +
                ", resourceHostname='" + resourceHostname + '\'' +
                ", eventType=" + eventType +
                ", action=" + action +
                ", rule='" + rule + '\'' +
                '}';
    }
}
