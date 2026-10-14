package com.cachebrowse.model;

/**
 * Categorization of protection events.
 */
public enum ProtectionEventType {
    AD,
    TRACKER,
    ALLOWED,
    ERROR;

    public static ProtectionEventType fromString(String value) {
        if (value == null) return null;
        try {
            return ProtectionEventType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
