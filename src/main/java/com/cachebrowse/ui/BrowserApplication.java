package com.cachebrowse.ui;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.history.HistoryManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.statistics.CacheStatistics;

import javafx.application.Application;
import javafx.scene.Scene;
import com.cachebrowse.bookmark.BookmarkManager;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class BrowserApplication extends Application {

    @Override
    public void start(Stage stage) {

        // Application settings
        BrowserSettings settings = new BrowserSettings();

        CacheStatistics statistics = new CacheStatistics();
        // Cache – pass statistics for tracking.
        CacheManager cacheManager =
                new CacheManager(settings.getCacheCapacity(), statistics);


        // History
        HistoryManager historyManager =
                new HistoryManager();

        // Ad and tracker blocking
        AdBlocker adBlocker =
                new AdBlocker();

        TrackerBlocker trackerBlocker =
                new TrackerBlocker();

        // Bookmark manager
        BookmarkManager bookmarkManager = new BookmarkManager();

        // Browser controller
        BrowserController controller =
                new BrowserController(
                        stage,
                        cacheManager,
                        statistics,
                        settings,
                        historyManager,
                        bookmarkManager,
                        adBlocker,
                        trackerBlocker
                );

        // Root layout
        BorderPane root = new BorderPane();

        root.setCenter(controller.createView());

        // Scene
        Scene scene =
                new Scene(root, 1400, 850);

        // Load CSS
        var css =
                getClass().getResource("/styles.css");

        if (css != null) {
            scene.getStylesheets()
                    .add(css.toExternalForm());
        }

        // Window
        stage.setTitle(
                "CacheBrowse - Smart Caching Browser"
        );

        stage.setMinWidth(1000);
        stage.setMinHeight(650);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}