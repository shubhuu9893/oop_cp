package com.cachebrowse.ui;

import com.cachebrowse.model.Bookmark;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Modern New Tab / Home page UI component.
 * UI redesigned with premium dark theme. All functionality preserved.
 */
public class NewTabPane extends VBox {

    public interface OnUrlEnterListener {
        void onUrlEntered(String url);
    }

    private TextField searchField;
    private final OnUrlEnterListener urlListener;
    private List<Bookmark> userBookmarks = Collections.emptyList();
    private FlowPane shortcutContainer;

    private static final Shortcut[] PRESETS = {
            new Shortcut("Google",    "https://www.google.com",    "G"),
            new Shortcut("YouTube",   "https://www.youtube.com",   "▶"),
            new Shortcut("GitHub",    "https://github.com",        "</>"),
            new Shortcut("ChatGPT",   "https://chat.openai.com",   "AI"),
            new Shortcut("Wikipedia", "https://www.wikipedia.org", "W")
    };

    public NewTabPane(OnUrlEnterListener urlListener) {
        this.urlListener = urlListener != null ? urlListener : __ -> {};
        setStyle("-fx-background-color: #0F172A;");
        buildContent();
    }

    public void setBookmarks(List<Bookmark> bookmarks) {
        this.userBookmarks = bookmarks == null ? Collections.emptyList() : new ArrayList<>(bookmarks);
        rebuildShortcuts();
    }

    private void buildContent() {
        setAlignment(Pos.TOP_CENTER);
        setSpacing(0);

        // Scroll-like center content
        VBox centerContent = new VBox(32);
        centerContent.setAlignment(Pos.CENTER);
        centerContent.setPadding(new Insets(80, 40, 40, 40));
        centerContent.setMaxWidth(680);

        centerContent.getChildren().addAll(
            buildBrand(),
            buildSearchBox(),
            buildShortcutSection()
        );

        Region topSpacer = new Region();
        topSpacer.setMinHeight(0);
        VBox.setVgrow(topSpacer, Priority.NEVER);

        VBox wrapper = new VBox();
        wrapper.setAlignment(Pos.TOP_CENTER);
        wrapper.getChildren().add(centerContent);
        VBox.setVgrow(wrapper, Priority.ALWAYS);

        getChildren().add(wrapper);
    }

    private VBox buildBrand() {
        VBox brand = new VBox(6);
        brand.setAlignment(Pos.CENTER);

        // CacheBrowse wordmark
        Label title = new Label("CacheBrowse");
        title.setStyle(
            "-fx-font-size: 38px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: #F8FAFC;"
        );

        Label subtitle = new Label("Smart Caching Browser");
        subtitle.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-text-fill: #64748B;" +
            "-fx-font-weight: 400;"
        );

        brand.getChildren().addAll(title, subtitle);
        return brand;
    }

    private VBox buildSearchBox() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);

        searchField = new TextField();
        searchField.setPromptText("  Search the web or enter a URL");
        searchField.setMaxWidth(560);
        searchField.setPrefHeight(52);
        searchField.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-background-color: #1E293B;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 26;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 26;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 0 20 0 20;"
        );
        searchField.setOnMouseEntered(e -> searchField.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-background-color: #243047;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 26;" +
            "-fx-border-color: #475569;" +
            "-fx-border-radius: 26;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 0 20 0 20;"
        ));
        searchField.setOnMouseExited(e -> searchField.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-background-color: #1E293B;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 26;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 26;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 0 20 0 20;"
        ));
        searchField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                searchField.setStyle(
                    "-fx-font-size: 15px;" +
                    "-fx-background-color: #0F172A;" +
                    "-fx-text-fill: #F8FAFC;" +
                    "-fx-prompt-text-fill: #64748B;" +
                    "-fx-background-radius: 26;" +
                    "-fx-border-color: #3B82F6;" +
                    "-fx-border-radius: 26;" +
                    "-fx-border-width: 2;" +
                    "-fx-padding: 0 20 0 20;"
                );
            } else {
                searchField.setStyle(
                    "-fx-font-size: 15px;" +
                    "-fx-background-color: #1E293B;" +
                    "-fx-text-fill: #F8FAFC;" +
                    "-fx-prompt-text-fill: #64748B;" +
                    "-fx-background-radius: 26;" +
                    "-fx-border-color: #334155;" +
                    "-fx-border-radius: 26;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 0 20 0 20;"
                );
            }
        });

        searchField.setOnAction(e -> {
            String text = searchField.getText().trim();
            if (!text.isEmpty()) navigate(text);
        });

        box.getChildren().add(searchField);
        return box;
    }

    private VBox buildShortcutSection() {
        VBox section = new VBox(14);
        section.setAlignment(Pos.CENTER);

        Label heading = new Label("Quick Access");
        heading.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: #64748B;" +
            "-fx-font-weight: 600;" +
            "-fx-letter-spacing: 0.1em;"
        );

        shortcutContainer = new FlowPane(12, 12);
        shortcutContainer.setAlignment(Pos.CENTER);
        populateShortcuts();

        section.getChildren().addAll(heading, shortcutContainer);
        return section;
    }

    private void populateShortcuts() {
        shortcutContainer.getChildren().clear();
        for (Shortcut s : PRESETS) {
            shortcutContainer.getChildren().add(makeShortcutTile(s.name(), s.icon(), s.url()));
        }
        for (Bookmark bm : userBookmarks) {
            shortcutContainer.getChildren().add(makeShortcutTile(bm.getTitle(), "★", bm.getUrl()));
        }
    }

    private void rebuildShortcuts() {
        if (shortcutContainer != null) {
            populateShortcuts();
        }
    }

    private Button makeShortcutTile(String name, String icon, String url) {
        String base =
            "-fx-background-color: #1E293B;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-text-fill: #CBD5E1;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: 500;" +
            "-fx-cursor: hand;" +
            "-fx-alignment: CENTER;";

        String hover =
            "-fx-background-color: #243047;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #3B82F6;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: 500;" +
            "-fx-cursor: hand;" +
            "-fx-alignment: CENTER;";

        Button tile = new Button(icon + "\n" + name);
        tile.setPrefSize(92, 80);
        tile.setMinSize(92, 80);
        tile.setMaxSize(92, 80);
        tile.setStyle(base);
        tile.setOnMouseEntered(e -> tile.setStyle(hover));
        tile.setOnMouseExited(e -> tile.setStyle(base));
        tile.setOnAction(e -> navigate(url));
        return tile;
    }

    private void navigate(String input) {
        String resolved = resolveInput(input);
        if (urlListener != null) {
            urlListener.onUrlEntered(resolved != null ? resolved : input);
        }
    }

    private String resolveInput(String input) {
        if (input == null || input.isBlank()) return null;
        if (input.startsWith("http://") || input.startsWith("https://")) return input;
        if (input.contains(".") && !input.contains(" ") && !input.startsWith(".")) return "https://" + input;
        String encoded = java.net.URLEncoder.encode(input, java.nio.charset.StandardCharsets.UTF_8);
        return "https://www.google.com/search?q=" + encoded;
    }

    private record Shortcut(String name, String url, String icon) {}

    public TextField getSearchField() { return searchField; }
}
