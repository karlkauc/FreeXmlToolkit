package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.util.ProjectLinks;
import org.fxt.freexmltoolkit.util.VersionUtil;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;

/**
 * The Help activity side panel: about / version information and quick links.
 * Reuses {@link VersionUtil} for the version (manifest → build-info → fallback).
 */
public class HelpPanel extends VBox {

    private static final String GITHUB_URL = ProjectLinks.GITHUB_URL;
    private static final String DOCS_URL = ProjectLinks.DOCS_URL;
    private static final String FUNDSXML_SITE_URL = "http://www.fundsxml.org";
    private static final String SCHEMA_DOCS_URL = "https://fundsxml.github.io/";

    private final Label version = new Label();
    private final Label updateStatus = new Label();

    public HelpPanel() {
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("HELP");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-panel-title");

        Label appName = new Label("FreeXmlToolkit");
        appName.getStyleClass().add("fxt-welcome-headline");

        version.setText("Version " + VersionUtil.getVersion());
        version.getStyleClass().add("fxt-placeholder-text");

        Label build = new Label("Build: " + VersionUtil.getBuildTimestampFormatted());
        build.getStyleClass().add("fxt-placeholder-text");
        Label vendor = new Label(VersionUtil.getVendor());
        vendor.getStyleClass().add("fxt-placeholder-text");

        PanelActionList project = new PanelActionList(
                PanelAction.of("help-github", "bi-github", "GitHub", () -> browse(GITHUB_URL)).color(ActionColor.NAVIGATE),
                PanelAction.of("help-sponsor", "bi-heart-fill", "Sponsor this Project",
                        () -> browse(ProjectLinks.SPONSORS_URL)),
                PanelAction.of("help-about", "bi-info-circle", "About",
                        () -> AboutDialog.show(getScene() != null ? getScene().getWindow() : null)),
                PanelAction.of("help-shortcuts", "bi-keyboard", "Keyboard Shortcuts", KeyboardShortcutsDialog::show));
        project.button("help-sponsor").setGraphic(ProjectLinks.sponsorIcon(16));
        // Anonymous in-app problem report — only when error reporting is enabled.
        if (org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog.isAvailable()) {
            project.add(PanelAction.of("help-report-problem", "bi-send", "Report a Problem…",
                    () -> org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog.show(
                            getScene() != null ? getScene().getWindow() : null, null)).color(ActionColor.NAVIGATE));
        }

        // Documentation quick links — open in the system browser (replaces the legacy
        // Help tab's embedded WebViews for the FXT docs, FundsXML site and schema docs).
        PanelActionList docs = new PanelActionList(
                PanelAction.of("help-docs", "bi-book", "Documentation", () -> browse(DOCS_URL)).color(ActionColor.NAVIGATE),
                PanelAction.of("help-fundsxml-site", "bi-globe", "FundsXML Website", () -> browse(FUNDSXML_SITE_URL)).color(ActionColor.NAVIGATE),
                PanelAction.of("help-schema-docs", "bi-file-earmark-text", "FundsXML4 Schema Docs",
                        () -> browse(SCHEMA_DOCS_URL)).color(ActionColor.NAVIGATE));

        PanelActionList updates = new PanelActionList(
                PanelAction.of("help-check-updates", "bi-arrow-clockwise", "Check for Updates", this::checkForUpdates).color(ActionColor.TOOL));
        updateStatus.getStyleClass().addAll("fxt-placeholder-text", "fxt-tp-section-body");
        updateStatus.setWrapText(true);

        getChildren().addAll(title, appName, version, build, vendor,
                PanelActionList.section("PROJECT", false, project),
                PanelActionList.section("DOCUMENTATION", false, docs),
                PanelActionList.section("UPDATES", false, updates), updateStatus);

        // FundsXML extension — only when enabled in the settings (conditional).
        if (FundsXmlActionRunner.isEnabled()) {
            Label fundsStatus = new Label();
            fundsStatus.getStyleClass().addAll("fxt-placeholder-text", "fxt-tp-section-body");
            fundsStatus.setWrapText(true);
            PanelActionList funds = new PanelActionList(
                    PanelAction.of("help-fundsxml-updates", "bi-cloud-arrow-down", "Check FundsXML Updates", () -> {
                        fundsStatus.setText("Checking…");
                        FxtGui.executorService.submit(() -> {
                            String msg = FundsXmlActionRunner.checkForUpdate();
                            Platform.runLater(() -> fundsStatus.setText(msg));
                        });
                    }).color(ActionColor.TOOL));
            getChildren().addAll(PanelActionList.section("FUNDSXML", false, funds), fundsStatus);
        }
    }

    /** Checks for application updates asynchronously and shows the result. */
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
