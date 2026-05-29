package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
