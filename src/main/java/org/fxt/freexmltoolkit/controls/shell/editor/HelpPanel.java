package org.fxt.freexmltoolkit.controls.shell.editor;

import java.net.URI;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.VersionUtil;

/**
 * The Help activity side panel: about / version information and quick links.
 * Reuses {@link VersionUtil} for the version (manifest → build-info → fallback).
 */
public class HelpPanel extends VBox {

    private static final String GITHUB_URL = "https://github.com/karlkauc/FreeXmlToolkit";
    private static final String DOCS_URL = "https://karlkauc.github.io/FreeXmlToolkit";
    private static final String FUNDSXML_SITE_URL = "http://www.fundsxml.org";
    private static final String SCHEMA_DOCS_URL = "https://fundsxml.github.io/";

    private final Label version = new Label();
    private final Label updateStatus = new Label();

    public HelpPanel() {
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("HELP");
        title.getStyleClass().add("fxt-side-panel-title");

        Label appName = new Label("FreeXmlToolkit");
        appName.getStyleClass().add("fxt-welcome-headline");

        version.setText("Version " + VersionUtil.getVersion());
        version.getStyleClass().add("fxt-placeholder-text");

        Label build = new Label("Build: " + VersionUtil.getBuildTimestampFormatted());
        build.getStyleClass().add("fxt-placeholder-text");
        Label vendor = new Label(VersionUtil.getVendor());
        vendor.getStyleClass().add("fxt-placeholder-text");

        Button github = button("GitHub", "bi-github", () -> browse(GITHUB_URL));

        Button aboutBtn = button("About", "bi-info-circle",
                () -> AboutDialog.show(getScene() != null ? getScene().getWindow() : null));
        Button shortcutsBtn = button("Keyboard Shortcuts", "bi-keyboard",
                KeyboardShortcutsDialog::show);

        // Documentation quick links — open in the system browser (replaces the legacy
        // Help tab's embedded WebViews for the FXT docs, FundsXML site and schema docs).
        Label linksTitle = new Label("DOCUMENTATION");
        linksTitle.getStyleClass().add("fxt-side-panel-title");
        Button docs = button("Documentation", "bi-book", () -> browse(DOCS_URL));
        Button fundsSite = button("FundsXML Website", "bi-globe", () -> browse(FUNDSXML_SITE_URL));
        Button schemaDocs = button("FundsXML4 Schema Docs", "bi-file-earmark-text", () -> browse(SCHEMA_DOCS_URL));

        Button checkUpdates = button("Check for Updates", "bi-arrow-clockwise", this::checkForUpdates);
        updateStatus.getStyleClass().add("fxt-placeholder-text");
        updateStatus.setWrapText(true);

        getChildren().addAll(title, appName, version, build, vendor, github,
                aboutBtn, shortcutsBtn,
                linksTitle, docs, fundsSite, schemaDocs,
                checkUpdates, updateStatus);

        // FundsXML extension — only when enabled in the settings (conditional).
        if (FundsXmlActionRunner.isEnabled()) {
            Label fundsTitle = new Label("FUNDSXML");
            fundsTitle.getStyleClass().add("fxt-side-panel-title");
            Label fundsStatus = new Label();
            fundsStatus.getStyleClass().add("fxt-placeholder-text");
            fundsStatus.setWrapText(true);
            Button fundsCheck = button("Check FundsXML Updates", "bi-cloud-arrow-down", () -> {
                fundsStatus.setText("Checking…");
                FxtGui.executorService.submit(() -> {
                    String msg = FundsXmlActionRunner.checkForUpdate();
                    Platform.runLater(() -> fundsStatus.setText(msg));
                });
            });
            getChildren().addAll(fundsTitle, fundsCheck, fundsStatus);
        }
    }

    /** Checks for application updates asynchronously and shows the result. */
    public void checkForUpdates() {
        updateStatus.setText("Checking…");
        UpdateActionRunner.check().whenComplete((info, err) -> Platform.runLater(() ->
                updateStatus.setText(err != null ? "Update check failed." : UpdateActionRunner.describe(info))));
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
     * @return the documentation quick-link URLs offered by the panel (GitHub plus
     *         the FXT docs, FundsXML site and schema docs the legacy Help tab embedded).
     */
    public List<String> getQuickLinkUrls() {
        return List.of(GITHUB_URL, DOCS_URL, FUNDSXML_SITE_URL, SCHEMA_DOCS_URL);
    }

    private void browse(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
            // no desktop browser available
        }
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
