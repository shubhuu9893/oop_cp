package com.cachebrowse.ui;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.model.HistoryEntry;
import com.cachebrowse.service.HistoryService;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * History dialog. UI redesigned with modern dark style. All history logic preserved.
 */
public class HistoryDialog {
    private final HistoryService historyService;
    private final UserSession userSession;
    private final Consumer<String> onNavigate;

    public HistoryDialog(HistoryService historyService, UserSession userSession, Consumer<String> onNavigate) {
        this.historyService = historyService;
        this.userSession    = userSession != null ? userSession : UserSession.getInstance();
        this.onNavigate     = onNavigate;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("History – CacheBrowse");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0F172A;");

        // ── Header ──────────────────────────────────────────────────────────
        HBox header = buildHeader("Browsing History", stage);

        // ── List ─────────────────────────────────────────────────────────────
        ObservableList<HistoryEntry> items = FXCollections.observableArrayList();
        ListView<HistoryEntry> listView = new ListView<>(items);
        listView.setStyle(
            "-fx-background-color: #0F172A;" +
            "-fx-border-color: transparent;"
        );
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(HistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                // Build a styled row
                VBox cell = new VBox(3);
                cell.setPadding(new Insets(10, 16, 10, 16));

                String titleStr = (item.getTitle() != null && !item.getTitle().isBlank())
                        ? item.getTitle() : item.getUrl();

                Label titleLabel = new Label("⏱  " + titleStr);
                titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: #E2E8F0;");

                Label urlLabel = new Label(item.getUrl());
                urlLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

                String time = item.getVisitedAt() != null ? item.getVisitedAt().toString() : "";
                Label timeLabel = new Label(time);
                timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

                cell.getChildren().addAll(titleLabel, urlLabel, timeLabel);
                setText(null);
                setGraphic(cell);

                if (isSelected()) {
                    setStyle("-fx-background-color: #1E293B;");
                } else {
                    setStyle("-fx-background-color: " +
                        (getIndex() % 2 == 0 ? "#0F172A" : "#111827") + ";");
                }
            }
        });

        loadHistory(items);

        // ── Footer ──────────────────────────────────────────────────────────
        HBox footer = new HBox(8);
        footer.setStyle(
            "-fx-background-color: #111827;" +
            "-fx-padding: 12 16 12 16;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 1 0 0 0;"
        );
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button openBtn = primaryButton("Open");
        openBtn.setOnAction(e -> {
            HistoryEntry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && onNavigate != null) {
                onNavigate.accept(selected.getUrl());
                stage.close();
            }
        });

        Button deleteBtn = outlineButton("Delete");
        deleteBtn.setOnAction(e -> {
            HistoryEntry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                historyService.deleteHistory(userSession.getUserId(), selected.getId());
                items.remove(selected);
            }
        });

        Button clearBtn = dangerButton("Clear History");
        clearBtn.setOnAction(e -> {
            historyService.clearHistory(userSession.getUserId());
            items.clear();
        });

        Button closeBtn = outlineButton("Close");
        closeBtn.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(spacer, openBtn, deleteBtn, clearBtn, closeBtn);

        root.getChildren().addAll(header, listView, footer);

        Scene scene = new Scene(root, 580, 500);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    private void loadHistory(ObservableList<HistoryEntry> items) {
        historyService.getHistoryAsync(userSession.getUserId(), 100)
            .thenAccept(list -> Platform.runLater(() -> items.setAll(list)));
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    static HBox buildHeader(String title, Stage stage) {
        HBox header = new HBox();
        header.setStyle(
            "-fx-background-color: #111827;" +
            "-fx-padding: 16 20 16 20;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 0 0 1 0;"
        );
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeX = new Button("✕");
        closeX.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #64748B;" +
            "-fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent;"
        );
        closeX.setOnAction(e -> stage.close());

        header.getChildren().addAll(titleLbl, spacer, closeX);
        return header;
    }

    static Button primaryButton(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: #3B82F6; -fx-text-fill: white;" +
            "-fx-background-radius: 7; -fx-padding: 8 16 8 16;" +
            "-fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: 600;"
        );
        return b;
    }

    static Button outlineButton(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #94A3B8;" +
            "-fx-background-radius: 7; -fx-border-color: #334155;" +
            "-fx-border-radius: 7; -fx-border-width: 1;" +
            "-fx-padding: 8 14 8 14; -fx-cursor: hand; -fx-font-size: 12px;"
        );
        return b;
    }

    static Button dangerButton(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: #7F1D1D; -fx-text-fill: #FCA5A5;" +
            "-fx-background-radius: 7; -fx-padding: 8 14 8 14;" +
            "-fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: 500;"
        );
        return b;
    }
}
