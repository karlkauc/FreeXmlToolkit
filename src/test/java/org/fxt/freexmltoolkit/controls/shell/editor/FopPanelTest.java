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
 * TestFX verification that the PDF/FOP panel renders the active XML to a PDF via
 * the chosen XSL stylesheet (off the UI thread).
 */
@ExtendWith(ApplicationExtension.class)
class FopPanelTest {

    private static final String XML = "<doc>Hello PDF</doc>";
    private static final String XSLT_FO = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <xsl:template match="/">
                <fo:root>
                  <fo:layout-master-set>
                    <fo:simple-page-master master-name="p" page-height="297mm" page-width="210mm">
                      <fo:region-body/>
                    </fo:simple-page-master>
                  </fo:layout-master-set>
                  <fo:page-sequence master-reference="p">
                    <fo:flow flow-name="xsl-region-body"><fo:block><xsl:value-of select="/doc"/></fo:block></fo:flow>
                  </fo:page-sequence>
                </fo:root>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private FopPanel panel;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        panel = new FopPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @Test
    void titleFollowsTheSharedSidePanelConvention() {
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.control.Label title =
                (javafx.scene.control.Label) panel.lookup(".fxt-side-panel-title");
        assertNotNull(title, "panel must keep the shared side-panel title class");
        assertEquals("PDF / FOP", title.getText());
    }

    @Test
    void exposesInputMetadataAndOptionsSections(@TempDir Path tmp) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(panel.lookup("#fop-xml-name"), "INPUT must show the XML source");
        assertNotNull(panel.lookup("#fop-xsl-name"), "INPUT must show the XSL source");
        assertNotNull(panel.lookup("#fop-meta-title"), "METADATA must offer a title field");
        assertNotNull(panel.lookup("#fop-meta-author"), "METADATA must offer an author field");
        assertNotNull(panel.lookup("#fop-meta-subject"), "METADATA must offer a subject field");
        assertNotNull(panel.lookup("#fop-pdfa"), "OPTIONS must offer the PDF/A toggle");
        assertNotNull(panel.lookup("#fop-page-size"), "OPTIONS must offer the page size");
        assertNotNull(panel.lookup("#fop-generate"), "the primary Generate PDF button must exist");

        Path xsl = tmp.resolve("sheet.xsl");
        Files.writeString(xsl, XSLT_FO);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXslFile(xsl.toFile());
            return null;
        });
        javafx.scene.control.Label xslName = (javafx.scene.control.Label) panel.lookup("#fop-xsl-name");
        assertEquals("sheet.xsl", xslName.getText());
    }

    @Test
    void metadataFieldsFlowIntoTheGenerationOptions() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setMetadata("Fact Sheet", "Erste AM", "Monthly");
            return null;
        });
        FopRunner.PdfOptions options = panel.currentOptions();
        assertEquals("Fact Sheet", options.title());
        assertEquals("Erste AM", options.author());
        assertEquals("Monthly", options.subject());
        assertFalse(options.pdfACompliant(), "PDF/A must be off by default");
        assertEquals("A4", options.pageSize());
        assertEquals("Portrait", options.orientation());
    }

    @Test
    void xmlInputFollowsTheActiveEditorAndSupportsOverride(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> {
            javafx.scene.control.Label name = (javafx.scene.control.Label) panel.lookup("#fop-xml-name");
            return name != null && "doc.xml".equals(name.getText());
        });

        Path other = tmp.resolve("other.xml");
        Files.writeString(other, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXmlOverride(other.toFile());
            return null;
        });
        javafx.scene.control.Label name = (javafx.scene.control.Label) panel.lookup("#fop-xml-name");
        assertEquals("other.xml", name.getText(), "the override must win over the active editor");
    }

    @Test
    void generatesPdfFromActiveXml(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        Files.writeString(xsl, XSLT_FO);
        Path pdf = tmp.resolve("out.pdf");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("doc")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXslFile(xsl.toFile());
            panel.generateTo(pdf.toFile());
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> panel.getStatusText().startsWith("Generated"));

        assertTrue(panel.getStatusText().startsWith("Generated"), panel.getStatusText());
        assertTrue(Files.exists(pdf) && Files.size(pdf) > 0, "PDF must be created");
    }
}
