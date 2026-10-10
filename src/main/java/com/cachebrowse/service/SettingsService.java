package com.cachebrowse.service;

import com.cachebrowse.database.repository.SettingsRepository;
import com.cachebrowse.model.BrowserSettings;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing browser configuration persistence and runtime updates.
 */
public class SettingsService {
    private final SettingsRepository settingsRepository;
    private final BrowserSettings currentSettings;
    private final CompletableFuture<Void> initialLoad;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SettingsService-Worker");
        t.setDaemon(true);
        return t;
    });

    public SettingsService(SettingsRepository settingsRepository, BrowserSettings initialSettings) {
        this.settingsRepository = settingsRepository;
        this.currentSettings = initialSettings != null ? initialSettings : new BrowserSettings();
        this.initialLoad = loadSettingsFromDb(this.currentSettings.getUserId());
    }

    public BrowserSettings getSettings() {
        return currentSettings;
    }

    public CompletableFuture<Void> ready() { return initialLoad; }

    public CompletableFuture<Void> loadSettingsFromDb(String userId) {
        if (settingsRepository == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            Optional<BrowserSettings> opt = settingsRepository.getSettings(userId);
            opt.ifPresent(loaded -> {
                currentSettings.setCacheEnabled(loaded.isCacheEnabled());
                currentSettings.setCacheCapacity(loaded.getCacheCapacity());
                currentSettings.setCacheTtl(loaded.getCacheTtl());
                currentSettings.setAdBlockingEnabled(loaded.isAdBlockingEnabled());
                currentSettings.setTrackerBlockingEnabled(loaded.isTrackerBlockingEnabled());
                currentSettings.setHomePage(loaded.getHomePage());
                currentSettings.setDefaultSearchEngine(loaded.getDefaultSearchEngine());
            });
        }, executor);
    }

    public void saveSettings(BrowserSettings newSettings) {
        if (newSettings == null) return;
        currentSettings.setCacheEnabled(newSettings.isCacheEnabled());
        currentSettings.setCacheCapacity(newSettings.getCacheCapacity());
        currentSettings.setCacheTtl(newSettings.getCacheTtl());
        currentSettings.setAdBlockingEnabled(newSettings.isAdBlockingEnabled());
        currentSettings.setTrackerBlockingEnabled(newSettings.isTrackerBlockingEnabled());
        currentSettings.setHomePage(newSettings.getHomePage());
        currentSettings.setDefaultSearchEngine(newSettings.getDefaultSearchEngine());

        try {
            executor.submit(() -> {
            if (settingsRepository != null) {
                settingsRepository.saveSettings(currentSettings);
            }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Settings persistence was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Settings persistence failed", cause);
        }
    }

    /** Persists a user's own settings without replacing the process-wide active settings object. */
    public void saveSettingsForUser(String userId, BrowserSettings userSettings) {
        if (settingsRepository == null) throw new IllegalStateException("Settings storage is unavailable");
        if (userId == null || userId.isBlank() || userSettings == null)
            throw new IllegalArgumentException("Verified user and settings are required");
        BrowserSettings scoped = new BrowserSettings(userId);
        scoped.setCacheEnabled(userSettings.isCacheEnabled());
        scoped.setCacheCapacity(userSettings.getCacheCapacity());
        scoped.setCacheTtl(userSettings.getCacheTtl());
        scoped.setAdBlockingEnabled(userSettings.isAdBlockingEnabled());
        scoped.setTrackerBlockingEnabled(userSettings.isTrackerBlockingEnabled());
        scoped.setHomePage(userSettings.getHomePage());
        scoped.setDefaultSearchEngine(userSettings.getDefaultSearchEngine());
        try {
            executor.submit(() -> settingsRepository.saveSettings(scoped)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Settings persistence was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Settings persistence failed", cause);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
