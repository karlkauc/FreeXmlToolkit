package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Objects;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateNotificationDialog;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateProgressDialog;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.fxt.freexmltoolkit.util.VersionUtil;

/** Shell "About" dialog (ported from the legacy MainController). */
public final class AboutDialog {

    private static final Logger logger = LogManager.getLogger(AboutDialog.class);

    private AboutDialog() {
    }

    /** Builds the dialog. {@code owner} may be null (no owner window). */
    public static Dialog<Void> build(Window owner) {
        final String version = VersionUtil.getVersion();
        final String buildTs = VersionUtil.getBuildTimestampFormatted();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("About FreeXmlToolkit");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(Objects.requireNonNull(
                AboutDialog.class.getResource("/css/dialog-theme.css")).toExternalForm());
        dialogPane.setPrefWidth(560);

        // Set the window icon once the dialog is showing (the Scene/Window does not
        // exist yet at build time, so this cannot be done here directly).
        dialog.setOnShowing(evt -> {
            try {
                javafx.stage.Stage stage = (javafx.stage.Stage) dialogPane.getScene().getWindow();
                stage.getIcons().add(new javafx.scene.image.Image(java.util.Objects.requireNonNull(
                        AboutDialog.class.getResourceAsStream("/img/logo.png"))));
            } catch (Exception e) {
                logger.warn("Could not load logo for about dialog window.", e);
            }
        });

        ImageView logo = null;
        try {
            logo = new ImageView(new Image(Objects.requireNonNull(
                    AboutDialog.class.getResourceAsStream("/img/logo.png"))));
            logo.setFitHeight(72);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog graphic.", e);
        }

        Label title = new Label("FreeXmlToolkit");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill: #1f2937;");
        Label tagline = new Label("Universal Toolkit for XML, XSD, XSLT, Schematron & FOP");
        tagline.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #6b7280;");
        tagline.setWrapText(true);
        Label versionPill = new Label("v" + version);
        versionPill.setStyle("-fx-background-color: #e7f3ff;-fx-text-fill: #0b5ed7;-fx-font-weight: 600;"
                + "-fx-font-size: 12px;-fx-padding: 3 10 3 10;-fx-background-radius: 12;");
        VBox titleBox = new VBox(4, title, tagline, versionPill);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox header = new HBox(16);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (logo != null) {
            header.getChildren().add(logo);
        }
        header.getChildren().add(titleBox);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        GridPane info = new GridPane();
        info.setHgap(14);
        info.setVgap(6);
        info.setStyle("-fx-background-color: #f9fafb;-fx-background-radius: 8;-fx-border-color: #e5e7eb;"
                + "-fx-border-radius: 8;-fx-padding: 12;");
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(120);
        c1.setHalignment(javafx.geometry.HPos.LEFT);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        info.getColumnConstraints().addAll(c1, c2);
        int row = 0;
        addInfoRow(info, row++, "Version", version);
        if (!buildTs.isBlank()) {
            addInfoRow(info, row++, "Build", buildTs);
        }
        addInfoRow(info, row++, "Java", System.getProperty("java.version", "?")
                + "  (" + System.getProperty("java.vm.vendor", "?") + ")");
        addInfoRow(info, row++, "JavaFX", System.getProperty("javafx.runtime.version", "?"));
        addInfoRow(info, row++, "OS", System.getProperty("os.name", "?") + " "
                + System.getProperty("os.version", "") + "  (" + System.getProperty("os.arch", "?") + ")");

        Label copyright = new Label("Copyright © " + VersionUtil.getVendor()
                + " 2024-2026. All rights reserved.");
        copyright.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6b7280;");
        Hyperlink licenseLink = new Hyperlink("Licensed under the Apache License, Version 2.0");
        licenseLink.setOnAction(e -> openExternalUrl("http://www.apache.org/licenses/LICENSE-2.0"));
        licenseLink.setStyle("-fx-padding: 0; -fx-text-fill: #0b5ed7;");
        VBox legal = new VBox(2, copyright, licenseLink);

        Button githubBtn = linkButton("bi-github", "GitHub",
                "https://github.com/karlkauc/FreeXmlToolkit");
        Button docsBtn = linkButton("bi-book", "Documentation",
                "https://karlkauc.github.io/FreeXmlToolkit");
        Button issueBtn = linkButton("bi-bug", "Report an issue",
                "https://github.com/karlkauc/FreeXmlToolkit/issues/new");
        HBox links = new HBox(8, githubBtn, docsBtn, issueBtn);
        links.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox content = new VBox(14, header, new Separator(), info, legal, links);
        content.setStyle("-fx-padding: 18 20 8 20;");
        dialogPane.setContent(content);

        ButtonType copyVersionType = new ButtonType("Copy version", ButtonBar.ButtonData.LEFT);
        ButtonType checkUpdatesType = new ButtonType("Check for updates", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(copyVersionType, checkUpdatesType, ButtonType.CLOSE);

        Button copyBtn = (Button) dialogPane.lookupButton(copyVersionType);
        copyBtn.setGraphic(new IconifyIcon("bi-clipboard"));
        copyBtn.addEventFilter(ActionEvent.ACTION, evt -> {
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                    java.util.Map.of(javafx.scene.input.DataFormat.PLAIN_TEXT,
                            "FreeXmlToolkit " + version
                                    + (buildTs.isBlank() ? "" : " (build " + buildTs + ")")));
            copyBtn.setText("Copied!");
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(Duration.seconds(1.5));
            pt.setOnFinished(e -> copyBtn.setText("Copy version"));
            pt.play();
            evt.consume();
        });

        Button updatesBtn = (Button) dialogPane.lookupButton(checkUpdatesType);
        updatesBtn.setGraphic(new IconifyIcon("bi-arrow-clockwise"));
        updatesBtn.addEventFilter(ActionEvent.ACTION, evt -> {
            evt.consume();
            checkForUpdatesFromAbout(updatesBtn);
        });

        return dialog;
    }

    /** Convenience: build and show. */
    public static void show(Window owner) {
        build(owner).showAndWait();
    }

    // Ported verbatim from MainController.addInfoRow.
    private static void addInfoRow(GridPane grid, int row, String key, String value) {
        Label k = new Label(key);
        k.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11.5px; -fx-font-weight: 600;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: #1f2937; -fx-font-size: 12.5px;");
        v.setWrapText(true);
        grid.add(k, 0, row);
        grid.add(v, 1, row);
    }

    // Ported verbatim from MainController.linkButton.
    private static Button linkButton(String iconLiteral, String text, String url) {
        Button btn = new Button(text);
        btn.setGraphic(new IconifyIcon(iconLiteral));
        btn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-border-color: #d1d5db;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-text-fill: #1f2937;" +
                        "-fx-padding: 5 12 5 12;" +
                        "-fx-cursor: hand;");
        btn.setOnAction(e -> openExternalUrl(url));
        return btn;
    }

    // Ported verbatim from MainController.openExternalUrl.
    private static void openExternalUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ex) {
            logger.warn("Could not open URL {}: {}", url, ex.getMessage());
        }
    }

    // Ported verbatim from MainController.checkForUpdatesFromAbout.
    private static void checkForUpdatesFromAbout(Button trigger) {
        String original = trigger.getText();
        trigger.setText("Checking...");
        trigger.setDisable(true);

        UpdateCheckService service = ServiceRegistry.get(UpdateCheckService.class);
        service.checkForUpdates()
                .whenComplete((updateInfo, ex) -> Platform.runLater(() -> {
                    trigger.setText(original);
                    trigger.setDisable(false);
                    if (ex != null) {
                        showUpdateCheckError(ex);
                        return;
                    }
                    if (updateInfo != null && updateInfo.updateAvailable()) {
                        showUpdateDialog(updateInfo);
                    } else {
                        Alert info = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                                Alert.AlertType.INFORMATION, "Up to date",
                                "You are running the latest version.",
                                "FreeXmlToolkit "
                                        + (updateInfo != null ? updateInfo.currentVersion()
                                                : VersionUtil.getVersion())
                                        + " is current.");
                        info.initOwner(trigger.getScene().getWindow());
                        info.showAndWait();
                    }
                }));
    }

    // Ported from MainController.showUpdateCheckError (helper of checkForUpdatesFromAbout).
    private static void showUpdateCheckError(Throwable ex) {
        org.fxt.freexmltoolkit.util.DialogHelper.showWarning("Update check failed",
                "Could not check for updates",
                ex.getMessage() != null ? ex.getMessage() : ex.toString());
    }

    // Ported from MainController.showUpdateDialog (helper of checkForUpdatesFromAbout).
    public static void showUpdateDialog(UpdateInfo updateInfo) {
        try {
            UpdateNotificationDialog dialog = new UpdateNotificationDialog(updateInfo);
            dialog.showAndWait().ifPresent(action -> {
                if (action == UpdateNotificationDialog.UpdateAction.DOWNLOAD_AND_INSTALL) {
                    startAutoUpdate(updateInfo);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to show update dialog", e);
        }
    }

    // Ported from MainController.startAutoUpdate (helper of showUpdateDialog).
    private static void startAutoUpdate(UpdateInfo updateInfo) {
        logger.info("Starting auto-update to version {}", updateInfo.latestVersion());

        UpdateProgressDialog progressDialog = new UpdateProgressDialog(updateInfo);
        progressDialog.startUpdate(result -> {
            if (result.success()) {
                logger.info("Auto-update initiated successfully. Application will restart.");
            } else {
                logger.warn("Auto-update failed: {}", result.errorMessage());
                Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showError(
                        "Update Failed", "Could not complete the update",
                        result.errorMessage()
                                + "\n\nYou can try again or download the update manually from GitHub."));
            }
        });
    }
}
