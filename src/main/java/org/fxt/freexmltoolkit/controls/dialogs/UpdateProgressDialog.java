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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.AutoUpdateService;
import org.fxt.freexmltoolkit.service.AutoUpdateService.UpdateProgress;
import org.fxt.freexmltoolkit.service.AutoUpdateService.UpdateResult;
import org.fxt.freexmltoolkit.service.AutoUpdateService.UpdateStage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Dialog that shows progress during update download and installation.
 *
 * <p>This dialog displays:
 * <ul>
 *   <li>Progress bar for download progress</li>
 *   <li>Current stage indicator (Downloading, Extracting, Installing)</li>
 *   <li>Status message with details</li>
 *   <li>Cancel button to abort the update</li>
 * </ul>
 *
 * @since 2.0
 */
public class UpdateProgressDialog extends Dialog<UpdateResult> {

    private static final Logger logger = LogManager.getLogger(UpdateProgressDialog.class);

    private final UpdateInfo updateInfo;
    private final AutoUpdateService autoUpdateService;

    private ProgressBar progressBar;
    private Label stageLabel;
    private Label statusLabel;
    private Label detailsLabel;
    private Button cancelButton;
    private FontIcon stageIcon;

    private CompletableFuture<UpdateResult> updateFuture;
    private volatile boolean cancelled = false;

    /**
     * Creates a new update progress dialog.
     *
     * @param updateInfo Information about the update being applied
     */
    public UpdateProgressDialog(UpdateInfo updateInfo) {
        this.updateInfo = updateInfo;
        this.autoUpdateService = ServiceRegistry.get(AutoUpdateService.class);

        initializeDialog();
    }

    private void initializeDialog() {
        setTitle("Updating FreeXmlToolkit");
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setResizable(false);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setPrefWidth(450);
        dialogPane.setPrefHeight(250);

        // Load CSS
        try {
            dialogPane.getStylesheets().addAll(
                    getClass().getResource("/css/app-theme.css").toExternalForm(),
                    getClass().getResource("/css/dialog-theme.css").toExternalForm()
            );
        } catch (Exception e) {
            logger.warn("Could not load dialog theme CSS", e);
        }

        // Build content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);

        // Header
        content.getChildren().add(createHeader());

        // Progress section
        content.getChildren().add(createProgressSection());

        // Details section
        content.getChildren().add(createDetailsSection());

        dialogPane.setContent(content);

        // Custom buttons - only Cancel during download
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(cancelButtonType);

        cancelButton = (Button) dialogPane.lookupButton(cancelButtonType);
        cancelButton.setOnAction(e -> handleCancel());

        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == cancelButtonType) {
                return UpdateResult.failure("Update cancelled by user");
            }
            return null;
        });

        // Handle dialog close
        setOnCloseRequest(e -> {
            if (!cancelled && autoUpdateService.isUpdateInProgress()) {
                e.consume();
                handleCancel();
            }
        });
    }

    private VBox createHeader() {
        VBox header = new VBox(5);

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        stageIcon = new FontIcon("bi-cloud-download");
        stageIcon.setIconSize(24);
        stageIcon.setIconColor(Color.web("#007bff"));

        stageLabel = new Label("Preparing update...");
        stageLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        headerRow.getChildren().addAll(stageIcon, stageLabel);
        header.getChildren().add(headerRow);

        Label versionLabel = new Label(String.format("Updating to version %s",
                updateInfo.latestVersion() != null ? updateInfo.latestVersion() : "latest"));
        versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");
        header.getChildren().add(versionLabel);

        return header;
    }

    private VBox createProgressSection() {
        VBox section = new VBox(8);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        statusLabel = new Label("Initializing...");
        statusLabel.setStyle("-fx-font-size: 12px;");

        section.getChildren().addAll(progressBar, statusLabel);

        return section;
    }

    private VBox createDetailsSection() {
        VBox section = new VBox(5);

        detailsLabel = new Label("Platform: " + autoUpdateService.getPlatformIdentifier());
        detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        section.getChildren().add(detailsLabel);

        return section;
    }

    /**
     * Starts the update process and shows the dialog.
     *
     * @param onComplete Callback when update completes (success or failure)
     */
    public void startUpdate(Consumer<UpdateResult> onComplete) {
        // Start the update process
        updateFuture = autoUpdateService.downloadAndApplyUpdate(updateInfo, this::handleProgress);

        updateFuture.thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.success()) {
                    // Update successful - close dialog and notify caller
                    logger.info("Update completed successfully");
                    handleUpdateSuccess(result);
                } else if (!cancelled) {
                    // Update failed
                    logger.warn("Update failed: {}", result.errorMessage());
                    handleUpdateFailure(result);
                }

                if (onComplete != null) {
                    onComplete.accept(result);
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                logger.error("Update exception", e);
                UpdateResult failResult = UpdateResult.failure("Update error: " + e.getMessage());
                handleUpdateFailure(failResult);

                if (onComplete != null) {
                    onComplete.accept(failResult);
                }
            });
            return null;
        });

        // Show the dialog
        show();
    }

    /**
     * Handles progress updates from the AutoUpdateService.
     */
    private void handleProgress(UpdateProgress progress) {
        Platform.runLater(() -> {
            updateStageUI(progress.stage());
            statusLabel.setText(progress.message());

            if (progress.stage() == UpdateStage.DOWNLOADING) {
                double percentage = progress.percentage();
                if (percentage >= 0) {
                    progressBar.setProgress(percentage);
                } else {
                    progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                }
            } else if (progress.stage() == UpdateStage.EXTRACTING ||
                    progress.stage() == UpdateStage.LAUNCHING_UPDATER) {
                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            }
        });
    }

    /**
     * Updates the UI based on the current stage.
     */
    private void updateStageUI(UpdateStage stage) {
        switch (stage) {
            case PREPARING -> {
                stageLabel.setText("Preparing update...");
                stageIcon.setIconLiteral("bi-gear");
                stageIcon.setIconColor(Color.web("#6c757d"));
            }
            case DOWNLOADING -> {
                stageLabel.setText("Downloading update...");
                stageIcon.setIconLiteral("bi-cloud-download");
                stageIcon.setIconColor(Color.web("#007bff"));
            }
            case EXTRACTING -> {
                stageLabel.setText("Extracting files...");
                stageIcon.setIconLiteral("bi-file-earmark-zip");
                stageIcon.setIconColor(Color.web("#17a2b8"));
            }
            case LAUNCHING_UPDATER -> {
                stageLabel.setText("Installing update...");
                stageIcon.setIconLiteral("bi-arrow-repeat");
                stageIcon.setIconColor(Color.web("#28a745"));
                cancelButton.setDisable(true);
            }
            case COMPLETED -> {
                stageLabel.setText("Update ready!");
                stageIcon.setIconLiteral("bi-check-circle");
                stageIcon.setIconColor(Color.web("#28a745"));
                progressBar.setProgress(1.0);
                cancelButton.setDisable(true);
            }
            case FAILED -> {
                stageLabel.setText("Update failed");
                stageIcon.setIconLiteral("bi-x-circle");
                stageIcon.setIconColor(Color.web("#dc3545"));
                progressBar.setProgress(0);
            }
        }
    }

    /**
     * Handles successful update completion.
     */
    private void handleUpdateSuccess(UpdateResult result) {
        updateStageUI(UpdateStage.COMPLETED);
        statusLabel.setText("Update ready. The application will restart shortly...");
        detailsLabel.setText("Please wait while the updater finishes installation.");

        // Replace Cancel button with Close
        cancelButton.setText("Close & Restart");
        cancelButton.setDisable(false);
        cancelButton.setOnAction(e -> {
            setResult(result);
            close();
            // Exit the application to let the updater take over
            Platform.exit();
        });

        // Auto-close after 3 seconds
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                Platform.runLater(() -> {
                    if (isShowing()) {
                        setResult(result);
                        close();
                        Platform.exit();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Handles update failure.
     */
    private void handleUpdateFailure(UpdateResult result) {
        updateStageUI(UpdateStage.FAILED);
        statusLabel.setText(result.errorMessage() != null ? result.errorMessage() : "Unknown error");
        detailsLabel.setText("You can try again or download manually from GitHub.");

        // Change button to Close
        cancelButton.setText("Close");
        cancelButton.setDisable(false);
        cancelButton.setOnAction(e -> {
            setResult(result);
            close();
        });
    }

    /**
     * Handles cancel button click.
     */
    private void handleCancel() {
        if (cancelled) {
            return;
        }

        cancelled = true;
        logger.info("User cancelled update");

        autoUpdateService.cancelUpdate();
        statusLabel.setText("Cancelling...");
        cancelButton.setDisable(true);
    }

    /**
     * Shows the dialog and waits for the result.
     *
     * @return Optional containing the update result, or empty if cancelled
     */
    public Optional<UpdateResult> showAndWaitForResult() {
        startUpdate(null);
        return showAndWait();
    }
}
