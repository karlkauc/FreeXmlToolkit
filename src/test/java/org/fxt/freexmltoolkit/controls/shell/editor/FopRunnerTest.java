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
    void optionsCarryMetadataIntoThePdf(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        Files.writeString(xsl, XSLT_FO);
        Path pdf = tmp.resolve("out.pdf");

        String result = FopRunner.generate(xml.toFile(), xsl.toFile(), pdf.toFile(),
                new FopRunner.PdfOptions("Fact Sheet 2026", "Erste AM", "Monthly Factsheet",
                        false, "A4", "Portrait"));

        assertTrue(result.startsWith("OK:"), result);
        String raw = new String(Files.readAllBytes(pdf), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("Fact Sheet 2026"), "title metadata must be embedded");
        assertTrue(raw.contains("Monthly Factsheet"), "subject metadata must be embedded");
    }

    @Test
    void pdfACompliantModeProducesAPdfWithEmbeddableFonts(@TempDir Path tmp) throws Exception {
        // PDF/A requires embedded fonts; auto-detection registers the system fonts.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.exists(Path.of("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf")),
                "needs an embeddable system font");
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        Files.writeString(xsl, XSLT_FO.replace("<fo:block>",
                "<fo:block font-family=\"Liberation Sans\">"));
        Path pdf = tmp.resolve("out.pdf");

        String result = FopRunner.generate(xml.toFile(), xsl.toFile(), pdf.toFile(),
                new FopRunner.PdfOptions("t", "a", "s", true, "", ""));

        assertTrue(result.startsWith("OK:"), result);
        String raw = new String(Files.readAllBytes(pdf), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("pdfaid"), "PDF must carry the PDF/A identification schema");
    }

    @Test
    void pdfAModeWithBase14FontsExplainsTheFontRequirement(@TempDir Path tmp) throws Exception {
        // The default Helvetica is a base-14 font that cannot be embedded - the
        // error must surface FOP's explanation instead of failing silently.
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        Files.writeString(xsl, XSLT_FO);
        Path pdf = tmp.resolve("out.pdf");

        String result = FopRunner.generate(xml.toFile(), xsl.toFile(), pdf.toFile(),
                new FopRunner.PdfOptions("t", "a", "s", true, "", ""));

        assertTrue(result.startsWith("ERROR:") && result.contains("embedded"), result);
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
