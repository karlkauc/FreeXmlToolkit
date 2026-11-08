package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Runs XSpec tests (<a href="https://github.com/xspec/xspec">XSpec</a> 3, bundled) for XSLT,
 * XQuery and Schematron with Saxon, in process: the {@code .xspec} file is compiled by XSpec's own
 * stylesheets into a test stylesheet or query, which runs and writes XSpec's XML report; the report
 * is read into {@link Scenario}s and can be formatted as XSpec's HTML report or as JUnit XML.
 * UI-free; used by the XSpec tool tab and {@code fxt-cli xspec}.
 */
public final class XSpecRunner {

    /** XSpec's namespace. */
    public static final String XSPEC_NS = "http://www.jenitennison.com/xslt/xspec";
    private static final String BASE = "/io/xspec/xspec/impl/src/";

    /** What is tested. */
    public enum Kind { XSLT, XQUERY, SCHEMATRON }

    /** The state of a test. */
    public enum Status { PASSED, FAILED, PENDING }

    /**
     * One {@code x:expect}.
     *
     * @param expected the expected result, serialized (empty when the test is a boolean {@code test})
     * @param actual   the actual result of the scenario, serialized
     */
    public record Test(String label, Status status, String expected, String actual) {
    }

    /** A scenario with its tests and nested scenarios. */
    public record Scenario(String label, boolean pending, List<Test> tests, List<Scenario> scenarios) {

        /** @return the tests of this scenario and all nested ones */
        public List<Test> allTests() {
            List<Test> all = new ArrayList<>(tests);
            scenarios.forEach(s -> all.addAll(s.allTests()));
            return all;
        }
    }

    /**
     * The result of a run.
     *
     * @param kind      what was tested
     * @param scenarios the top-level scenarios
     * @param reportXml XSpec's XML report
     * @param error     why the tests did not run (compile error, …), or {@code null}
     */
    public record Result(Kind kind, List<Scenario> scenarios, String reportXml, String error) {

        public List<Test> tests() {
            List<Test> all = new ArrayList<>();
            scenarios.forEach(s -> all.addAll(s.allTests()));
            return all;
        }

        public long count(Status status) {
            return tests().stream().filter(t -> t.status() == status).count();
        }

        /** @return whether the tests ran and none failed */
        public boolean passed() {
            return error == null && count(Status.FAILED) == 0;
        }

        /** @return e.g. {@code 12 tests: 11 passed, 1 failed, 0 pending} */
        public String summary() {
            if (error != null) {
                return "The tests did not run: " + error;
            }
            int total = tests().size();
            return total + (total == 1 ? " test: " : " tests: ") + count(Status.PASSED) + " passed, "
                    + count(Status.FAILED) + " failed, " + count(Status.PENDING) + " pending";
        }
    }

    private final Processor processor;
    private final Map<String, XsltExecutable> stylesheets = new ConcurrentHashMap<>();

    public XSpecRunner() {
        this.processor = SaxonResourceAccess.restrictedProcessor();
    }

    /** @return whether a document is an XSpec description ({@code x:description} root) */
    public static boolean isXSpec(String text) {
        return text != null && text.contains(XSPEC_NS) && text.contains("description");
    }

    /**
     * Runs the tests of an XSpec document.
     *
     * @param xspecText the document (the editor text; may be unsaved)
     * @param xspecFile where it is (relative references to the stylesheet, query, Schematron, contexts)
     */
    public Result run(String xspecText, Path xspecFile) {
        Kind kind = Kind.XSLT;
        try {
            String systemId = xspecFile.toAbsolutePath().toUri().toString();
            XdmNode description = processor.newDocumentBuilder()
                    .build(source(xspecText, systemId));
            XdmNode root = rootElement(description);
            if (root == null || !XSPEC_NS.equals(root.getNodeName().getNamespace())
                    || !"description".equals(root.getNodeName().getLocalName())) {
                return new Result(kind, List.of(), null, "the document is not an XSpec description (x:description)");
            }
            if (root.getAttributeValue(new QName("schematron")) != null) {
                kind = Kind.SCHEMATRON;
            } else if (root.getAttributeValue(new QName("query")) != null
                    || root.getAttributeValue(new QName("query-at")) != null) {
                kind = Kind.XQUERY;
            }
            XdmNode report = switch (kind) {
                case XSLT -> runXslt(description, systemId);
                case XQUERY -> runXQuery(description, systemId);
                case SCHEMATRON -> runSchematron(description, systemId);
            };
            return new Result(kind, scenarios(rootElement(report)), serialize(report), null);
        } catch (SaxonApiException e) {
            return new Result(kind, List.of(), null, message(e));
        } catch (IOException | RuntimeException e) {
            return new Result(kind, List.of(), null, String.valueOf(e.getMessage()));
        }
    }

    private XdmNode runXslt(XdmNode description, String systemId) throws SaxonApiException, IOException {
        XdmNode compiled = transform("compiler/compile-xslt-tests.xsl", description, Map.of());
        XsltExecutable tests = processor.newXsltCompiler().compile(withSystemId(compiled, systemId));
        Xslt30Transformer transformer = tests.load30();
        XdmDestination report = new XdmDestination();
        transformer.callTemplate(new QName(XSPEC_NS, "main"), report);
        return report.getXdmNode();
    }

    private XdmNode runXQuery(XdmNode description, String systemId) throws SaxonApiException, IOException {
        String query = transformToText("compiler/compile-xquery-tests.xsl", description);
        var compiler = processor.newXQueryCompiler();
        compiler.setBaseURI(java.net.URI.create(systemId));
        XQueryEvaluator evaluator = compiler.compile(query).load();
        XdmDestination report = new XdmDestination();
        evaluator.run(report);
        return report.getXdmNode();
    }

    private XdmNode runSchematron(XdmNode description, String systemId) throws SaxonApiException, IOException {
        XdmNode compiledSchematron = transform("schematron/schut-to-xslt.xsl", description, Map.of());
        Path temp = Files.createTempFile("fxt-xspec-schematron", ".xsl");
        try {
            Files.writeString(temp, serialize(compiledSchematron), StandardCharsets.UTF_8);
            XdmNode tests = transform("schematron/schut-to-xspec.xsl", description,
                    Map.of(new QName("stylesheet-uri"), new XdmAtomicValue(temp.toUri().toString())));
            XdmNode rebased = processor.newDocumentBuilder().build(source(serialize(tests), systemId));
            return runXslt(rebased, systemId);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ---- reports ------------------------------------------------------------------------------

    /** @return XSpec's HTML report (styles inline, no scripts) */
    public String html(Result result) throws SaxonApiException, IOException {
        return formatReport(result, "reporter/format-xspec-report.xsl", Map.of(
                new QName("inline-css"), new XdmAtomicValue("true"),
                new QName("report-css-uri"), new XdmAtomicValue(reportCss().toUri().toString())));
    }

    private static volatile Path reportCss;

    /**
     * XSpec's report styles as one local file: the reporter reads them with {@code unparsed-text()},
     * which cannot read from inside the application's jar.
     */
    private static Path reportCss() throws IOException {
        Path css = reportCss;
        if (css != null && Files.isRegularFile(css)) {
            return css;
        }
        StringBuilder text = new StringBuilder();
        for (String name : List.of("test-report-colors-classic.css", "test-report-base.css")) {
            try (InputStream in = XSpecRunner.class.getResourceAsStream(BASE + "reporter/" + name)) {
                if (in != null) {
                    text.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        css = Files.createTempFile("fxt-xspec-report", ".css");
        css.toFile().deleteOnExit();
        Files.writeString(css, text, StandardCharsets.UTF_8);
        reportCss = css;
        return css;
    }

    /** @return the report as JUnit XML (for CI servers) */
    public String junit(Result result) throws SaxonApiException, IOException {
        return formatReport(result, "reporter/junit-report.xsl", Map.of());
    }

    private String formatReport(Result result, String stylesheet, Map<QName, XdmAtomicValue> parameters)
            throws SaxonApiException, IOException {
        if (result.reportXml() == null) {
            throw new IOException("There is no report: " + result.error());
        }
        XdmNode report = processor.newDocumentBuilder().build(new StreamSource(new StringReader(result.reportXml())));
        Xslt30Transformer transformer = executable(stylesheet).load30();
        if (!parameters.isEmpty()) {
            transformer.setStylesheetParameters(parameters);
        }
        StringWriter out = new StringWriter();
        // the stylesheet's own output declaration and character maps (the inline CSS relies on them)
        transformer.transform(report.asSource(), transformer.newSerializer(out));
        return out.toString();
    }

    private static List<Scenario> scenarios(XdmNode parent) {
        List<Scenario> scenarios = new ArrayList<>();
        if (parent == null) {
            return scenarios;
        }
        for (XdmNode child : parent.children()) {
            if (isXSpec(child, "scenario")) {
                scenarios.add(scenario(child));
            }
        }
        return scenarios;
    }

    private static Scenario scenario(XdmNode node) {
        List<Test> tests = new ArrayList<>();
        String result = "";
        for (XdmNode child : node.children()) {
            if (isXSpec(child, "result")) {
                result = content(child);
            }
        }
        for (XdmNode child : node.children()) {
            if (isXSpec(child, "test")) {
                String expected = "";
                for (XdmNode part : child.children()) {
                    if (isXSpec(part, "expect")) {
                        expected = content(part);
                    }
                }
                String successful = child.getAttributeValue(new QName("successful"));
                Status status = child.getAttributeValue(new QName("pending")) != null ? Status.PENDING
                        : "true".equals(successful) ? Status.PASSED : Status.FAILED;
                tests.add(new Test(label(child), status, expected, result));
            }
        }
        return new Scenario(label(node), node.getAttributeValue(new QName("pending")) != null, tests, scenarios(node));
    }

    private static boolean isXSpec(XdmNode node, String localName) {
        return node.getNodeKind() == XdmNodeKind.ELEMENT && XSPEC_NS.equals(node.getNodeName().getNamespace())
                && localName.equals(node.getNodeName().getLocalName());
    }

    private static String label(XdmNode node) {
        for (XdmNode child : node.children()) {
            if (isXSpec(child, "label")) {
                return child.getStringValue().strip();
            }
        }
        String label = node.getAttributeValue(new QName("label"));
        return label == null ? "" : label.strip();
    }

    /** The serialized content of {@code x:result} / {@code x:expect}: its children, or its select attribute. */
    private static String content(XdmNode node) {
        StringBuilder text = new StringBuilder();
        for (XdmNode child : node.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT || child.getNodeKind() == XdmNodeKind.TEXT) {
                text.append(child.getNodeKind() == XdmNodeKind.ELEMENT ? child.toString() : child.getStringValue());
            }
        }
        String value = text.toString().strip();
        if (value.isEmpty()) {
            String select = node.getAttributeValue(new QName("select"));
            value = select == null ? "" : select;
        }
        return value;
    }

    // ---- Saxon helpers ------------------------------------------------------------------------

    private XdmNode transform(String stylesheet, XdmNode source, Map<QName, XdmAtomicValue> parameters)
            throws SaxonApiException, IOException {
        Xslt30Transformer transformer = executable(stylesheet).load30();
        if (!parameters.isEmpty()) {
            transformer.setStylesheetParameters(parameters);
        }
        XdmDestination out = new XdmDestination();
        if (source.getBaseURI() != null && source.getBaseURI().isAbsolute()) {
            out.setBaseURI(source.getBaseURI());
        }
        transformer.transform(source.asSource(), out);
        return out.getXdmNode();
    }

    private String transformToText(String stylesheet, XdmNode source) throws SaxonApiException, IOException {
        Xslt30Transformer transformer = executable(stylesheet).load30();
        StringWriter out = new StringWriter();
        transformer.transform(source.asSource(), processor.newSerializer(out));
        return out.toString();
    }

    private XsltExecutable executable(String stylesheet) throws SaxonApiException, IOException {
        XsltExecutable cached = stylesheets.get(stylesheet);
        if (cached != null) {
            return cached;
        }
        URL url = XSpecRunner.class.getResource(BASE + stylesheet);
        if (url == null) {
            throw new IOException("XSpec is missing from the application: " + stylesheet);
        }
        XsltExecutable compiled;
        try (InputStream in = url.openStream()) {
            StreamSource source = new StreamSource(in, url.toString());
            compiled = processor.newXsltCompiler().compile(source);
        }
        stylesheets.put(stylesheet, compiled);
        return compiled;
    }

    private static StreamSource source(String text, String systemId) {
        StreamSource source = new StreamSource(new StringReader(text));
        source.setSystemId(systemId);
        return source;
    }

    private javax.xml.transform.Source withSystemId(XdmNode node, String systemId) throws SaxonApiException {
        return source(serialize(node), systemId);
    }

    private String serialize(XdmNode node) throws SaxonApiException {
        StringWriter out = new StringWriter();
        Serializer serializer = processor.newSerializer(out);
        serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
        serializer.setOutputProperty(Serializer.Property.INDENT, "no");
        serializer.serializeNode(node);
        return out.toString();
    }

    private static XdmNode rootElement(XdmNode document) {
        if (document == null) {
            return null;
        }
        if (document.getNodeKind() == XdmNodeKind.ELEMENT) {
            return document;
        }
        for (XdmNode child : document.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        return null;
    }

    private static String message(SaxonApiException e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        return e.getErrorCode() != null ? message + " [" + e.getErrorCode().getLocalName() + "]" : message;
    }
}
