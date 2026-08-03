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
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;

/**
 * Regression test: the text editor's vertical scrollbar thumb must stay grabbable for
 * very large documents. {@code ScrollBarSkin} clamps the thumb to
 * {@code minThumbLength() = 1.5 * getBreadth()}, and {@code getBreadth()} is derived from
 * the increment/decrement arrow buttons — which the slim-styling CSS blanks with
 * {@code -fx-padding: 0}. That made the minimum thumb length 0, so for a 35k-line file
 * the proportional thumb (viewport/total &lt; 0.1%) collapsed to ~1 px and became
 * invisible (the bug this test pins down). The fix gives the blanked buttons cross-axis
 * padding only, restoring the breadth without consuming track space.
 */
@ExtendWith(ApplicationExtension.class)
class XmlEditorScrollbarThumbTest {

    /** A thumb thinner than this is effectively invisible and not grabbable. */
    private static final double MIN_USABLE_THUMB_PX = 8;

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        HBox root = new HBox(editor);
        editor.prefWidthProperty().bind(root.widthProperty());
        editor.prefHeightProperty().bind(root.heightProperty());
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
    void verticalThumbStaysGrabbableForHugeDocumentInBothThemes() throws Exception {
        StringBuilder big = new StringBuilder("<root>\n");
        for (int i = 0; i < 35000; i++) {
            big.append("  <line n=\"").append(i).append("\">value</line>\n");
        }
        big.append("</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(10000, () -> {
            editor.setText(big.toString());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertVerticalThumbUsable("light");

        WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            ThemeManager.apply(editor.getScene(), true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertVerticalThumbUsable("dark");
    }

    private void assertVerticalThumbUsable(String theme) {
        // Thumb metrics settle on a layout pass after the theme sheet applied; poll the
        // combined condition instead of asserting a single pulse.
        try {
            WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> {
                for (Node node : editor.lookupAll(".scroll-bar")) {
                    ScrollBar bar = (ScrollBar) node;
                    if (bar.getOrientation() != Orientation.VERTICAL
                            || bar.getParent() == null
                            || !bar.getParent().getClass().getSimpleName().contains("VirtualizedScrollPane")) {
                        continue;
                    }
                    Node thumb = bar.lookup(".thumb");
                    if (bar.isVisible() && bar.getWidth() > 4 && thumb != null
                            && thumb.getBoundsInParent().getHeight() >= MIN_USABLE_THUMB_PX) {
                        return true;
                    }
                }
                return false;
            });
        } catch (java.util.concurrent.TimeoutException e) {
            org.junit.jupiter.api.Assertions.fail(theme + " theme: the editor's vertical"
                    + " scrollbar thumb is missing or collapsed below " + MIN_USABLE_THUMB_PX
                    + " px for a huge document");
        }
    }
}
