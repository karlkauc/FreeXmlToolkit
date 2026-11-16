package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A generated HTML report opened straight into the Preview is shown without a mode switch - also a large one, whose
 * text reaches the editor only after the switch (built off the FX thread above
 * {@code XmlCodeEditorV2.PREPARE_THRESHOLD_CHARS}).
 */
@ExtendWith(ApplicationExtension.class)
class EditorTabHtmlPreviewTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void aReportOpenedIntoThePreviewIsLoadedAndVisible() throws Exception {
        assertShown("<html><body><h1 id='t'>Check report</h1></body></html>");
    }

    @Test
    void aLargeReportOpenedIntoThePreviewIsShownOnceItsTextArrives() throws Exception {
        StringBuilder rows = new StringBuilder();
        while (rows.length() <= org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2.PREPARE_THRESHOLD_CHARS) {
            rows.append("<tr><td>AT0000A1B2C4</td><td>Beispiel Fonds</td><td>Comm</td><td>12.5</td></tr>\n");
        }
        assertShown("<html><body><h1 id='t'>Check report</h1><table>" + rows + "</table></body></html>");
    }

    private void assertShown(String html) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            host.openGeneratedDocument(html, EditorFileType.HTML, "r.html");
            host.setActiveViewMode(ViewMode.PREVIEW);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            WebView view = webView();
            return view != null && view.isVisible() && view.getEngine().getDocument() != null
                    && view.getEngine().getDocument().getElementById("t") != null;
        }));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, () -> webView().getLayoutBounds().getWidth() > 0));
    }

    private WebView webView() {
        for (Node n : host.lookupAll(".web-view")) {
            if (n instanceof WebView w) {
                return w;
            }
        }
        return null;
    }
}
