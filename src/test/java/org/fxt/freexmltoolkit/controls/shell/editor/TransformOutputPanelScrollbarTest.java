package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.concurrent.TimeUnit;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * Regression test: the OUTPUT panel must show usable scrollbars when the result is
 * larger than the viewport. The "Scrollbar slim styling" blocks in light-theme.css /
 * dark-theme.css blank the Modena arrow buttons (shape " ", padding 0) — the arrows
 * are what gives a ScrollBar its thickness, so without an explicit
 * {@code -fx-pref-width}/{@code -fx-pref-height} every scrollbar collapses to 0 px
 * and becomes invisible (the bug this test pins down).
 */
@ExtendWith(ApplicationExtension.class)
class TransformOutputPanelScrollbarTest {

    private EditorHost host;
    private TransformOutputPanel out;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        out = host.transformOutputPanel();
        HBox root = new HBox(host);
        // The same base sheets the shell declares in tab_unified_shell.fxml/shell.fxml.
        for (String css : new String[]{"/css/design-tokens.css", "/css/app-theme.css",
                "/css/fxt-theme.css", "/css/unified-shell.css"}) {
            root.getStylesheets().add(getClass().getResource(css).toExternalForm());
        }
        Scene scene = new Scene(root, 900, 500);
        stage.setScene(scene);
        ThemeManager.apply(scene, false);
        stage.show();
    }

    @Test
    void outputScrollbarsHaveThicknessInBothThemes() throws Exception {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            big.append("<line n=\"").append(i)
               .append("\" text=\"a very long attribute value forcing horizontal overflow beyond the viewport width of the output panel____________\"/>\n");
        }
        WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            out.showTransformResult(big.toString(), OutputFormat.XML, 5);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> !out.getOutputText().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertScrollbarsUsable("light");

        WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            ThemeManager.apply(out.getScene(), true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertScrollbarsUsable("dark");
    }

    private void assertScrollbarsUsable(String theme) {
        // The scrollbars only get their size on a layout pass after the theme sheet
        // applied; poll the combined condition instead of asserting a single pulse.
        try {
            WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> {
            boolean vertical = false;
            boolean horizontal = false;
            for (Node node : out.lookupAll(".scroll-bar")) {
                ScrollBar bar = (ScrollBar) node;
                if (bar.getParent() == null
                        || !bar.getParent().getClass().getSimpleName().contains("VirtualizedScrollPane")) {
                    continue;
                }
                if (bar.getOrientation() == Orientation.VERTICAL) {
                    vertical = bar.isVisible() && bar.getWidth() > 4;
                } else {
                    horizontal = bar.isVisible() && bar.getHeight() > 4;
                }
            }
                return vertical && horizontal;
            });
        } catch (java.util.concurrent.TimeoutException e) {
            org.junit.jupiter.api.Assertions.fail(theme + " theme: the OUTPUT panel scrollbars"
                    + " are missing or collapsed to zero thickness");
        }
    }
}
