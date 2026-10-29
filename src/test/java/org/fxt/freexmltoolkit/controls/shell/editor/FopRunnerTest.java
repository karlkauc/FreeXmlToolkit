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

    @Test
    void brokenStylesheetIsClassifiedAsInvalidInputOfTheTransformStage(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("broken.xslt");
        Files.writeString(xsl, "<xsl:not-a-stylesheet/>");

        FopRunner.FopResult result = FopRunner.generateResult(xml.toFile(), xsl.toFile(),
                tmp.resolve("out.pdf").toFile(), FopRunner.PdfOptions.none());

        assertFalse(result.ok());
        assertEquals(FopRunner.STAGE_TRANSFORM, result.stage());
        assertNotNull(result.failure());
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.INVALID_INPUT,
                result.failure().status(), "the user's stylesheet, not an app failure: " + result.failure());
        assertNotNull(result.failure().errorCode(), "the processor's error code is kept");
    }

    @Test
    void foThatFopRejectsIsClassifiedAsInvalidInputOfTheRenderStage(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("not-fo.xslt");
        // A valid stylesheet whose output is not an XSL-FO document (no fo:root)
        Files.writeString(xsl, """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><html><body>not fo</body></html></xsl:template>
                </xsl:stylesheet>
                """);

        FopRunner.FopResult result = FopRunner.generateResult(xml.toFile(), xsl.toFile(),
                tmp.resolve("out.pdf").toFile(), FopRunner.PdfOptions.none());

        assertFalse(result.ok());
        assertEquals(FopRunner.STAGE_RENDER, result.stage());
        assertNotNull(result.foXml(), "the transform stage succeeded");
        assertNotNull(result.failure());
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.INVALID_INPUT,
                result.failure().status(), "FOP rejecting the document is the user's input: " + result.failure());
        assertEquals("ValidationException", result.failure().errorCode());
    }

    @Test
    void successfulRunHasNoFailureAndEndsInTheRenderStage(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        Files.writeString(xsl, XSLT_FO);

        FopRunner.FopResult result = FopRunner.generateResult(xml.toFile(), xsl.toFile(),
                tmp.resolve("out.pdf").toFile(), FopRunner.PdfOptions.none());

        assertTrue(result.ok(), result.error());
        assertEquals(FopRunner.STAGE_RENDER, result.stage());
        assertNull(result.failure());
    }

    @Test
    void structuredResultCarriesTheIntermediateFoMessagesAndEvents(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("to-fo.xslt");
        // A message in the stylesheet and a font FOP has to substitute → one message, ≥ 1 FOP event.
        Files.writeString(xsl, XSLT_FO
                .replace("<xsl:template match=\"/\">", "<xsl:template match=\"/\"><xsl:message>rendering</xsl:message>")
                .replace("<fo:block>", "<fo:block font-family=\"NoSuchFontFamily\">"));
        Path pdf = tmp.resolve("out.pdf");

        FopRunner.FopResult result = FopRunner.generateResult(xml.toFile(), xsl.toFile(), pdf.toFile(),
                FopRunner.PdfOptions.none());

        assertTrue(result.ok(), result.error());
        assertTrue(Files.size(pdf) > 0);
        assertNotNull(result.foXml(), "the intermediate FO is kept");
        assertTrue(result.foXml().contains("fo:root"), result.foXml());
        assertEquals(1, result.messages().size(), "xsl:message captured");
        assertEquals("rendering", result.messages().get(0).getMessage());
        assertTrue(result.events().stream().anyMatch(e -> e.id().contains("fontSubstituted")),
                "FOP's font substitution is reported as an event: " + result.events());
        assertTrue(result.warningCount() >= 1);
        assertTrue(result.notesText().contains("fontSubstituted"));
        assertEquals("OK: " + pdf.toFile().getAbsolutePath(), result.asText());
    }

    @Test
    void stylesheetErrorKeepsTheLocationAndRenderFoWorksStandalone(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        Path xsl = tmp.resolve("bad.xslt");
        Files.writeString(xsl, XSLT_FO.replace("select=\"/doc\"", "select=\"/doc)\""));
        FopRunner.FopResult failed = FopRunner.generateResult(xml.toFile(), xsl.toFile(), tmp.resolve("x.pdf").toFile(),
                FopRunner.PdfOptions.none());
        assertFalse(failed.ok());
        assertNull(failed.foXml(), "no FO when the XSLT stage fails");
        assertTrue(failed.error().contains("Failed to transform"), failed.error());

        Files.writeString(tmp.resolve("to-fo.xslt"), XSLT_FO);
        String fo = FopRunner.generateResult(xml.toFile(), tmp.resolve("to-fo.xslt").toFile(),
                tmp.resolve("y.pdf").toFile(), FopRunner.PdfOptions.none()).foXml();
        assertNotNull(fo);
        Path pdf = tmp.resolve("from-fo.pdf");
        FopRunner.FopResult rendered = FopRunner.renderFo(fo, pdf.toFile(), FopRunner.PdfOptions.none());
        assertTrue(rendered.ok(), rendered.error());
        assertTrue(Files.size(pdf) > 0, "a PDF can be rendered from an FO document alone");
    }
}
