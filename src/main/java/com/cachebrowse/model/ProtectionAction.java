package com.cachebrowse.model;

/**
 * Outcome/action of a protection decision.
 */
public enum ProtectionAction {
    BLOCKED,
    ALLOWED,
    FAILED;

    public static ProtectionAction fromString(String value) {
        if (value == null) return null;
        try {
            return ProtectionAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
