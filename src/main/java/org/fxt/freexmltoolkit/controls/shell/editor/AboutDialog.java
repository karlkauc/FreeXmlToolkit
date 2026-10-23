package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Objects;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateNotificationDialog;
import org.fxt.freexmltoolkit.controls.dialogs.UpdateProgressDialog;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.fxt.freexmltoolkit.service.telemetry.SessionMarker;
import org.fxt.freexmltoolkit.util.ProjectLinks;
import org.fxt.freexmltoolkit.util.VersionUtil;

/** Shell "About" dialog (ported from the legacy MainController). */
public final class AboutDialog {

    private static final Logger logger = LogManager.getLogger(AboutDialog.class);

    @FXML private HBox header;
    @FXML private ImageView logo;
    @FXML private Label versionPill;
    @FXML private GridPane info;
    @FXML private Label copyright;
    @FXML private HBox links;
    @FXML private Button sponsorButton;
    @FXML private Button reportButton;

    /** Controller instance of {@code /pages/dialogs/AboutDialog.fxml}; created by {@link #build(Window)}. */
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

        AboutDialog view = new AboutDialog();
        dialogPane.setContent(FxmlViews.loadContent(view, "/pages/dialogs/AboutDialog.fxml"));
        view.fill(version, buildTs);

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

    /** Fills the data-dependent parts of the loaded layout. */
    private void fill(String version, String buildTs) {
        try {
            logo.setImage(new Image(Objects.requireNonNull(
                    AboutDialog.class.getResourceAsStream("/img/logo.png"))));
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog graphic.", e);
            header.getChildren().remove(logo);
        }
        versionPill.setText("v" + version);

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

        copyright.setText("Copyright © " + VersionUtil.getVendor() + " 2024-2026. All rights reserved.");
        sponsorButton.setGraphic(ProjectLinks.sponsorIcon(16));
        if (!org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog.isAvailable()) {
            links.getChildren().remove(reportButton);
        }
    }

    @FXML
    public void onLicense() {
        openExternalUrl("http://www.apache.org/licenses/LICENSE-2.0");
    }

    @FXML
    public void onGitHub() {
        openExternalUrl(ProjectLinks.GITHUB_URL);
    }

    @FXML
    public void onSponsor() {
        openExternalUrl(ProjectLinks.SPONSORS_URL);
    }

    @FXML
    public void onDocs() {
        openExternalUrl(ProjectLinks.DOCS_URL);
    }

    @FXML
    public void onIssues() {
        openExternalUrl(ProjectLinks.ISSUES_URL);
    }

    @FXML
    public void onReportProblem() {
        org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog.show(
                links.getScene() == null ? null : links.getScene().getWindow(), null);
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

    private static void openExternalUrl(String url) {
        ProjectLinks.openInBrowser(url);
    }

    // Ported verbatim from MainController.checkForUpdatesFromAbout.
    private static void checkForUpdatesFromAbout(Button trigger) {
        String original = trigger.getText();
        trigger.setText("Checking...");
        trigger.setDisable(true);

        UpdateCheckService service = ServiceRegistry.get(UpdateCheckService.class);
        service.checkForUpdates()
                .whenComplete((updateInfo, ex) -> {
                    org.fxt.freexmltoolkit.service.telemetry.UsageEvents.updateCheck(
                            "manual", UpdateActionRunner.usageResult(updateInfo, ex),
                            UpdateActionRunner.failureReason(updateInfo, ex));
                    Platform.runLater(() -> {
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
                    });
                });
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
        if (!confirmNoOtherInstances()) {
            logger.info("Auto-update postponed: other application instances are running");
            return;
        }
        logger.info("Starting auto-update to version {}", updateInfo.latestVersion());
        try {
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
        } catch (Exception | LinkageError e) {
            // A LinkageError here means the installation changed under this process,
            // typically because another instance has already installed the update.
            logger.error("Could not start the auto-update", e);
            org.fxt.freexmltoolkit.util.DialogHelper.showError("Update Failed",
                    "The update could not be started",
                    "The installation was changed while this window was open, probably by an update"
                            + " started in another window.\n\nPlease close and restart FreeXmlToolkit.");
        }
    }

    /**
     * The updater replaces the installation, which breaks every other running instance
     * (they lose their JARs mid-session). Asks the user to close them first.
     *
     * @return {@code true} when no other instance runs (any more), {@code false} on cancel
     */
    private static boolean confirmNoOtherInstances() {
        SessionMarker marker = SessionMarker.createDefault();
        long ownPid = ProcessHandle.current().pid();
        int others;
        while ((others = marker.otherLiveSessions(ownPid)) > 0) {
            boolean retry = org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Update",
                    "Close the other FreeXmlToolkit windows first", otherInstancesMessage(others),
                    "Retry", "Cancel");
            if (!retry) {
                return false;
            }
        }
        return true;
    }

    static String otherInstancesMessage(int others) {
        return (others == 1
                ? "FreeXmlToolkit is running in 1 other window."
                : "FreeXmlToolkit is running in " + others + " other windows.")
                + " The update replaces the installed files, so the other windows would stop working"
                + " and unsaved changes there could be lost.\n\nSave your work, close them and choose Retry.";
    }
}
