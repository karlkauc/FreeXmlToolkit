package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Transform panel's result handling: a successful transform opens the
 * result as a regular editor tab (re-using one tab across runs), the output format
 * is auto-detected from the stylesheet by default, and an HTML result additionally
 * gets a rendered preview tab.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelResultTabTest {

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XML_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;
    private static final String HTML_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="html"/>
              <xsl:template match="/greeting"><html><body><p><xsl:value-of select="."/></p></body></html></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void transformOpensResultAsXmlEditorTabByDefault(@TempDir Path tmp) throws Exception {
        transform(tmp, XML_XSLT);
        OpenDocument result = waitForResultDocument();
        assertEquals("Transform-Result.xml", result.getDisplayName());
        assertEquals(EditorFileType.XML, result.getFileType());
        assertFalse(panel.isHtmlPreviewOpen(), "an XML result must not open an HTML preview");
    }

    @Test
    void autoDetectedHtmlResultGetsHtmlTabAndRenderedPreview(@TempDir Path tmp) throws Exception {
        transform(tmp, HTML_XSLT);
        OpenDocument result = waitForResultDocument();
        assertEquals("Transform-Result.html", result.getDisplayName());
        // The preview tab is created right after the result document (same FX pulse) —
        // poll for it instead of asserting immediately.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, panel::isHtmlPreviewOpen);
        assertTrue(panel.isHtmlPreviewOpen(), "an HTML result must open a rendered preview tab");
    }

    @Test
    void rerunningTheTransformReusesTheResultTab(@TempDir Path tmp) throws Exception {
        transform(tmp, XML_XSLT);
        waitForResultDocument();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(500); // allow the async second run to complete
        WaitForAsyncUtils.waitForFxEvents();
        long resultTabs = host.getOpenDocuments().stream()
                .filter(d -> d.getDisplayName() != null && d.getDisplayName().startsWith("Transform-Result"))
                .count();
        assertEquals(1, resultTabs, "re-running the transform must update the existing result tab");
    }

    @Test
    void failingTransformShowsErrorInPanelAndOpensNoTab(@TempDir Path tmp) throws Exception {
        transform(tmp, "<xsl:not-a-stylesheet/>");
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().startsWith("ERROR"));
        assertTrue(host.getOpenDocuments().stream()
                        .noneMatch(d -> d.getDisplayName() != null
                                && d.getDisplayName().startsWith("Transform-Result")),
                "a failed transform must not open a result tab");
    }

    private OpenDocument waitForResultDocument() throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getDisplayName() != null && d.getDisplayName().startsWith("Transform-Result")));
        return host.getOpenDocuments().stream()
                .filter(d -> d.getDisplayName() != null && d.getDisplayName().startsWith("Transform-Result"))
                .findFirst().orElseThrow();
    }

    private void transform(Path tmp, String xslt) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
        Path sheet = tmp.resolve("t.xslt");
        Files.writeString(sheet, xslt);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.transform();
            return null;
        });
    }
}
