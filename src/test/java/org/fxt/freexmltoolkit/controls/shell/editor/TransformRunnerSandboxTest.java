package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.SandboxedTransformation;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sandboxed transformation used by the AI tools: a stylesheet written by a model or an external agent
 * cannot write files, call Java, reach the network, read outside the allowed folders, read environment
 * variables, or run forever.
 */
class TransformRunnerSandboxTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String XML = "<root><item>1</item><item>2</item></root>";

    @TempDir
    Path temp;

    private Path root;
    private Path outside;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(root.resolve("inside.xml"), "<data>inside</data>");
        Files.writeString(outside.resolve("secret.xml"), "<data>secret</data>");
        Files.writeString(outside.resolve("secret.txt"), "secret text");
    }

    private static String stylesheet(String body) {
        return """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                xmlns:f="urn:f" exclude-result-prefixes="#all">
                  <xsl:output method="xml" omit-xml-declaration="yes"/>
                %s
                </xsl:stylesheet>
                """.formatted(body);
    }

    private SandboxedTransformation run(String xslt) {
        return run(xslt, null, TIMEOUT);
    }

    private SandboxedTransformation run(String xslt, String xsltSystemId, Duration timeout) {
        return TransformRunner.xsltTransformSandboxed(XML, null, xslt, xsltSystemId, Map.of(), OutputFormat.XML,
                timeout, List.of(root));
    }

    private static List<Path> filesBelow(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    @Test
    void plainTransformationWorks() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/"><sum><xsl:value-of select="sum(//item)"/></sum></xsl:template>"""));
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        assertEquals("<sum>3</sum>", run.result().getOutputContent().strip());
        assertFalse(run.timedOut());
    }

    @Test
    void resultDocumentWithAnAbsolutePathStaysInMemory() {
        Path probe = temp.resolve("fxt-sandbox-probe.xml");
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/">
                  <xsl:result-document href="%s"><written>yes</written></xsl:result-document>
                  <main/>
                </xsl:template>""".formatted(probe.toUri())));
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        assertFalse(Files.exists(probe), "the sandbox wrote a file");
        String key = run.resultDocuments().keySet().stream().filter(k -> k.endsWith("fxt-sandbox-probe.xml"))
                .findFirst().orElse(null);
        assertNotNull(key, run.resultDocuments().toString());
        assertTrue(run.resultDocuments().get(key).contains("<written>yes</written>"));
    }

    @Test
    void resultDocumentWithARelativePathStaysInMemory() throws IOException {
        Path stylesheetFile = root.resolve("sheet.xsl");
        String xslt = stylesheet("""
                <xsl:template match="/">
                  <xsl:result-document href="out/part.xml"><part/></xsl:result-document>
                  <xsl:result-document href="../escape.xml"><escape/></xsl:result-document>
                  <main/>
                </xsl:template>""");
        Files.writeString(stylesheetFile, xslt);
        List<Path> before = filesBelow(temp);
        SandboxedTransformation run = run(xslt, stylesheetFile.toUri().toString(), TIMEOUT);
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        assertEquals(before, filesBelow(temp), "the sandbox created files");
        assertEquals(2, run.resultDocuments().size(), run.resultDocuments().toString());
    }

    @Test
    void javaExtensionsStayOff() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/" xmlns:sys="java:java.lang.System">
                  <home><xsl:value-of select="sys:getProperty('user.home')"/></home>
                </xsl:template>"""));
        assertFalse(run.result().isSuccess(), run.result().getOutputContent());
    }

    @Test
    void remoteDocumentsAreBlocked() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/"><x><xsl:copy-of select="doc('https://example.invalid/x.xml')"/></x></xsl:template>"""));
        assertFalse(run.result().isSuccess());
    }

    @Test
    void localDocumentsOnlyInsideTheRoots() {
        SandboxedTransformation inside = run(stylesheet("""
                <xsl:template match="/"><x><xsl:value-of select="doc('%s')"/></x></xsl:template>"""
                .formatted(root.resolve("inside.xml").toUri())));
        assertTrue(inside.result().isSuccess(), inside.result().getErrorMessage());
        assertTrue(inside.result().getOutputContent().contains("inside"));

        SandboxedTransformation doc = run(stylesheet("""
                <xsl:template match="/"><x><xsl:value-of select="doc('%s')"/></x></xsl:template>"""
                .formatted(outside.resolve("secret.xml").toUri())));
        assertFalse(doc.result().isSuccess());
        assertFalse(doc.result().getOutputContent().contains("secret"));

        SandboxedTransformation text = run(stylesheet("""
                <xsl:template match="/"><x><xsl:value-of select="unparsed-text('%s')"/></x></xsl:template>"""
                .formatted(outside.resolve("secret.txt").toUri())));
        assertFalse(text.result().isSuccess());

        SandboxedTransformation available = run(stylesheet("""
                <xsl:template match="/"><x><xsl:value-of select="unparsed-text-available('%s')"/></x></xsl:template>"""
                .formatted(outside.resolve("secret.txt").toUri())));
        assertFalse(available.result().isSuccess() && available.result().getOutputContent().contains("true"));

        SandboxedTransformation collection = run(stylesheet("""
                <xsl:template match="/"><x><xsl:value-of select="count(collection('%s'))"/></x></xsl:template>"""
                .formatted(outside.toUri())));
        assertFalse(collection.result().isSuccess(), collection.result().getOutputContent());
    }

    @Test
    void includesOnlyInsideTheRoots() throws IOException {
        Files.writeString(outside.resolve("mod.xsl"), stylesheet("<xsl:variable name=\"v\" select=\"1\"/>"));
        SandboxedTransformation run = run(stylesheet("""
                <xsl:include href="%s"/>
                <xsl:template match="/"><x/></xsl:template>""".formatted(outside.resolve("mod.xsl").toUri())));
        assertFalse(run.result().isSuccess());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, run.result().getErrorPhase());
    }

    @Test
    void environmentVariablesAreHidden() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/">
                  <env count="{count(available-environment-variables())}"
                       path="{environment-variable('PATH')}"/>
                </xsl:template>"""));
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        assertTrue(run.result().getOutputContent().contains("count=\"0\""), run.result().getOutputContent());
        assertTrue(run.result().getOutputContent().contains("path=\"\""), run.result().getOutputContent());
    }

    @Test
    void javaSystemPropertiesAreHidden() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/">
                  <xsl:variable name="name" select="string-join(('user', 'home'), '.')"/>
                  <p literal="{system-property('user.home')}" dynamic="{system-property($name)}"
                     version="{system-property('xsl:version')}"/>
                </xsl:template>"""));
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        String out = run.result().getOutputContent();
        assertFalse(out.contains(System.getProperty("user.home")), out);
        assertTrue(out.contains("version=\"3.0\""), out);
    }

    @Test
    void outputIsCapped() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/">
                  <x><xsl:value-of select="string-join((1 to 3000000) ! 'xxxxxxx')"/></x>
                </xsl:template>"""));
        assertFalse(run.result().isSuccess());
        assertTrue(run.result().getErrorMessage().contains("characters of output"), run.result().getErrorMessage());
    }

    @Test
    void anEndlessIterateTimesOut() {
        assertTimesOut(stylesheet("""
                <xsl:template match="/">
                  <xsl:for-each select="1 to 2000000000">
                    <xsl:iterate select="1 to 2000000000">
                      <xsl:param name="n" select="0" as="xs:integer"/>
                      <xsl:next-iteration><xsl:with-param name="n" select="$n + 1"/></xsl:next-iteration>
                    </xsl:iterate>
                  </xsl:for-each>
                </xsl:template>"""));
    }

    @Test
    void anEndlessRecursiveTemplateEndsWithAnError() {
        assertEndsSafely(stylesheet("""
                <xsl:template match="/"><xsl:call-template name="loop"/></xsl:template>
                <xsl:template name="loop">
                  <xsl:param name="n" select="0" as="xs:integer"/>
                  <xsl:call-template name="loop"><xsl:with-param name="n" select="$n + 1"/></xsl:call-template>
                </xsl:template>"""));
    }

    @Test
    void anEndlessRecursiveFunctionEndsWithAnError() {
        assertEndsSafely(stylesheet("""
                <xsl:function name="f:loop" as="xs:integer">
                  <xsl:param name="n" as="xs:integer"/>
                  <xsl:sequence select="f:loop($n + 1)"/>
                </xsl:function>
                <xsl:template match="/"><x><xsl:value-of select="f:loop(0)"/></x></xsl:template>"""));
    }

    /** Endless recursion runs out of stack before the deadline (Saxon: SXLM0001); either end is safe. */
    private void assertEndsSafely(String xslt) {
        long start = System.nanoTime();
        SandboxedTransformation run = run(xslt, null, Duration.ofSeconds(5));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertFalse(run.result().isSuccess());
        assertNotNull(run.result().getErrorMessage());
        assertTrue(millis < 15_000, "returned after " + millis + " ms");
    }

    private void assertTimesOut(String xslt) {
        long start = System.nanoTime();
        SandboxedTransformation run = run(xslt, null, Duration.ofMillis(500));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(run.timedOut(), "not timed out: " + run.result().getErrorMessage());
        assertFalse(run.result().isSuccess());
        assertTrue(millis < 10_000, "returned after " + millis + " ms");
    }

    @Test
    void messagesAreReported() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/"><xsl:message>hello from the stylesheet</xsl:message><x/></xsl:template>"""));
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        assertTrue(run.result().getMessages().stream().anyMatch(m -> m.getMessage().contains("hello from the stylesheet")),
                String.valueOf(run.result().getMessages()));
    }

    @Test
    void compileErrorCarriesCodePhaseAndLine() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/">
                  <xsl:no-such-instruction/>
                </xsl:template>"""));
        XsltTransformationResult result = run.result();
        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, result.getErrorPhase());
        assertNotNull(result.getErrorCode());
        assertTrue(result.getErrorCode().startsWith("XTSE"), result.getErrorCode());
        assertEquals(6, result.getErrorLine(), result.getErrorMessage());
    }

    @Test
    void runtimeErrorCarriesCodePhaseAndLine() {
        SandboxedTransformation run = run(stylesheet("""
                <xsl:template match="/">
                  <x><xsl:value-of select="error(xs:QName('f:BOOM'), 'boom')"/></x>
                </xsl:template>"""));
        XsltTransformationResult result = run.result();
        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_RUNTIME, result.getErrorPhase());
        assertEquals("BOOM", result.getErrorCode());
        assertEquals(6, result.getErrorLine(), result.getErrorMessage());
    }

    @Test
    void malformedInputIsAnInputFailure() {
        SandboxedTransformation run = TransformRunner.xsltTransformSandboxed("<root>", null,
                stylesheet("<xsl:template match=\"/\"><x/></xsl:template>"), null, Map.of(), OutputFormat.XML,
                TIMEOUT, List.of(root));
        assertEquals(XsltTransformationResult.PHASE_INPUT, run.result().getErrorPhase());
    }
}
