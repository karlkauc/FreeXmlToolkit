package org.fxt.freexmltoolkit.util;

import java.awt.Desktop;
import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.SemanticColors;

/**
 * Central list of the project's public web links (repository, documentation, issue
 * tracker, GitHub Sponsors) plus a helper to open them in the system browser.
 */
public final class ProjectLinks {

    private static final Logger logger = LogManager.getLogger(ProjectLinks.class);

    /** The GitHub repository. */
    public static final String GITHUB_URL = "https://github.com/karlkauc/FreeXmlToolkit";
    /** The user documentation (MkDocs site). */
    public static final String DOCS_URL = "https://karlkauc.github.io/FreeXmlToolkit";
    /** New GitHub issue form. */
    public static final String ISSUES_URL = GITHUB_URL + "/issues/new";
    /** The maintainer's GitHub Sponsors page. */
    public static final String SPONSORS_URL = "https://github.com/sponsors/karlkauc";

    private ProjectLinks() {
        // constants holder
    }

    /**
     * Creates the heart icon used for every "Sponsor" entry. The colour is bound (not set) so
     * themed {@code -fx-icon-color} CSS rules cannot override it.
     *
     * @param size icon size in pixels
     * @return a new sponsor heart icon
     */
    public static IconifyIcon sponsorIcon(double size) {
        IconifyIcon icon = new IconifyIcon("bi-heart-fill");
        icon.setIconSize(size);
        icon.iconColorProperty().bind(new SimpleObjectProperty<Paint>(Color.web(SemanticColors.SPONSOR)));
        return icon;
    }

    /**
     * Opens {@code url} in the system browser, off the calling thread
     * ({@link Desktop#browse} can block on some platforms). Failures are logged, never thrown.
     *
     * @param url absolute URL to open
     */
    public static void openInBrowser(String url) {
        FxtGui.executorService.submit(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                } else {
                    logger.warn("No desktop browser available to open {}", url);
                }
            } catch (Exception ex) {
                logger.warn("Could not open URL {}: {}", url, ex.getMessage());
            }
        });
    }
}
