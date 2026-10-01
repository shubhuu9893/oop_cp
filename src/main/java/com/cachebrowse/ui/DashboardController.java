package com.cachebrowse.ui;

import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.statistics.CacheStatistics;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DashboardController {

    private final CacheStatistics statistics;
    private final CacheManager cacheManager;

    public DashboardController(
            CacheStatistics statistics,
            CacheManager cacheManager) {

        this.statistics = statistics;
        this.cacheManager = cacheManager;
    }

    public void show() {

        Stage stage = new Stage();

        stage.setTitle(
                "CacheBrowse Performance Dashboard"
        );

        VBox root = new VBox(15);

        root.setPadding(
                new Insets(20)
        );

        Label title = new Label(
                "CacheBrowse Performance Dashboard"
        );

        title.setStyle(
                "-fx-font-size: 24px;" +
                "-fx-font-weight: bold;"
        );

        GridPane grid = new GridPane();

        grid.setHgap(30);
        grid.setVgap(15);

        addRow(
                grid,
                0,
                "Total Requests",
                String.valueOf(
                        statistics.getTotalRequests()
                )
        );

        addRow(
                grid,
                1,
                "Cache Hits",
                String.valueOf(
                        statistics.getCacheHits()
                )
        );

        addRow(
                grid,
                2,
                "Cache Misses",
                String.valueOf(
                        statistics.getCacheMisses()
                )
        );

        addRow(
                grid,
                3,
                "Cache Hit Ratio",
                String.format(
                        "%.2f%%",
                        statistics.getHitRatio()
                )
        );

        addRow(
                grid,
                4,
                "Ads Blocked",
                String.valueOf(
                        statistics.getAdsBlocked()
                )
        );

        addRow(
                grid,
                5,
                "Trackers Blocked",
                String.valueOf(
                        statistics.getTrackersBlocked()
                )
        );

        addRow(
                grid,
                6,
                "Average Response Time",
                String.format(
                        "%.2f ms",
                        statistics.getAverageResponseTime()
                )
        );

        addRow(
                grid,
                7,
                "Cache Entries",
                cacheManager.size()
                        + " / "
                        + cacheManager.capacity()
        );

        addRow(
                grid,
                8,
                "LRU Evictions",
                String.valueOf(
                        cacheManager.getEvictions()
                )
        );

        root.getChildren().addAll(
                title,
                grid
        );

        Scene scene = new Scene(
                root,
                550,
                500
        );

        stage.setScene(scene);
        stage.show();
    }

    private void addRow(
            GridPane grid,
            int row,
            String name,
            String value) {

        Label nameLabel =
                new Label(name);

        Label valueLabel =
                new Label(value);

        nameLabel.setStyle(
                "-fx-font-weight: bold;"
        );

        grid.add(
                nameLabel,
                0,
                row
        );

        grid.add(
                valueLabel,
                1,
                row
        );
    }
}