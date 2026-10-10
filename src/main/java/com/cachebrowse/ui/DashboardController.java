package com.cachebrowse.ui;

import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.statistics.CacheStatistics;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Performance Dashboard displaying real-time metrics and cache controls.
 * UI redesigned with modern card-based layout. All data logic is preserved.
 */
public class DashboardController {

    private final CacheStatistics statistics;
    private final CacheManager cacheManager;

    // Metric value labels
    private Label totalRequestsVal;
    private Label allowedRequestsVal;
    private Label blockedRequestsVal;
    private Label cacheHitsVal;
    private Label cacheMissesVal;
    private Label hitRatioVal;
    private Label networkRequestsVal;
    private Label adsBlockedVal;
    private Label trackersBlockedVal;
    private Label avgResponseTimeVal;
    private Label cacheEntriesVal;
    private Label lruEvictionsVal;
    private Label expiredEntriesVal;
    private Label bytesFromCacheVal;
    private Label bytesFromNetworkVal;

    public DashboardController(CacheStatistics statistics, CacheManager cacheManager) {
        this.statistics = statistics;
        this.cacheManager = cacheManager;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Dashboard – CacheBrowse");

        // Root scroll-style container
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0F172A;");

        // Header
        HBox header = new HBox();
        header.setStyle(
            "-fx-background-color: #111827;" +
            "-fx-padding: 20 24 20 24;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 0 0 1 0;"
        );
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);

        Label dashIcon = new Label("⬡");
        dashIcon.setStyle("-fx-font-size: 20px; -fx-text-fill: #3B82F6;");

        VBox titleBlock = new VBox(2);
        Label titleLbl = new Label("Performance Dashboard");
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC;");
        Label subLbl = new Label("Real-time browser metrics and cache analytics");
        subLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        titleBlock.getChildren().addAll(titleLbl, subLbl);

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Button refreshBtn = toolbarButton("↻ Refresh", "#243047", "#94A3B8");
        refreshBtn.setOnAction(e -> refreshData());

        Button clearCacheBtn = toolbarButton("Clear Cache", "#7F1D1D", "#FCA5A5");
        clearCacheBtn.setOnAction(e -> {
            if (cacheManager != null) cacheManager.clear();
            refreshData();
        });

        Button closeBtn = toolbarButton("✕ Close", "#243047", "#94A3B8");
        closeBtn.setOnAction(e -> stage.close());

        header.getChildren().addAll(dashIcon, titleBlock, hSpacer, refreshBtn, clearCacheBtn, closeBtn);

        // Body
        VBox body = new VBox(24);
        body.setPadding(new Insets(24));
        body.setStyle("-fx-background-color: #0F172A;");

        // ── Row 1: Big summary cards ────────────────────────────────────────
        hitRatioVal       = new Label("0.0%");
        adsBlockedVal     = new Label("0");
        trackersBlockedVal = new Label("0");
        cacheHitsVal      = new Label("0");

        HBox summaryRow = new HBox(16);
        summaryRow.getChildren().addAll(
            bigCard("Cache Hit Rate", hitRatioVal,  "⚡", "#3B82F6"),
            bigCard("Ads Blocked",    adsBlockedVal, "🛡", "#06B6D4"),
            bigCard("Trackers Blocked", trackersBlockedVal, "🔒", "#8B5CF6"),
            bigCard("Cache Hits",     cacheHitsVal,  "✓",  "#22C55E")
        );
        for (var node : summaryRow.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        // ── Section: Requests ───────────────────────────────────────────────
        totalRequestsVal    = new Label("0");
        allowedRequestsVal  = new Label("0");
        blockedRequestsVal  = new Label("0");
        networkRequestsVal  = new Label("0");
        avgResponseTimeVal  = new Label("0 ms");

        VBox requestsCard = sectionCard("Network & Requests", new String[][]{
            {"Total Requests",    null},
            {"Allowed",          null},
            {"Blocked",          null},
            {"Network Requests", null},
            {"Avg Response Time",null}
        }, new Label[]{
            totalRequestsVal, allowedRequestsVal, blockedRequestsVal,
            networkRequestsVal, avgResponseTimeVal
        }, "#3B82F6");

        // ── Section: Cache ──────────────────────────────────────────────────
        cacheMissesVal     = new Label("0");
        cacheEntriesVal    = new Label("0");
        lruEvictionsVal    = new Label("0");
        expiredEntriesVal  = new Label("0");
        bytesFromCacheVal  = new Label("0 B");
        bytesFromNetworkVal = new Label("0 B");

        VBox cacheCard = sectionCard("Cache Details", new String[][]{
            {"Cache Misses",   null},
            {"Cache Entries",  null},
            {"LRU Evictions",  null},
            {"Expired Entries",null},
            {"Saved from Cache",null},
            {"Loaded from Network",null}
        }, new Label[]{
            cacheMissesVal, cacheEntriesVal, lruEvictionsVal,
            expiredEntriesVal, bytesFromCacheVal, bytesFromNetworkVal
        }, "#06B6D4");

        HBox detailRow = new HBox(16);
        HBox.setHgrow(requestsCard, Priority.ALWAYS);
        HBox.setHgrow(cacheCard,    Priority.ALWAYS);
        detailRow.getChildren().addAll(requestsCard, cacheCard);

        body.getChildren().addAll(summaryRow, detailRow);

        refreshData();

        root.getChildren().addAll(header, body);

        Scene scene = new Scene(root, 760, 560);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    // ── Card builders ────────────────────────────────────────────────────────

    private VBox bigCard(String title, Label valueLabel, String icon, String accentColor) {
        VBox card = new VBox(8);
        card.setStyle(
            "-fx-background-color: #1E293B;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + accentColor + "33;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 20 20 20 20;"
        );
        card.setAlignment(Pos.TOP_LEFT);

        Label iconLbl = new Label(icon);
        iconLbl.setStyle(
            "-fx-font-size: 22px;" +
            "-fx-text-fill: " + accentColor + ";" +
            "-fx-background-color: " + accentColor + "22;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 6 8 6 8;"
        );

        Label titleLbl = new Label(title);
        titleLbl.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: #94A3B8;" +
            "-fx-font-weight: 500;"
        );

        valueLabel.setStyle(
            "-fx-font-size: 28px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + accentColor + ";"
        );

        card.getChildren().addAll(iconLbl, titleLbl, valueLabel);
        return card;
    }

    private VBox sectionCard(String sectionTitle, String[][] rows, Label[] valueLabels, String accentColor) {
        VBox card = new VBox(0);
        card.setStyle(
            "-fx-background-color: #1E293B;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        );

        // Card header
        HBox cardHeader = new HBox();
        cardHeader.setStyle(
            "-fx-padding: 14 16 14 16;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 0 0 1 0;"
        );
        cardHeader.setAlignment(Pos.CENTER_LEFT);
        cardHeader.setSpacing(8);

        Label dot = new Label("●");
        dot.setStyle("-fx-font-size: 8px; -fx-text-fill: " + accentColor + ";");

        Label hdrLabel = new Label(sectionTitle);
        hdrLabel.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 600;" +
            "-fx-text-fill: #E2E8F0;"
        );
        cardHeader.getChildren().addAll(dot, hdrLabel);

        // Card rows
        VBox rowsBox = new VBox(0);
        rowsBox.setStyle("-fx-padding: 4 0 4 0;");

        for (int i = 0; i < rows.length; i++) {
            HBox row = new HBox();
            row.setStyle(
                "-fx-padding: 10 16 10 16;" +
                (i % 2 == 0 ? "-fx-background-color: transparent;" : "-fx-background-color: #0F172A22;")
            );
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(rows[i][0]);
            nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            valueLabels[i].setStyle(
                "-fx-font-size: 13px;" +
                "-fx-font-weight: 600;" +
                "-fx-text-fill: " + accentColor + ";"
            );

            row.getChildren().addAll(nameLabel, rowSpacer, valueLabels[i]);
            rowsBox.getChildren().add(row);
        }

        card.getChildren().addAll(cardHeader, rowsBox);
        return card;
    }

    private Button toolbarButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-radius: 7;" +
            "-fx-padding: 7 14 7 14;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: 500;" +
            "-fx-cursor: hand;"
        );
        return btn;
    }

    // ── Data refresh (logic unchanged) ───────────────────────────────────────

    private void refreshData() {
        if (statistics != null) {
            totalRequestsVal.setText(String.valueOf(statistics.getTotalRequests()));
            allowedRequestsVal.setText(String.valueOf(statistics.getAllowedRequests()));
            blockedRequestsVal.setText(String.valueOf(statistics.getBlockedRequests()));
            cacheHitsVal.setText(String.valueOf(statistics.getCacheHits()));
            cacheMissesVal.setText(String.valueOf(statistics.getCacheMisses()));
            hitRatioVal.setText(String.format("%.1f%%", statistics.getHitRatio()));
            networkRequestsVal.setText(String.valueOf(statistics.getNetworkRequests()));
            adsBlockedVal.setText(String.valueOf(statistics.getAdsBlocked()));
            trackersBlockedVal.setText(String.valueOf(statistics.getTrackersBlocked()));
            avgResponseTimeVal.setText(String.format("%.1f ms", statistics.getAverageResponseTime()));
            lruEvictionsVal.setText(String.valueOf(statistics.getEvictions()));
            expiredEntriesVal.setText(String.valueOf(statistics.getExpiredEntries()));
            bytesFromCacheVal.setText(formatBytes(statistics.getBytesFromCache()));
            bytesFromNetworkVal.setText(formatBytes(statistics.getBytesFromNetwork()));
        }
        if (cacheManager != null) {
            cacheEntriesVal.setText(cacheManager.size() + " / " + cacheManager.capacity());
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
