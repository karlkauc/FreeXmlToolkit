package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.util.ProjectLinks;
import org.fxt.freexmltoolkit.util.VersionUtil;

/**
 * The Help activity side panel: about / version information and quick links.
 * Reuses {@link VersionUtil} for the version (manifest → build-info → fallback).
 */
public class HelpPanel extends VBox {

    private static final String GITHUB_URL = ProjectLinks.GITHUB_URL;
    private static final String DOCS_URL = ProjectLinks.DOCS_URL;
    private static final String FUNDSXML_SITE_URL = "http://www.fundsxml.org";
    private static final String SCHEMA_DOCS_URL = "https://fundsxml.github.io/";

    @FXML private Label version;
    @FXML private Label build;
    @FXML private Label vendor;
    @FXML private Label updateStatus;
    @FXML private PanelActionList projectActions;
    @FXML private Button sponsorButton;
    @FXML private Button reportProblemButton;
    @FXML private SidePanelSection fundsSection;
    @FXML private Label fundsStatus;

    /** Loads the layout from {@code /pages/panels/HelpPanel.fxml} and fills the version texts. */
    public HelpPanel() {
        FxmlViews.load(this, "/pages/panels/HelpPanel.fxml");

        version.setText("Version " + VersionUtil.getVersion());
        build.setText("Build: " + VersionUtil.getBuildTimestampFormatted());
        vendor.setText(VersionUtil.getVendor());
        sponsorButton.setGraphic(ProjectLinks.sponsorIcon(16));

        // Anonymous in-app problem report — only when error reporting is enabled.
        if (!org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog.isAvailable()) {
            projectActions.getChildren().remove(reportProblemButton);
        }
        // FundsXML extension — only when enabled in the settings (conditional).
        if (!FundsXmlActionRunner.isEnabled()) {
            getChildren().removeAll(fundsSection, fundsStatus);
        }
    }

    @FXML
    public void onGitHub() {
        browse(GITHUB_URL);
    }

    @FXML
    public void onSponsor() {
        browse(ProjectLinks.SPONSORS_URL);
    }

    @FXML
    public void onAbout() {
        AboutDialog.show(getScene() != null ? getScene().getWindow() : null);
    }

    @FXML
    public void onShortcuts() {
        KeyboardShortcutsDialog.show();
    }

    @FXML
    public void onReportProblem() {
        org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog.show(
                getScene() != null ? getScene().getWindow() : null, null);
    }

    @FXML
    public void onDocs() {
        browse(DOCS_URL);
    }

    @FXML
    public void onFundsXmlSite() {
        browse(FUNDSXML_SITE_URL);
    }

    @FXML
    public void onSchemaDocs() {
        browse(SCHEMA_DOCS_URL);
    }

    @FXML
    public void onCheckFundsXmlUpdates() {
        fundsStatus.setText("Checking…");
        FxtGui.executorService.submit(() -> {
            String msg = FundsXmlActionRunner.checkForUpdate();
            Platform.runLater(() -> fundsStatus.setText(msg));
        });
    }

    /** Checks for application updates asynchronously and shows the result. */
    @FXML
    public void checkForUpdates() {
        updateStatus.setText("Checking…");
        UpdateActionRunner.check().whenComplete((info, err) -> {
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.updateCheck("manual",
                    UpdateActionRunner.usageResult(info, err));
            Platform.runLater(() ->
                    updateStatus.setText(err != null ? "Update check failed." : UpdateActionRunner.describe(info)));
        });
    }

    /** @return the update-status line (for tests/observers). */
    public String getUpdateStatusText() {
        return updateStatus.getText();
    }

    /** @return the version line (for tests/observers). */
    public String getVersionText() {
        return version.getText();
    }

    /**
     * @return the quick-link URLs offered by the panel (GitHub, GitHub Sponsors, plus
     *         the FXT docs, FundsXML site and schema docs the legacy Help tab embedded).
     */
    public List<String> getQuickLinkUrls() {
        return List.of(GITHUB_URL, ProjectLinks.SPONSORS_URL, DOCS_URL, FUNDSXML_SITE_URL, SCHEMA_DOCS_URL);
    }

    private void browse(String url) {
        ProjectLinks.openInBrowser(url);
    }
}
