package com.cachebrowse.ui;

import com.cachebrowse.model.BrowserSettings;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class SettingsController {

    private final BrowserSettings settings;

    public SettingsController(
            BrowserSettings settings) {

        this.settings = settings;
    }

    public void show() {

        Stage stage = new Stage();

        stage.setTitle(
                "CacheBrowse Settings"
        );

        GridPane grid = new GridPane();

        grid.setPadding(
                new Insets(20)
        );

        grid.setHgap(15);
        grid.setVgap(15);

        TextField homePage =
                new TextField(
                        settings.getHomePage()
                );

        TextField ttl =
                new TextField(
                        String.valueOf(
                                settings.getCacheTtl() / 1000
                        )
                );

        TextField capacity =
                new TextField(
                        String.valueOf(
                                settings.getCacheCapacity()
                        )
                );

        CheckBox cacheEnabled =
                new CheckBox(
                        "Enable Cache"
                );

        cacheEnabled.setSelected(
                settings.isCacheEnabled()
        );

        CheckBox adBlocking =
                new CheckBox(
                        "Enable Ad Blocking"
                );

        adBlocking.setSelected(
                settings.isAdBlockingEnabled()
        );

        CheckBox trackerBlocking =
                new CheckBox(
                        "Enable Tracker Blocking"
                );

        trackerBlocking.setSelected(
                settings.isTrackerBlockingEnabled()
        );

        Button save =
                new Button("Save");

        save.setOnAction(event -> {

            try {

                settings.setHomePage(
                        homePage.getText()
                );

                settings.setCacheTtl(
                        Long.parseLong(
                                ttl.getText()
                        ) * 1000
                );

                settings.setCacheCapacity(
                        Integer.parseInt(
                                capacity.getText()
                        )
                );

                settings.setCacheEnabled(
                        cacheEnabled.isSelected()
                );

                settings.setAdBlockingEnabled(
                        adBlocking.isSelected()
                );

                settings.setTrackerBlockingEnabled(
                        trackerBlocking.isSelected()
                );

                stage.close();

            } catch (NumberFormatException e) {

                Alert alert =
                        new Alert(
                                Alert.AlertType.ERROR
                        );

                alert.setTitle(
                        "Invalid Settings"
                );

                alert.setHeaderText(
                        "Invalid number"
                );

                alert.setContentText(
                        "TTL and Cache Capacity "
                        + "must be numbers."
                );

                alert.showAndWait();
            }
        });

        grid.add(
                new Label("Home Page:"),
                0,
                0
        );

        grid.add(
                homePage,
                1,
                0
        );

        grid.add(
                new Label("TTL (seconds):"),
                0,
                1
        );

        grid.add(
                ttl,
                1,
                1
        );

        grid.add(
                new Label("Cache Capacity:"),
                0,
                2
        );

        grid.add(
                capacity,
                1,
                2
        );

        grid.add(
                cacheEnabled,
                1,
                3
        );

        grid.add(
                adBlocking,
                1,
                4
        );

        grid.add(
                trackerBlocking,
                1,
                5
        );

        grid.add(
                save,
                1,
                6
        );

        Scene scene =
                new Scene(
                        grid,
                        550,
                        400
                );

        stage.setScene(scene);
        stage.show();
    }
}