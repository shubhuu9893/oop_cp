package com.cachebrowse.ui;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.model.User;
import com.cachebrowse.service.AuthenticationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Authentication dialog for sign-in, registration, and profile management.
 * UI redesigned with premium dark theme. All authentication logic is preserved.
 */
public class AuthDialog {
    private final AuthenticationService authenticationService;
    private final UserSession userSession;
    private Stage stage;

    public AuthDialog(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        this.userSession = authenticationService != null
                ? authenticationService.getUserSession()
                : UserSession.getInstance();
        this.stage = new Stage();
    }

    public void show() {
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("CacheBrowse – Account");
        stage.setResizable(false);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: #0F172A;");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(48, 40, 40, 40));
        root.setSpacing(0);

        // Brand header
        VBox brand = new VBox(4);
        brand.setAlignment(Pos.CENTER);
        brand.setStyle("-fx-padding: 0 0 32 0;");

        Label appName = new Label("CacheBrowse");
        appName.setStyle(
            "-fx-font-size: 28px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: #F8FAFC;"
        );
        Label appSub = new Label("Smart Caching Browser");
        appSub.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-text-fill: #64748B;"
        );
        brand.getChildren().addAll(appName, appSub);
        root.getChildren().add(brand);

        if (userSession.isLoggedIn()) {
            root.getChildren().add(buildProfilePanel());
        } else {
            root.getChildren().add(buildLoginPanel());
        }

        Scene scene = new Scene(root, 440, userSession.isLoggedIn() ? 400 : 460);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.showAndWait();
    }

    // ── Logged-in profile panel ─────────────────────────────────────────────

    private VBox buildProfilePanel() {
        VBox card = new VBox(20);
        card.setStyle(
            "-fx-background-color: #1E293B;" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 16;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 28 28 24 28;"
        );

        User user = userSession.getCurrentUser();

        Label sectionTitle = new Label("Your Account");
        sectionTitle.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: 600;" +
            "-fx-text-fill: #F8FAFC;"
        );

        // Avatar circle
        Label avatar = new Label(getInitial(user.getEmail()));
        avatar.setStyle(
            "-fx-font-size: 22px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: white;" +
            "-fx-background-color: #3B82F6;" +
            "-fx-background-radius: 28;" +
            "-fx-min-width: 56;" +
            "-fx-min-height: 56;" +
            "-fx-max-width: 56;" +
            "-fx-max-height: 56;" +
            "-fx-alignment: center;"
        );

        Label emailLbl = new Label(user.getEmail());
        emailLbl.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-font-weight: 500;"
        );

        Label idLbl = new Label("ID: " + user.getId());
        idLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        VBox userInfo = new VBox(4, emailLbl, idLbl);
        HBox userRow = new HBox(16, avatar, userInfo);
        userRow.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #334155;");

        HBox btnBar = new HBox(10);
        btnBar.setAlignment(Pos.CENTER_RIGHT);

        Button logoutBtn = styledButton("Logout", "#EF4444", "white");
        logoutBtn.setOnAction(e -> {
            authenticationService.logout();
            stage.close();
        });

        Button closeBtn = styledButton("Close", "#243047", "#94A3B8");
        closeBtn.setOnAction(e -> stage.close());

        btnBar.getChildren().addAll(closeBtn, logoutBtn);
        card.getChildren().addAll(sectionTitle, userRow, sep, btnBar);
        return card;
    }

    // ── Login / Register panel ──────────────────────────────────────────────

    private VBox buildLoginPanel() {
        VBox card = new VBox(20);
        card.setStyle(
            "-fx-background-color: #1E293B;" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 16;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 28 28 24 28;"
        );

        Label sectionTitle = new Label("Sign in to your account");
        sectionTitle.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: 600;" +
            "-fx-text-fill: #F8FAFC;"
        );

        // Email field
        TextField emailField = styledField("Email address", false);

        // Password field
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setStyle(fieldStyle());
        passwordField.setPrefHeight(44);

        addFieldHoverFocus(emailField);
        addPasswordHoverFocus(passwordField);

        VBox fields = new VBox(12);
        fields.getChildren().addAll(
            labelFor("Email"), emailField,
            labelFor("Password"), passwordField
        );

        // Action buttons
        Button loginBtn = styledButton("Sign In", "#3B82F6", "white");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(44);
        loginBtn.setStyle(loginBtn.getStyle() + "-fx-font-size: 14px; -fx-font-weight: 600;");

        Button registerBtn = styledButton("Create Account", "#243047", "#94A3B8");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setPrefHeight(44);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #64748B;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: transparent;"
        );
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> stage.close());

        // Error label (hidden by default)
        Label errorLabel = new Label();
        errorLabel.setStyle(
            "-fx-text-fill: #EF4444;" +
            "-fx-font-size: 12px;"
        );
        errorLabel.setVisible(false);

        loginBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            String pass  = passwordField.getText();
            if (email.isEmpty()) { showFieldError(errorLabel, "Please enter your email address."); return; }
            errorLabel.setVisible(false);
            loginBtn.setText("Signing in…");
            loginBtn.setDisable(true);
            authenticationService.loginAsync(email, pass).thenAccept(ok ->
                javafx.application.Platform.runLater(() -> {
                    loginBtn.setText("Sign In");
                    loginBtn.setDisable(false);
                    stage.close();
                })
            ).exceptionally(ex -> {
                javafx.application.Platform.runLater(() -> {
                    loginBtn.setText("Sign In");
                    loginBtn.setDisable(false);
                    showFieldError(errorLabel, "Login failed. Check your credentials.");
                });
                return null;
            });
        });

        registerBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            String pass  = passwordField.getText();
            if (email.isEmpty()) { showFieldError(errorLabel, "Please enter an email address."); return; }
            errorLabel.setVisible(false);
            registerBtn.setText("Creating…");
            registerBtn.setDisable(true);
            authenticationService.registerAsync(email, pass).thenAccept(ok ->
                javafx.application.Platform.runLater(() -> {
                    registerBtn.setText("Create Account");
                    registerBtn.setDisable(false);
                    stage.close();
                })
            ).exceptionally(ex -> {
                javafx.application.Platform.runLater(() -> {
                    registerBtn.setText("Create Account");
                    registerBtn.setDisable(false);
                    showFieldError(errorLabel, "Registration failed. Try a different email.");
                });
                return null;
            });
        });

        VBox btnGroup = new VBox(8, loginBtn, registerBtn, errorLabel, cancelBtn);

        card.getChildren().addAll(sectionTitle, fields, btnGroup);
        return card;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String fieldStyle() {
        return
            "-fx-background-color: #0F172A;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #334155;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 10 14 10 14;" +
            "-fx-font-size: 14px;";
    }

    private String fieldFocusStyle() {
        return
            "-fx-background-color: #0F172A;" +
            "-fx-text-fill: #F8FAFC;" +
            "-fx-prompt-text-fill: #64748B;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #3B82F6;" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 10 14 10 14;" +
            "-fx-font-size: 14px;";
    }

    private TextField styledField(String prompt, boolean isPassword) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setStyle(fieldStyle());
        f.setPrefHeight(44);
        return f;
    }

    private void addFieldHoverFocus(TextField f) {
        f.focusedProperty().addListener((obs, was, is) ->
            f.setStyle(is ? fieldFocusStyle() : fieldStyle()));
    }

    private void addPasswordHoverFocus(PasswordField f) {
        f.focusedProperty().addListener((obs, was, is) ->
            f.setStyle(is ? fieldFocusStyle() : fieldStyle()));
    }

    private Label labelFor(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: #94A3B8;");
        return l;
    }

    private Button styledButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        String base =
            "-fx-background-color: " + bg + ";" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 10 20 10 20;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 500;";
        btn.setStyle(base);
        return btn;
    }

    private void showFieldError(Label errorLabel, String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private String getInitial(String email) {
        if (email == null || email.isEmpty()) return "?";
        return String.valueOf(Character.toUpperCase(email.charAt(0)));
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Authentication");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
