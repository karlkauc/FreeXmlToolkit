package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link XProcRunner} the way the editor does: live pipeline text plus
 * either an in-memory XML target, a file target, or none at all. Pure JUnit —
 * no JavaFX involved.
 */
class XProcRunnerTest {

    private static final String IDENTITY_PIPELINE = """
            <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
              <p:input port="source"/>
              <p:output port="result"/>
              <p:identity/>
            </p:declare-step>
            """;

    private static final String INPUT_XML = "<order id=\"42\"><item>ACME Widget</item></order>";

    @TempDir
    Path tempDir;

    @Test
    void identityPipelineRunsAgainstInMemoryXml() {
        XProcRunner.Result result = XProcRunner.runPipeline(IDENTITY_PIPELINE, null, INPUT_XML, null);
        assertFalse(result.isError(), result.text());
        assertTrue(result.text().contains("ACME Widget"), result.text());
        assertEquals(XsltTransformationEngine.OutputFormat.XML, result.format());
    }

    @Test
    void identityPipelineRunsAgainstFileTarget() throws Exception {
        Path xml = tempDir.resolve("input.xml");
        Files.writeString(xml, INPUT_XML);
        XProcRunner.Result result = XProcRunner.runPipeline(IDENTITY_PIPELINE, null, null, xml.toFile());
        assertFalse(result.isError(), result.text());
        assertTrue(result.text().contains("ACME Widget"), result.text());
    }

    @Test
    void relativeDocumentHrefResolvesAgainstThePipelineFile() throws Exception {
        Files.writeString(tempDir.resolve("data.xml"), "<fund><name>Equity One</name></fund>");
        File pipelineFile = tempDir.resolve("pipeline.xpl").toFile();
        String pipeline = """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:output port="result"/>
                  <p:identity>
                    <p:with-input><p:document href="data.xml"/></p:with-input>
                  </p:identity>
                </p:declare-step>
                """;
        Files.writeString(pipelineFile.toPath(), pipeline);
        XProcRunner.Result result = XProcRunner.runPipeline(pipeline, pipelineFile, null, null);
        assertFalse(result.isError(), result.text());
        assertTrue(result.text().contains("Equity One"), result.text());
    }

    @Test
    void xsltStepWithRelativeStylesheetHrefWorks() throws Exception {
        Files.writeString(tempDir.resolve("upper.xsl"), """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                  <xsl:template match="/">
                    <shouted><xsl:value-of select="upper-case(string(/order/item))"/></shouted>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        File pipelineFile = tempDir.resolve("pipeline.xpl").toFile();
        String pipeline = """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:input port="source"/>
                  <p:output port="result"/>
                  <p:xslt>
                    <p:with-input port="stylesheet"><p:document href="upper.xsl"/></p:with-input>
                  </p:xslt>
                </p:declare-step>
                """;
        Files.writeString(pipelineFile.toPath(), pipeline);
        XProcRunner.Result result = XProcRunner.runPipeline(pipeline, pipelineFile, INPUT_XML, null);
        assertFalse(result.isError(), result.text());
        assertTrue(result.text().contains("ACME WIDGET"), result.text());
    }

    @Test
    void declaredInputWithoutTargetReturnsGuidanceError() {
        XProcRunner.Result result = XProcRunner.runPipeline(IDENTITY_PIPELINE, null, null, null);
        assertTrue(result.isError());
        assertTrue(result.text().contains("Target dropdown"), result.text());
    }

    @Test
    void brokenPipelineReturnsErrorWithXProcCode() {
        String broken = """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:output port="result"/>
                  <p:no-such-step/>
                </p:declare-step>
                """;
        XProcRunner.Result result = XProcRunner.runPipeline(broken, null, null, null);
        assertTrue(result.isError());
        assertTrue(result.text().contains("err:"), "expected an XProc error code, got: " + result.text());
    }

    @Test
    void jsonProducingPipelineIsRoutedAsJson() {
        String pipeline = """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:input port="source"/>
                  <p:output port="result"/>
                  <p:xslt>
                    <p:with-input port="stylesheet">
                      <p:inline expand-text="false">
                        <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                          <xsl:output method="json" build-tree="no"/>
                          <xsl:template match="/">
                            <xsl:sequence select="map { 'item': string(/order/item) }"/>
                          </xsl:template>
                        </xsl:stylesheet>
                      </p:inline>
                    </p:with-input>
                  </p:xslt>
                </p:declare-step>
                """;
        XProcRunner.Result result = XProcRunner.runPipeline(pipeline, null, INPUT_XML, null);
        assertFalse(result.isError(), result.text());
        assertEquals(XsltTransformationEngine.OutputFormat.JSON, result.format());
        assertTrue(result.text().contains("ACME Widget"), result.text());
    }

    @Test
    void selfContainedPipelineRunsWithoutAnyTarget() throws Exception {
        Files.writeString(tempDir.resolve("data.xml"), "<standalone/>");
        File pipelineFile = tempDir.resolve("pipeline.xpl").toFile();
        String pipeline = """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:output port="result"/>
                  <p:identity>
                    <p:with-input><p:document href="data.xml"/></p:with-input>
                  </p:identity>
                </p:declare-step>
                """;
        Files.writeString(pipelineFile.toPath(), pipeline);
        XProcRunner.Result result = XProcRunner.runPipeline(pipeline, pipelineFile, null, null);
        assertFalse(result.isError(), result.text());
        assertTrue(result.text().contains("standalone"), result.text());
    }
}
