/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.*;
import java.net.URI;

/**
 * Dialog that displays update notification with release notes.
 *
 * <p>This dialog is shown when a newer version of FreeXmlToolkit is available on GitHub.
 * It displays the current and new version, release notes, and provides options to:
 * <ul>
 *   <li>Download - Opens the GitHub release page in the default browser</li>
 *   <li>Remind Me Later - Closes the dialog without action</li>
 *   <li>Skip This Version - Skips the current version and won't show the dialog again for it</li>
 * </ul>
 *
 * @since 2.0
 */
public class UpdateNotificationDialog extends Dialog<UpdateNotificationDialog.UpdateAction> {

    private static final Logger logger = LogManager.getLogger(UpdateNotificationDialog.class);

    /**
     * Actions that can be taken in the update dialog
     */
    public enum UpdateAction {
        DOWNLOAD,
        REMIND_LATER,
        SKIP_VERSION
    }

    private final UpdateInfo updateInfo;
    private final UpdateCheckService updateCheckService;

    /**
     * Creates a new update notification dialog.
     *
     * @param updateInfo the update information to display
     */
    public UpdateNotificationDialog(UpdateInfo updateInfo) {
        this.updateInfo = updateInfo;
        this.updateCheckService = ServiceRegistry.get(UpdateCheckService.class);

        initializeDialog();
    }

    private void initializeDialog() {
        setTitle("Update Available");
        initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setPrefWidth(550);
        dialogPane.setPrefHeight(500);

        // Load CSS
        try {
            dialogPane.getStylesheets().add(
                    getClass().getResource("/css/dialog-theme.css").toExternalForm()
            );
        } catch (Exception e) {
            logger.warn("Could not load dialog theme CSS", e);
        }

        // Build content
        VBox content = new VBox(20);
        content.setPadding(new Insets(0));

        // Header
        content.getChildren().add(createHeader());

        // Version info
        content.getChildren().add(createVersionInfo());

        // Release notes
        content.getChildren().add(createReleaseNotesSection());

        // Info box
        content.getChildren().add(createInfoBox());

        dialogPane.setContent(content);

        // Custom buttons
        ButtonType downloadButton = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
        ButtonType remindLaterButton = new ButtonType("Remind Me Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType skipVersionButton = new ButtonType("Skip This Version", ButtonBar.ButtonData.OTHER);

        dialogPane.getButtonTypes().addAll(downloadButton, remindLaterButton, skipVersionButton);

        // Style the Download button
        Button downloadBtn = (Button) dialogPane.lookupButton(downloadButton);
        if (downloadBtn != null) {
            downloadBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
            FontIcon downloadIcon = new FontIcon("bi-download");
            downloadIcon.setIconSize(14);
            downloadIcon.setIconColor(Color.WHITE);
            downloadBtn.setGraphic(downloadIcon);
        }

        // Result converter
        setResultConverter(buttonType -> {
            if (buttonType == downloadButton) {
                openDownloadPage();
                return UpdateAction.DOWNLOAD;
            } else if (buttonType == skipVersionButton) {
                skipVersion();
                return UpdateAction.SKIP_VERSION;
            }
            return UpdateAction.REMIND_LATER;
        });
    }

    private VBox createHeader() {
        VBox header = new VBox(5);
        header.getStyleClass().add("dialog-header");
        header.setStyle("-fx-background-color: linear-gradient(to right, #28a745, #20c997); -fx-padding: 20px;");

        HBox headerContent = new HBox(15);
        headerContent.setAlignment(Pos.CENTER_LEFT);

        // Icon
        FontIcon icon = new FontIcon("bi-arrow-up-circle-fill");
        icon.setIconSize(48);
        icon.setIconColor(Color.WHITE);

        // Text
        VBox textBox = new VBox(5);

        Label titleLabel = new Label("Update Available");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitleLabel = new Label("A new version of FreeXmlToolkit is available");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(255,255,255,0.9);");

        textBox.getChildren().addAll(titleLabel, subtitleLabel);
        headerContent.getChildren().addAll(icon, textBox);
        header.getChildren().add(headerContent);

        return header;
    }

    private HBox createVersionInfo() {
        HBox versionBox = new HBox(30);
        versionBox.setAlignment(Pos.CENTER);
        versionBox.setPadding(new Insets(15));
        versionBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6px;");

        // Current version
        VBox currentBox = createVersionBox("Current Version", updateInfo.currentVersion(), "#6c757d");

        // Arrow
        FontIcon arrowIcon = new FontIcon("bi-arrow-right");
        arrowIcon.setIconSize(24);
        arrowIcon.setIconColor(Color.web("#28a745"));

        // New version
        VBox newBox = createVersionBox("New Version", updateInfo.latestVersion(), "#28a745");

        versionBox.getChildren().addAll(currentBox, arrowIcon, newBox);

        HBox container = new HBox(versionBox);
        container.setPadding(new Insets(0, 20, 0, 20));
        container.setAlignment(Pos.CENTER);

        return container;
    }

    private VBox createVersionBox(String label, String version, String color) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);

        Label labelLbl = new Label(label);
        labelLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        Label versionLbl = new Label(version != null ? version : "Unknown");
        versionLbl.setStyle(String.format(
                "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: %s;", color));

        box.getChildren().addAll(labelLbl, versionLbl);
        return box;
    }

    private VBox createReleaseNotesSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(0, 20, 0, 20));
        VBox.setVgrow(section, Priority.ALWAYS);

        // Header with icon
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon notesIcon = new FontIcon("bi-journal-text");
        notesIcon.setIconSize(18);
        notesIcon.setIconColor(Color.web("#007bff"));

        Label headerLabel = new Label("What's New");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c5aa0;");

        if (updateInfo.publishedDate() != null && !updateInfo.publishedDate().isBlank()) {
            Label dateLabel = new Label(" - " + updateInfo.publishedDate());
            dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
            headerBox.getChildren().addAll(notesIcon, headerLabel, dateLabel);
        } else {
            headerBox.getChildren().addAll(notesIcon, headerLabel);
        }

        section.getChildren().add(headerBox);

        // Release notes text area
        TextArea releaseNotes = new TextArea();
        releaseNotes.setEditable(false);
        releaseNotes.setWrapText(true);
        VBox.setVgrow(releaseNotes, Priority.ALWAYS);
        releaseNotes.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace; " +
                        "-fx-font-size: 12px; " +
                        "-fx-background-color: #f8f9fa; " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px;"
        );

        String notes = updateInfo.releaseNotes();
        if (notes == null || notes.isBlank()) {
            notes = "No release notes available for this version.";
        }
        releaseNotes.setText(notes);

        section.getChildren().add(releaseNotes);

        return section;
    }

    private HBox createInfoBox() {
        HBox infoBox = new HBox(10);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setPadding(new Insets(10, 15, 10, 15));
        infoBox.setStyle(
                "-fx-background-color: #e7f3ff; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-border-color: #b6d4fe; " +
                        "-fx-border-radius: 4px;"
        );

        FontIcon infoIcon = new FontIcon("bi-info-circle-fill");
        infoIcon.setIconSize(16);
        infoIcon.setIconColor(Color.web("#0d6efd"));

        Label infoLabel = new Label("Click 'Download' to open the GitHub releases page in your browser.");
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0a58ca;");
        infoLabel.setWrapText(true);

        infoBox.getChildren().addAll(infoIcon, infoLabel);

        HBox container = new HBox(infoBox);
        container.setPadding(new Insets(0, 20, 10, 20));
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        return container;
    }

    private void openDownloadPage() {
        String url = updateInfo.downloadUrl();
        if (url == null || url.isBlank()) {
            url = "https://github.com/karlkauc/FreeXmlToolkit/releases";
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                logger.info("Opened download page: {}", url);
            } else {
                logger.warn("Desktop browsing not supported on this platform");
            }
        } catch (Exception e) {
            logger.error("Failed to open download page", e);
        }
    }

    private void skipVersion() {
        String versionToSkip = updateInfo.latestVersion();
        if (versionToSkip != null) {
            updateCheckService.setSkippedVersion(versionToSkip);
            logger.info("Skipped version: {}", versionToSkip);
        }
    }
}
