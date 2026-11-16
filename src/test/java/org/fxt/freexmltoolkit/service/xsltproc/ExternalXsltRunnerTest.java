package org.fxt.freexmltoolkit.service.xsltproc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.fo.FakeProcessors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An external XSLT processor run: result, parameters, texts next to their files, errors, time limit. */
public class ExternalXsltRunnerTest {

    @TempDir
    Path dir;

    public static String classpath() {
        try {
            return Path.of(FakeXsltProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return a processor that starts {@link FakeXsltProcessor} in {@code mode} with the running JVM */
    public static XsltProcessorConfig fake(String mode, int timeoutSeconds) {
        return new XsltProcessorConfig(null, "Fake " + mode, XsltProcessorPreset.CUSTOM, FakeProcessors.java(),
                "--enable-preview -cp \"" + classpath() + "\" " + FakeXsltProcessor.class.getName() + " " + mode
                        + " ${xsl} ${xml} ${out} ${params}", "{name}={value}", "", timeoutSeconds);
    }

    private static final String XSL = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:include href="part.xsl"/>
              <xsl:param name="greeting" select="'none'"/>
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="$greeting"/>, <xsl:call-template name="who"/></xsl:template>
            </xsl:stylesheet>
            """;

    private ExternalXsltRunner.Request request(Map<String, ?> params) throws Exception {
        Files.writeString(dir.resolve("part.xsl"), """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template name="who"><xsl:value-of select="/who"/></xsl:template>
                </xsl:stylesheet>
                """);
        return new ExternalXsltRunner.Request("<who>Wörld</who>", dir.resolve("input.xml"), XSL, dir.resolve("main.xsl"),
                params, null);
    }

    private List<Path> leftovers() throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith(".fxt-xslt-")).toList();
        }
    }

    @Test
    void unsavedTextsRunNextToTheirFilesWithParameters() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("greeting", "Hello there");
        XsltTransformationResult result = ExternalXsltRunner.transform(fake("ok", 60), request(params));
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("Hello there, Wörld", result.getOutputContent().strip(), "include resolved, parameter passed");
        assertEquals("Message: started", result.getMessages().getFirst().getMessage());
        assertEquals(List.of(), leftovers(), "temporary files are removed");
    }

    @Test
    void saxonAndXsltprocErrorsCarryTheirLine() throws Exception {
        XsltTransformationResult saxon = ExternalXsltRunner.transform(fake("saxonerror", 60), request(Map.of()));
        assertFalse(saxon.isSuccess());
        assertEquals(7, saxon.getErrorLine());
        assertEquals("XPST0008", saxon.getErrorCode());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, saxon.getErrorPhase());
        assertTrue(saxon.getErrorMessage().contains("failed (exit code 2)"), saxon.getErrorMessage());

        XsltTransformationResult xsltproc = ExternalXsltRunner.transform(fake("xsltprocerror", 60), request(Map.of()));
        assertEquals(5, xsltproc.getErrorLine());
        assertEquals(XsltTransformationResult.PHASE_RUNTIME, xsltproc.getErrorPhase());
        assertTrue(xsltproc.getErrorMessage().contains("Undefined variable"), xsltproc.getErrorMessage());
    }

    @Test
    void noResultTimeoutAndMissingProgram() throws Exception {
        assertTrue(ExternalXsltRunner.transform(fake("nooutput", 60), request(Map.of())).getErrorMessage()
                .contains("without writing a result"));
        XsltTransformationResult hang = ExternalXsltRunner.transform(fake("hang", 1), request(Map.of()));
        assertTrue(hang.getErrorMessage().contains("did not finish within 1 seconds"), hang.getErrorMessage());
        XsltProcessorConfig missing = new XsltProcessorConfig(null, "Missing", XsltProcessorPreset.CUSTOM,
                dir.resolve("no-such-program").toString(), null, null, "", 0);
        assertTrue(ExternalXsltRunner.transform(missing, request(Map.of())).getErrorMessage().contains("program not found"));
        XsltProcessorConfig noOut = new XsltProcessorConfig(null, "Bad", XsltProcessorPreset.CUSTOM, "x", "${xsl}", null, "", 0);
        assertTrue(ExternalXsltRunner.transform(noOut, request(Map.of())).getErrorMessage().contains("${out}"));
        assertEquals(List.of(), leftovers());
    }

    @Test
    void resultEncodingFollowsTheDeclaration() throws Exception {
        Path latin = dir.resolve("latin.out");
        Files.write(latin, "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>ä</a>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        assertTrue(ExternalXsltRunner.read(latin).contains("<a>ä</a>"));
    }
}
