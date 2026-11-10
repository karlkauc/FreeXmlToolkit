package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.fo.FakeProcessors;
import org.fxt.freexmltoolkit.service.fo.FoProcessorConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link FopRunner} with an external XSL-FO processor (a fake one started with the running JVM). */
class FopRunnerExternalProcessorTest {

    private static final String XSLT_FO = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <xsl:template match="/">
                <xsl:message>rendering</xsl:message>
                <fo:root><fo:layout-master-set/></fo:root>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @AfterEach
    void restoreLookup() {
        FopRunner.setProcessorLookup(null);
    }

    private static void useProcessors(FoProcessorConfig... processors) {
        FopRunner.setProcessorLookup(idOrName -> {
            for (FoProcessorConfig p : processors) {
                if (p.matches(idOrName)) {
                    return Optional.of(p);
                }
            }
            throw new IllegalArgumentException("No XSL-FO processor named \"" + idOrName + "\" is configured.");
        });
    }

    @Test
    void theXsltStageStaysInternalAndTheExternalProcessorRenders(@TempDir Path tmp) throws Exception {
        useProcessors(FakeProcessors.config("Fake XEP", "ok", "${xml} ${xsl}", 60));
        Path xml = Files.writeString(tmp.resolve("doc.xml"), "<doc/>");
        Path xsl = Files.writeString(tmp.resolve("to-fo.xsl"), XSLT_FO);
        Path pdf = tmp.resolve("out.pdf");

        FopRunner.FopResult result = FopRunner.generateResult(xml.toFile(), xsl.toFile(), pdf.toFile(),
                FopRunner.PdfOptions.none().withProcessor("fake xep"));

        assertTrue(result.ok(), result.error());
        assertEquals("rendering", result.messages().get(0).getMessage(), "xsl:message of the internal XSLT stage");
        assertNotNull(result.foXml());
        String written = Files.readString(pdf);
        assertTrue(written.contains(xml.toAbsolutePath().toString()) && written.contains(xsl.toAbsolutePath().toString()),
                written);
        assertEquals(1, result.warningCount());
        assertTrue(result.notesText().contains("substituted"));
    }

    @Test
    void aFailingProcessorKeepsTheFoAndTheOutput(@TempDir Path tmp) {
        useProcessors(FakeProcessors.config("Fake", "fail", null, 60));
        FopRunner.FopResult result = FopRunner.renderFo("<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"/>",
                tmp.resolve("x.pdf").toFile(), FopRunner.PdfOptions.none().withProcessor("Fake"));
        assertFalse(result.ok());
        assertEquals(FopRunner.STAGE_RENDER, result.stage());
        assertNotNull(result.foXml());
        assertTrue(result.error().contains("exit code 3"), result.error());
        assertFalse(result.events().isEmpty(), "the program's output is kept for the messages view");
    }

    @Test
    void anUnknownProcessorIsAFailureNotAnException(@TempDir Path tmp) {
        useProcessors();
        FopRunner.FopResult result = FopRunner.renderFo("<x/>", tmp.resolve("x.pdf").toFile(),
                FopRunner.PdfOptions.none().withProcessor("Antenna"));
        assertFalse(result.ok());
        assertTrue(result.error().contains("Antenna"), result.error());
    }

    @Test
    void builtinNeverAsksForAProcessor() {
        assertTrue(FopRunner.isBuiltin(null));
        assertTrue(FopRunner.isBuiltin(" BUILTIN "));
        assertFalse(FopRunner.isBuiltin("XEP"));
    }
}
