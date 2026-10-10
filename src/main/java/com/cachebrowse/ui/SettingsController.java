package com.cachebrowse.ui;

import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.service.SettingsService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Settings controller with modern card-based UI.
 * All settings logic and persistence is fully preserved.
 */
public class SettingsController {

    private final BrowserSettings settings;
    private final SettingsService settingsService;
    private final CacheManager cacheManager;

    public SettingsController(BrowserSettings settings) {
        this(settings, null, null);
    }

    public SettingsController(BrowserSettings settings, SettingsService settingsService, CacheManager cacheManager) {
        this.settings = settings;
        this.settingsService = settingsService;
        this.cacheManager = cacheManager;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Settings – CacheBrowse");
        stage.setResizable(false);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0F172A;");

        // ── Header ──────────────────────────────────────────────────────────
        HBox header = new HBox();
        header.setStyle(
            "-fx-background-color: #111827;" +
            "-fx-padding: 18 24 18 24;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 0 0 1 0;"
        );
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);

        Label icon = new Label("⚙");
        icon.setStyle("-fx-font-size: 18px; -fx-text-fill: #94A3B8;");

        Label titleLbl = new Label("Settings");
        titleLbl.setStyle("-fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC;");

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Button closeX = new Button("✕");
        closeX.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #64748B;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 14px;" +
            "-fx-border-color: transparent;"
        );
        closeX.setOnAction(e -> stage.close());

        header.getChildren().addAll(icon, titleLbl, hSpacer, closeX);

        // ── Body ─────────────────────────────────────────────────────────────
        VBox body = new VBox(20);
        body.setPadding(new Insets(24));
        body.setStyle("-fx-background-color: #0F172A;");

        // Input fields
        TextField homePage      = styledField(settings.getHomePage());
        TextField searchEngine  = styledField(settings.getDefaultSearchEngine());
        TextField ttl           = styledField(String.valueOf(settings.getCacheTtl() / 1000));
        TextField capacity      = styledField(String.valueOf(settings.getCacheCapacity()));

        // Checkboxes
        CheckBox cacheEnabled      = styledCheckBox("Enable Cache",           settings.isCacheEnabled());
        CheckBox adBlocking        = styledCheckBox("Enable Ad Blocking",     settings.isAdBlockingEnabled());
        CheckBox trackerBlocking   = styledCheckBox("Enable Tracker Blocking",settings.isTrackerBlockingEnabled());

        // ── Browser section ──────────────────────────────────────────────────
        body.getChildren().add(sectionCard("Browser",
            new String[]{"Home Page", "Search Engine URL"},
            new TextField[]{homePage, searchEngine}
        ));

        // ── Cache section ────────────────────────────────────────────────────
        body.getChildren().add(sectionCard("Cache",
            new String[]{"Cache TTL (seconds)", "Cache Capacity (entries)"},
            new TextField[]{ttl, capacity}
        ));

        // ── Toggles section ──────────────────────────────────────────────────
        VBox toggleCard = styledCard("Privacy & Protection");
        VBox togglesBox = new VBox(14, cacheEnabled, adBlocking, trackerBlocking);
        togglesBox.setPadding(new Insets(4, 16, 16, 16));
        toggleCard.getChildren().add(togglesBox);
        body.getChildren().add(toggleCard);

        // ── Action buttons ───────────────────────────────────────────────────
        HBox btnBar = new HBox(10);
        btnBar.setAlignment(Pos.CENTER_RIGHT);

        Button cancel = outlineButton("Cancel");
        cancel.setOnAction(e -> stage.close());

        Button save = primaryButton("Save Settings");
        save.setOnAction(event -> {
            try {
                long ttlSec = Long.parseLong(ttl.getText().trim());
                int  cap    = Integer.parseInt(capacity.getText().trim());
                if (ttlSec <= 0 || cap <= 0)
                    throw new IllegalArgumentException("TTL and Capacity must be positive numbers.");

                settings.setHomePage(homePage.getText().trim());
                settings.setDefaultSearchEngine(searchEngine.getText().trim());
                settings.setCacheTtl(ttlSec * 1000);
                settings.setCacheCapacity(cap);
                settings.setCacheEnabled(cacheEnabled.isSelected());
                settings.setAdBlockingEnabled(adBlocking.isSelected());
                settings.setTrackerBlockingEnabled(trackerBlocking.isSelected());

                if (cacheManager != null) cacheManager.setCapacity(cap);
                if (settingsService != null) settingsService.saveSettings(settings);

                stage.close();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Settings");
                alert.setHeaderText("Please check your input");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });

        btnBar.getChildren().addAll(cancel, save);
        body.getChildren().add(btnBar);

        root.getChildren().addAll(header, body);

        Scene scene = new Scene(root, 560, 520);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    // ── UI component helpers ─────────────────────────────────────────────────

    private VBox sectionCard(String title, String[] labels, TextField[] fields) {
        VBox card = styledCard(title);
        VBox content = new VBox(14);
        content.setPadding(new Insets(4, 16, 16, 16));
        for (int i = 0; i < labels.length; i++) {
            VBox fieldGroup = new VBox(6);
            Label lbl = new Label(labels[i]);
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8; -fx-font-weight: 500;");
            fieldGroup.getChildren().addAll(lbl, fields[i]);
            content.getChildren().add(fieldGroup);
        }
        card.getChildren().add(content);
        return card;
    }

    private VBox styledCard(String title) {
        VBox card = new VBox(0);
        card.setStyle(
            "-fx-background-color: #1E293B;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        );

        HBox cardHeader = new HBox();
        cardHeader.setStyle(
            "-fx-padding: 14 16 14 16;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 0 0 1 0;"
        );
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        Label hdr = new Label(title);
        hdr.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #E2E8F0;");
        cardHeader.getChildren().add(hdr);

        card.getChildren().add(cardHeader);
        return card;
    }

    private TextField styledField(String value) {
        TextField f = new TextField(value);
        String base =
            "-fx-background-color: #0F172A;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 9 12 9 12;" +
            "-fx-font-size: 13px;";
        String focused =
            "-fx-background-color: #0F172A;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #3B82F6;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 9 12 9 12;" +
            "-fx-font-size: 13px;";
        f.setStyle(base);
        f.setPrefHeight(38);
        f.setMaxWidth(Double.MAX_VALUE);
        f.focusedProperty().addListener((obs, was, is) -> f.setStyle(is ? focused : base));
        return f;
    }

    private CheckBox styledCheckBox(String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 13px;");
        return cb;
    }

    private Button primaryButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: #3B82F6;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 10 20 10 20;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 600;"
        );
        return btn;
    }

    private Button outlineButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #94A3B8;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 10 20 10 20;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 13px;"
        );
        return btn;
    }
}
