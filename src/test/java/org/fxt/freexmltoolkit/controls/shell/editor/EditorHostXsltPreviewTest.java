package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView;

/**
 * TestFX verification of the Explorer's one-click transform entry points on
 * {@link EditorHost}: {@link EditorHost#runXsltPreview} shows a single-file result in the docked
 * output panel and remembers the stylesheet, while {@link EditorHost#openBatchTransform} opens a
 * pre-loaded batch tool tab for several files.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXsltPreviewTest {

    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformOutputPanel out;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        out = host.transformOutputPanel();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void runXsltPreviewShowsResultAndRemembersStylesheet(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Path xslt = tmp.resolve("report.xslt");
        Files.writeString(xml, "<greeting>Hello</greeting>");
        Files.writeString(xslt, XSLT);

        WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.runXsltPreview(xml.toFile(), xslt.toFile()));
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> out.getOutputText().contains("<out>Hello</out>"));

        assertTrue(out.isShowing(), "the output panel must appear after a one-click transform");

        List<java.io.File> recent = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.PropertiesService.class).getRecentXsltFiles();
        assertFalse(recent.isEmpty(), "the stylesheet must be remembered as recent");
        assertEquals(xslt.toFile().getName(), recent.get(0).getName(),
                "the just-used stylesheet must be the most recent (sticky) one");
    }

    @Test
    void openBatchTransformOpensAPreloadedBatchTab(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.xml");
        Path b = tmp.resolve("b.xml");
        Path xslt = tmp.resolve("report.xslt");
        Files.writeString(a, "<greeting>A</greeting>");
        Files.writeString(b, "<greeting>B</greeting>");
        Files.writeString(xslt, XSLT);

        WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.openBatchTransform(List.of(a.toFile(), b.toFile()), xslt.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        BatchTransformView view = (BatchTransformView) host.lookupAll("*").stream()
                .filter(n -> n instanceof BatchTransformView)
                .findFirst().orElse(null);
        assertNotNull(view, "a batch transform tool tab must be opened");
        assertEquals(2, view.getFileCount(), "both selected files must be pre-loaded into the batch");
    }
}
