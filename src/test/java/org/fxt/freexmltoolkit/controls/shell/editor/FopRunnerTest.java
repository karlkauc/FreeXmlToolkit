package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link FopRunner} end-to-end (no UI): an XML + an XSLT producing XSL-FO
 * yields a non-empty PDF; a broken stylesheet yields an {@code ERROR:} message.
 */
class FopRunnerTest {

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
                    <fo:flow flow-name="xsl-region-body">
                      <fo:block><xsl:value-of select="/doc"/></fo:block>
                    </fo:flow>
                  </fo:page-sequence>
                </fo:root>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void generatesPdfFromXmlAndFoStylesheet(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        Files.writeString(xsl, XSLT_FO);
        Path pdf = tmp.resolve("out.pdf");

        String result = FopRunner.generate(xml.toFile(), xsl.toFile(), pdf.toFile());

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(Files.exists(pdf), "PDF must be created");
        assertTrue(Files.size(pdf) > 0, "PDF must not be empty");
    }

    @Test
    void brokenStylesheetReturnsError(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("broken.xslt");
        Files.writeString(xsl, "<xsl:not-a-stylesheet/>");
        Path pdf = tmp.resolve("out.pdf");

        assertTrue(FopRunner.generate(xml.toFile(), xsl.toFile(), pdf.toFile()).startsWith("ERROR:"));
    }
}
