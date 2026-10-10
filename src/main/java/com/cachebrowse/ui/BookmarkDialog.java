package com.cachebrowse.ui;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.model.Bookmark;
import com.cachebrowse.service.BookmarkService;
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
 * Bookmarks dialog. UI redesigned with modern dark style. All bookmark logic preserved.
 */
public class BookmarkDialog {
    private final BookmarkService bookmarkService;
    private final UserSession userSession;
    private final Consumer<String> onNavigate;

    public BookmarkDialog(BookmarkService bookmarkService, UserSession userSession, Consumer<String> onNavigate) {
        this.bookmarkService = bookmarkService;
        this.userSession     = userSession != null ? userSession : UserSession.getInstance();
        this.onNavigate      = onNavigate;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Bookmarks – CacheBrowse");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0F172A;");

        // ── Header ──────────────────────────────────────────────────────────
        HBox header = HistoryDialog.buildHeader("Saved Bookmarks", stage);

        // ── List ─────────────────────────────────────────────────────────────
        ObservableList<Bookmark> items = FXCollections.observableArrayList();
        ListView<Bookmark> listView = new ListView<>(items);
        listView.setStyle(
            "-fx-background-color: #0F172A;" +
            "-fx-border-color: transparent;"
        );
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Bookmark item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                VBox cell = new VBox(3);
                cell.setPadding(new Insets(10, 16, 10, 16));

                String titleStr = (item.getTitle() != null && !item.getTitle().isBlank())
                        ? item.getTitle() : item.getUrl();

                Label titleLabel = new Label("★  " + titleStr);
                titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: #E2E8F0;");

                Label urlLabel = new Label(item.getUrl());
                urlLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

                cell.getChildren().addAll(titleLabel, urlLabel);
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

        loadBookmarks(items);

        // ── Footer ──────────────────────────────────────────────────────────
        HBox footer = new HBox(8);
        footer.setStyle(
            "-fx-background-color: #111827;" +
            "-fx-padding: 12 16 12 16;" +
            "-fx-border-color: #334155;" +
            "-fx-border-width: 1 0 0 0;"
        );
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button openBtn = HistoryDialog.primaryButton("Open");
        openBtn.setOnAction(e -> {
            Bookmark selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && onNavigate != null) {
                onNavigate.accept(selected.getUrl());
                stage.close();
            }
        });

        Button deleteBtn = HistoryDialog.outlineButton("Delete");
        deleteBtn.setOnAction(e -> {
            Bookmark selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                bookmarkService.removeBookmark(userSession.getUserId(), selected.getId());
                items.remove(selected);
            }
        });

        Button clearBtn = HistoryDialog.dangerButton("Clear All");
        clearBtn.setOnAction(e -> {
            bookmarkService.clearBookmarks(userSession.getUserId());
            items.clear();
        });

        Button closeBtn = HistoryDialog.outlineButton("Close");
        closeBtn.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(spacer, openBtn, deleteBtn, clearBtn, closeBtn);

        root.getChildren().addAll(header, listView, footer);

        Scene scene = new Scene(root, 540, 480);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    private void loadBookmarks(ObservableList<Bookmark> items) {
        bookmarkService.getBookmarksAsync(userSession.getUserId())
            .thenAccept(list -> Platform.runLater(() -> items.setAll(list)));
    }
}
