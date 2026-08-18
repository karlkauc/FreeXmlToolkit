package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the read-only HTML Preview view: HTML documents open rendered in
 * {@link ViewMode#PREVIEW} (backed by a WebView), offer no Tree/Graphic modes,
 * and re-render the current text when switching back from a Text-mode edit.
 * Non-HTML documents never enter Preview (the mode clamps to Text).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostHtmlPreviewTest {

    private static final String HTML = """
            <!DOCTYPE html>
            <html>
            <head><title>Report</title></head>
            <body><h1>Hello Preview</h1></body>
            </html>
            """;

    private static final String XML = "<greeting>Hello</greeting>";

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void htmlFileOpensInPreviewByDefault(@TempDir Path tmp) throws Exception {
        openHtml(tmp);

        assertEquals(ViewMode.PREVIEW, host.activeViewModeProperty().get(),
                "an HTML file must open rendered, in Preview mode");
        if (webViewAvailable()) {
            boolean hasWebView = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                    host.lookupAll("*").stream()
                            .anyMatch(n -> n instanceof javafx.scene.web.WebView));
            assertTrue(hasWebView, "Preview mode must embed a WebView");
        }
    }

    @Test
    void htmlOffersPreviewButNotTreeOrGraphic(@TempDir Path tmp) throws Exception {
        openHtml(tmp);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.PREVIEW)),
                "HTML must offer the Preview view");
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.TREE)),
                "HTML output is often not well-formed XML — no Tree view");
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRAPHIC)),
                "HTML output is often not well-formed XML — no Graphic/grid view");
    }

    @Test
    void nonHtmlDoesNotOfferPreviewAndClampsToText(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("greeting.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.PREVIEW)),
                "XML must not offer the Preview view");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.PREVIEW);
            return null;
        });
        assertEquals(ViewMode.TEXT, host.activeViewModeProperty().get(),
                "requesting Preview for a non-HTML document must clamp to Text");
    }

    @Test
    void previewRefreshesOnSwitchAfterTextEdit(@TempDir Path tmp) throws Exception {
        openHtml(tmp);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> HTML.equals(host.activePreviewedTextForTest()));

        String edited = HTML.replace("Hello Preview", "Edited Content");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            host.activeEditorView().setText(edited);
            host.setActiveViewMode(ViewMode.PREVIEW);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> edited.equals(host.activePreviewedTextForTest()));
    }

    private void openHtml(Path tmp) throws Exception {
        Path html = tmp.resolve("report.html");
        Files.writeString(html, HTML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(html));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Hello Preview")).orElse(false));
    }

    private static boolean webViewAvailable() {
        try {
            Class.forName("javafx.scene.web.WebView");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
