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

import java.awt.Desktop;
import java.net.URI;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.UpdateCheckService;

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
        /** Open browser to download manually */
        DOWNLOAD,
        /** Download and install the update automatically */
        DOWNLOAD_AND_INSTALL,
        /** Close dialog, ask again later */
        REMIND_LATER,
        /** Skip this version */
        SKIP_VERSION
    }

    private final UpdateInfo updateInfo;
    private final UpdateCheckService updateCheckService;
    @FXML private Label currentVersion;
    @FXML private Label latestVersion;
    @FXML private HBox notesHeader;
    @FXML private Label publishedDate;
    @FXML private TextArea releaseNotes;

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
        // Width must accommodate four buttons in one row without truncation
        // ("Download & Install" + "Open in Browser" + "Remind Me Later" +
        // "Skip This Version" plus icons, padding, and ButtonBar gaps).
        dialogPane.setMinWidth(820);
        dialogPane.setPrefWidth(860);
        dialogPane.setPrefHeight(500);

        // Load CSS
        try {
            dialogPane.getStylesheets().addAll(
                    org.fxt.freexmltoolkit.util.DialogHelper.getThemeStylesheets()
            );
        } catch (Exception e) {
            logger.warn("Could not load dialog theme CSS", e);
        }

        // Content: header, versions, release notes, info tip
        dialogPane.setContent(FxmlViews.loadContent(this, "/pages/dialogs/UpdateNotificationDialog.fxml"));
        currentVersion.setText(versionText(updateInfo.currentVersion()));
        latestVersion.setText(versionText(updateInfo.latestVersion()));
        if (updateInfo.publishedDate() != null && !updateInfo.publishedDate().isBlank()) {
            publishedDate.setText(" - " + updateInfo.publishedDate());
        } else {
            notesHeader.getChildren().remove(publishedDate);
        }
        String notes = updateInfo.releaseNotes();
        if (notes == null || notes.isBlank()) {
            notes = "No release notes available for this version.";
        }
        releaseNotes.setText(notes);

        // Custom buttons
        ButtonType installButton = new ButtonType("Download & Install", ButtonBar.ButtonData.OK_DONE);
        ButtonType downloadButton = new ButtonType("Open in Browser", ButtonBar.ButtonData.LEFT);
        ButtonType remindLaterButton = new ButtonType("Remind Me Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType skipVersionButton = new ButtonType("Skip This Version", ButtonBar.ButtonData.OTHER);

        dialogPane.getButtonTypes().addAll(installButton, downloadButton, remindLaterButton, skipVersionButton);

        // ButtonBar enforces a uniform default min-width that truncates the
        // longer labels ("Open in Browser", "Skip This Version"). Force each
        // button to grow to its natural label width so nothing gets clipped.
        for (ButtonType bt : new ButtonType[]{installButton, downloadButton, remindLaterButton, skipVersionButton}) {
            Button b = (Button) dialogPane.lookupButton(bt);
            if (b != null) {
                b.setMinWidth(Region.USE_PREF_SIZE);
            }
        }

        // Style the Install button (primary action)
        Button installBtn = (Button) dialogPane.lookupButton(installButton);
        if (installBtn != null) {
            installBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
            IconifyIcon installIcon = new IconifyIcon("bi-cloud-download");
            installIcon.setIconSize(14);
            installIcon.setIconColor(Color.WHITE);
            installBtn.setGraphic(installIcon);
        }

        // Style the Download button (secondary action)
        Button downloadBtn = (Button) dialogPane.lookupButton(downloadButton);
        if (downloadBtn != null) {
            downloadBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
            IconifyIcon downloadIcon = new IconifyIcon("bi-box-arrow-up-right");
            downloadIcon.setIconSize(14);
            downloadIcon.setIconColor(Color.WHITE);
            downloadBtn.setGraphic(downloadIcon);
        }

        // Result converter
        setResultConverter(buttonType -> {
            if (buttonType == installButton) {
                return UpdateAction.DOWNLOAD_AND_INSTALL;
            } else if (buttonType == downloadButton) {
                openDownloadPage();
                return UpdateAction.DOWNLOAD;
            } else if (buttonType == skipVersionButton) {
                skipVersion();
                return UpdateAction.SKIP_VERSION;
            }
            return UpdateAction.REMIND_LATER;
        });
    }

    private static String versionText(String version) {
        return version != null ? version : "Unknown";
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
