package org.fxt.freexmltoolkit.controls.shell.editor;

import java.net.URI;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.VersionUtil;

/**
 * The Help activity side panel: about / version information and quick links.
 * Reuses {@link VersionUtil} for the version (manifest → build-info → fallback).
 */
public class HelpPanel extends VBox {

    private static final String GITHUB_URL = "https://github.com/karlkauc/FreeXmlToolkit";

    private final Label version = new Label();

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

        getChildren().addAll(title, appName, version, build, vendor, github);
    }

    /** @return the version line (for tests/observers). */
    public String getVersionText() {
        return version.getText();
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
