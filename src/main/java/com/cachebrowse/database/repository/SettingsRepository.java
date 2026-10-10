package com.cachebrowse.database.repository;

import com.cachebrowse.model.BrowserSettings;
import java.util.Optional;

/**
 * Repository interface for browser configuration persistence.
 */
public interface SettingsRepository {
    void saveSettings(BrowserSettings settings);
    Optional<BrowserSettings> getSettings(String userId);
}
