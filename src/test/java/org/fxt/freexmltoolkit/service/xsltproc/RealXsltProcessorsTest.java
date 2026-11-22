package org.fxt.freexmltoolkit.service.xsltproc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.fo.FakeProcessors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The presets against real programs: Saxon's command line (Saxon HE from the test class path stands in for
 * PE/EE - same command line) always, Saxon HE 13 (jars from the Gradle configuration {@code saxon13}, run
 * with the JDK's {@code java}) when Gradle hands them over, xsltproc when it is installed.
 */
class RealXsltProcessorsTest {

    @TempDir
    Path dir;

    private static final String XSL = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:param name="title" select="'none'"/>
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:message>working</xsl:message><xsl:value-of select="concat($title, ':', count(//i))"/></xsl:template>
            </xsl:stylesheet>
            """;

    private ExternalXsltRunner.Request request(String xsl) {
        return new ExternalXsltRunner.Request("<r><i/><i/></r>", dir.resolve("in.xml"), xsl, dir.resolve("s.xsl"),
                Map.of("title", "Q4 total"), null);
    }

    @Test
    void saxonCommandLine() throws Exception {
        String arguments = XsltProcessorPreset.SAXON.arguments()
                .replace("\"saxon-pe-12.jar\"", "\"" + System.getProperty("java.class.path") + "\"");
        XsltProcessorConfig saxon = new XsltProcessorConfig(null, "Saxon", XsltProcessorPreset.SAXON, FakeProcessors.java(),
                arguments, null, "", 120);
        XsltTransformationResult ok = ExternalXsltRunner.transform(saxon, request(XSL));
        assertTrue(ok.isSuccess(), ok.getErrorMessage());
        assertEquals("Q4 total:2", ok.getOutputContent().strip());
        assertTrue(ok.getMessages().stream().anyMatch(m -> m.getMessage().contains("working")), String.valueOf(ok.getMessages()));

        XsltTransformationResult bad = ExternalXsltRunner.transform(saxon, request(XSL.replace("count(//i)", "count($nope)")));
        assertFalse(bad.isSuccess());
        assertEquals("XPST0008", bad.getErrorCode(), bad.getErrorMessage());
        assertEquals(4, bad.getErrorLine(), bad.getErrorMessage());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, bad.getErrorPhase(), bad.getErrorMessage());
    }

    @Test
    void saxonHe13InItsOwnProcess() throws Exception {
        Path saxon = SaxonInstallerTest.saxon13Jar("Saxon-HE-13.1.jar");
        String classpath = saxon + java.io.File.pathSeparator + SaxonInstallerTest.saxon13Jar("xmlresolver-6.1.0.jar");
        XsltProcessorConfig config = new XsltProcessorConfig(null, SaxonInstaller.PROCESSOR_NAME,
                XsltProcessorPreset.SAXON_HE, "", SaxonInstaller.arguments(classpath), null, "", 120);
        assertTrue(config.resolveExecutable().isPresent(), "the JDK's java");

        XsltTransformationResult ok = ExternalXsltRunner.transform(config, request(XSL));
        assertTrue(ok.isSuccess(), ok.getErrorMessage());
        assertEquals("Q4 total:2", ok.getOutputContent().strip());
        assertTrue(ok.getMessages().stream().anyMatch(m -> m.getMessage().contains("working")), String.valueOf(ok.getMessages()));

        XsltTransformationResult version = ExternalXsltRunner.transform(config, request(XSL.replace(
                "concat($title, ':', count(//i))", "system-property('xsl:product-version')")));
        assertTrue(version.isSuccess(), version.getErrorMessage());
        assertEquals("HE 13.1", version.getOutputContent().strip(), "not the bundled Saxon HE 12");

        XsltTransformationResult bad = ExternalXsltRunner.transform(config, request(XSL.replace("count(//i)", "count($nope)")));
        assertFalse(bad.isSuccess());
        assertEquals("XPST0008", bad.getErrorCode(), bad.getErrorMessage());
        assertEquals(4, bad.getErrorLine(), bad.getErrorMessage());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, bad.getErrorPhase(), bad.getErrorMessage());

        XsltTransformationResult failing = ExternalXsltRunner.transform(config, request(XSL.replace(
                "count(//i)", "error(xs:QName('FXT0001'), 'stop')").replace("<xsl:stylesheet ",
                "<xsl:stylesheet xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" ")));
        assertFalse(failing.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_RUNTIME, failing.getErrorPhase(), failing.getErrorMessage());
        assertTrue(failing.getErrorMessage().contains("stop"), failing.getErrorMessage());
    }

    @Test
    void xsltproc() throws Exception {
        XsltProcessorConfig config = XsltProcessorConfig.fromPreset(XsltProcessorPreset.XSLTPROC);
        assumeTrue(config.resolveExecutable().isPresent(), "xsltproc is not installed");
        XsltTransformationResult ok = ExternalXsltRunner.transform(config, request(XSL));
        assertTrue(ok.isSuccess(), ok.getErrorMessage());
        assertEquals("Q4 total:2", ok.getOutputContent().strip());

        XsltTransformationResult bad = ExternalXsltRunner.transform(config, request(XSL.replace("count(//i)", "count($nope)")));
        assertFalse(bad.isSuccess());
        assertTrue(bad.getErrorMessage().toLowerCase().contains("variable"), bad.getErrorMessage());
    }
}
