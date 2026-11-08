package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Runs {@code fxt-cli} in-process ({@link FxtCli#newCommandLine}) on small files and checks exit
 * codes, standard output / error and the report formats.
 */
@DisplayName("fxt-cli")
class FxtCliTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="n" type="xs:int" maxOccurs="unbounded"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    private static final String SCHEMATRON = """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
              <pattern><rule context="n">
                <report test=". = '3'" role="warning">three is suspicious</report>
              </rule></pattern>
            </schema>
            """;

    @TempDir
    Path dir;

    private Path xsd;
    private Path valid;
    private Path invalid;
    private String out;
    private String err;

    @BeforeEach
    void files() throws Exception {
        xsd = write("s.xsd", XSD);
        valid = write("valid.xml", "<root>\n  <n>3</n>\n</root>\n");
        invalid = write("invalid.xml", "<root>\n  <n>x</n>\n</root>\n");
    }

    private Path write(String name, String text) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private int run(Object... args) {
        StringWriter outText = new StringWriter();
        StringWriter errText = new StringWriter();
        String[] strings = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            strings[i] = String.valueOf(args[i]);
        }
        int code = FxtCli.newCommandLine(new PrintWriter(outText, true), new PrintWriter(errText, true))
                .execute(strings);
        out = outText.toString();
        err = errText.toString();
        return code;
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("a valid file exits 0, an invalid one 1 with file:line:column")
        void exitCodes() {
            assertEquals(ExitCodes.OK, run("validate", "-x", xsd, valid), err);
            assertTrue(out.contains("1 file, all valid"), out);

            assertEquals(ExitCodes.FAILED, run("validate", "-x", xsd, valid, invalid), err);
            assertTrue(out.contains(invalid + ":2:"), out);
            assertTrue(out.contains("error: [XSD]"), out);
            assertTrue(out.contains("2 files, 1 invalid"), out);
        }

        @Test
        @DisplayName("without --xsd the schema the document declares is used")
        void declaredSchema() throws Exception {
            Path declared = write("declared.xml", "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " xsi:noNamespaceSchemaLocation=\"s.xsd\">\n  <n>x</n>\n</root>\n");
            assertEquals(ExitCodes.FAILED, run("validate", declared), err);
            assertTrue(out.contains("[XSD]"), out);

            // --no-schema-lookup: well-formedness only
            assertEquals(ExitCodes.OK, run("validate", "--no-schema-lookup", declared), err);
        }

        @Test
        @DisplayName("a malformed file is reported once per parse error")
        void malformed() throws Exception {
            Path broken = write("broken.xml", "<root><n>1</n>\n");
            assertEquals(ExitCodes.FAILED, run("validate", broken));
            assertEquals(1, out.lines().filter(l -> l.contains("[Well-formed]")).count(), out);
        }

        @Test
        @DisplayName("a schema that does not load exits 3")
        void schemaDoesNotLoad() throws Exception {
            Path broken = write("broken.xsd", "not a schema");
            assertEquals(ExitCodes.NOT_RUN, run("validate", "-x", broken, valid), out + err);
            assertTrue(out.contains("not validated"), out);
        }

        @Test
        @DisplayName("a missing file or schema is a usage error (2) with a short hint")
        void usageErrors() {
            assertEquals(ExitCodes.USAGE, run("validate", dir.resolve("missing.xml")));
            assertTrue(err.contains("File not found"), err);
            assertTrue(err.contains("--help"), err);
            assertFalse(err.contains("Usage:"), err);

            assertEquals(ExitCodes.USAGE, run("validate", "-x", dir.resolve("missing.xsd"), valid));
            assertTrue(err.contains("XML Schema not found"), err);
        }

        @Test
        @DisplayName("folders are searched recursively for the include globs, hidden folders skipped")
        void folders() throws Exception {
            write("sub/deep.xml", "<a/>");
            write("sub/ignored.txt", "<a");
            write(".git/hidden.xml", "<a");
            assertEquals(ExitCodes.FAILED, run("validate", "--no-schema-lookup", "-x", xsd, dir), err);
            // s.xsd is not *.xml; valid, invalid, deep
            assertTrue(out.contains("3 files"), out);
            assertFalse(out.contains("hidden.xml"), out);
        }

        @Test
        @DisplayName("Schematron warnings fail only with --fail-on-warning")
        void schematronWarnings() throws Exception {
            Path sch = write("r.sch", SCHEMATRON);
            assertEquals(ExitCodes.OK, run("validate", "-x", xsd, "-s", sch, valid), out + err);
            assertTrue(out.contains("warning: [Schematron] three is suspicious"), out);
            assertEquals(ExitCodes.FAILED, run("validate", "-x", xsd, "-s", sch, "--fail-on-warning", valid));
        }

        @Test
        @DisplayName("JSON files are checked against --json-schema")
        void json() throws Exception {
            Path schema = write("j.schema.json", "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}");
            Path good = write("good.json", "{\"a\": \"x\"}");
            Path bad = write("bad.json", "{\"a\": 1}");
            assertEquals(ExitCodes.OK, run("validate", "-j", schema, good), out + err);
            assertEquals(ExitCodes.FAILED, run("validate", "-j", schema, bad));
            assertTrue(out.contains("[JSON Schema]"), out);
        }

        @Test
        @DisplayName("SARIF 2.1.0 report with rule, level and region")
        void sarif() {
            assertEquals(ExitCodes.FAILED, run("validate", "-x", xsd, "-f", "sarif", invalid));
            JsonObject root = JsonParser.parseString(out).getAsJsonObject();
            assertEquals("2.1.0", root.get("version").getAsString());
            JsonObject runObject = root.getAsJsonArray("runs").get(0).getAsJsonObject();
            assertEquals("FreeXmlToolkit",
                    runObject.getAsJsonObject("tool").getAsJsonObject("driver").get("name").getAsString());
            JsonObject result = runObject.getAsJsonArray("results").get(0).getAsJsonObject();
            assertEquals("xsd", result.get("ruleId").getAsString());
            assertEquals("error", result.get("level").getAsString());
            JsonObject physical = result.getAsJsonArray("locations").get(0).getAsJsonObject()
                    .getAsJsonObject("physicalLocation");
            assertEquals(2, physical.getAsJsonObject("region").get("startLine").getAsInt());
            assertTrue(physical.getAsJsonObject("artifactLocation").get("uri").getAsString().endsWith("invalid.xml"));
        }

        @Test
        @DisplayName("JUnit report written to a file, text summary on standard output")
        void junitToFile() throws Exception {
            Path report = dir.resolve("reports/junit.xml");
            assertEquals(ExitCodes.FAILED, run("validate", "-x", xsd, "-f", "junit", "-r", report, valid, invalid));
            assertTrue(out.contains("2 files, 1 invalid"), out);
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(Files.readAllBytes(report)));
            assertEquals("2", doc.getDocumentElement().getAttribute("tests"));
            assertEquals("1", doc.getDocumentElement().getAttribute("failures"));
            assertEquals(1, doc.getElementsByTagName("failure").getLength());
        }

        @Test
        @DisplayName("JSON report")
        void jsonReport() {
            assertEquals(ExitCodes.FAILED, run("validate", "-x", xsd, "-f", "json", invalid));
            JsonObject root = JsonParser.parseString(out).getAsJsonObject();
            assertFalse(root.get("valid").getAsBoolean());
            assertEquals(1, root.getAsJsonObject("summary").get("invalidFiles").getAsInt());
        }

        @Test
        @DisplayName("--quiet prints the findings without the summary")
        void quiet() {
            assertEquals(ExitCodes.OK, run("-q", "validate", "-x", xsd, valid));
            assertEquals("", out);
        }
    }

    @Nested
    @DisplayName("validate (large files, streaming)")
    class ValidateStreaming {

        @Test
        @DisplayName("large XML files are validated as a stream with the same report")
        void streaming() throws Exception {
            long previous = ValidateCommand.streamingBytes;
            ValidateCommand.streamingBytes = 1;
            try {
                assertEquals(ExitCodes.OK, run("validate", "-x", xsd, valid), err);
                assertTrue(out.contains("1 file, all valid"), out);
                assertEquals(ExitCodes.FAILED, run("validate", "-x", xsd, invalid), err);
                assertTrue(out.contains(invalid + ":2:"), out);
                assertTrue(out.contains("error: [XSD]"), out);
                Path broken = write("broken.xml", "<root>\n<n>");
                assertEquals(ExitCodes.FAILED, run("validate", broken), err);
                assertTrue(out.contains("[Well-formed]"), out);
            } finally {
                ValidateCommand.streamingBytes = previous;
            }
        }
    }

    @Nested
    @DisplayName("transform --profile")
    class Profile {

        @Test
        @DisplayName("writes a JSON or text profile next to the normal output")
        void profile() throws Exception {
            Path xsl = write("p.xsl", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                      <xsl:output method="text"/>
                      <xsl:template match="/"><xsl:apply-templates select="//n"/></xsl:template>
                      <xsl:template match="n"><xsl:value-of select="."/>;</xsl:template>
                    </xsl:stylesheet>
                    """);
            Path json = dir.resolve("profile.json");
            assertEquals(ExitCodes.OK, run("transform", "-s", xsl, "--profile", json, "--warmup", "0", valid), err);
            assertEquals("3;", out);
            String report = Files.readString(json);
            assertTrue(report.contains("\"name\": \"match=\\\"n\\\"\"") && report.contains("\"callTree\""), report);

            assertEquals(ExitCodes.OK, run("transform", "-s", xsl, "--profile", "-", valid), err);
            assertTrue(err.contains("Total ") && err.contains("template rule match=\"n\""), err);
            assertEquals(ExitCodes.USAGE, run("transform", "-s", xsl, "--profile", "-", valid, invalid));
        }
    }

    @Nested
    @DisplayName("xspec")
    class XSpec {

        @Test
        @DisplayName("passing tests exit 0, a failure 1 with expected and actual, JUnit report")
        void xspec() throws Exception {
            write("f.xsl", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                    xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xsl:function name="f:twice" as="xs:integer"><xsl:param name="n" as="xs:integer"/>
                        <xsl:sequence select="$n * 2"/></xsl:function>
                    </xsl:stylesheet>
                    """);
            Path ok = write("ok.xspec", """
                    <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="f.xsl">
                      <x:scenario label="twice"><x:call function="f:twice"><x:param select="2"/></x:call>
                        <x:expect label="four" select="4"/></x:scenario>
                    </x:description>
                    """);
            assertEquals(ExitCodes.OK, run("xspec", ok), err);
            assertTrue(out.contains("passed   twice / four") && out.contains("1 test: 1 passed"), out);

            Path bad = write("bad.xspec", """
                    <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="f.xsl">
                      <x:scenario label="twice"><x:call function="f:twice"><x:param select="2"/></x:call>
                        <x:expect label="five" select="5"/></x:scenario>
                    </x:description>
                    """);
            Path junit = dir.resolve("junit.xml");
            assertEquals(ExitCodes.FAILED, run("xspec", "-r", junit, bad), err);
            assertTrue(out.contains("FAILED   twice / five"), out);
            assertTrue(Files.readString(junit).contains("<testsuite"), Files.readString(junit));
            assertEquals(ExitCodes.NOT_RUN, run("xspec", write("not.xspec", "<a/>")));
        }
    }

    @Nested
    @DisplayName("project")
    class Project {

        @Test
        @DisplayName("scenario runs a project's scenarios (all, by name, --list); validate --project uses its bindings")
        void scenarios() throws Exception {
            write("x/count.xsl", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                      <xsl:param name="label" select="'n'"/>
                      <xsl:output method="text"/>
                      <xsl:template match="/"><xsl:value-of select="$label"/>=<xsl:value-of select="count(//i)"/></xsl:template>
                    </xsl:stylesheet>
                    """);
            write("data/d.xml", "<r><i/><i/></r>");
            write("p.xsd", """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:element name="r"><xs:complexType><xs:sequence>
                        <xs:element name="i" maxOccurs="1"/></xs:sequence></xs:complexType></xs:element>
                    </xs:schema>
                    """);
            var project = org.fxt.freexmltoolkit.service.project.FxtProject.create(dir.resolve("p.fxtproj"), "P");
            project.putScenario(new org.fxt.freexmltoolkit.service.project.FxtProject.Scenario("count", "x/count.xsl",
                    "data/d.xml", java.util.Map.of("label", "items"), null, null));
            project.putScenario(new org.fxt.freexmltoolkit.service.project.FxtProject.Scenario("file", "x/count.xsl",
                    "data/d.xml", java.util.Map.of(), null, "out/c.txt"));
            project.putScenario(new org.fxt.freexmltoolkit.service.project.FxtProject.Scenario("broken", "x/none.xsl",
                    "data/d.xml", java.util.Map.of(), null, null));
            project.bindSchema("data/**/*.xml", dir.resolve("p.xsd"));
            project.save();
            Path file = dir.resolve("p.fxtproj");

            assertEquals(ExitCodes.OK, run("scenario", file, "count"), err);
            assertEquals("items=2", out);
            assertEquals(ExitCodes.OK, run("scenario", file, "--list"), err);
            assertTrue(out.contains("count\tx/count.xsl < data/d.xml") && out.contains("> out/c.txt"), out);
            assertEquals(ExitCodes.FAILED, run("scenario", file), err);
            assertTrue(err.contains("broken: error: Not found"), err);
            assertEquals("n=2", Files.readString(dir.resolve("out/c.txt")));
            assertEquals(ExitCodes.USAGE, run("scenario", file, "nope"));

            assertEquals(ExitCodes.OK, run("validate", dir.resolve("data/d.xml")), err + out);
            assertEquals(ExitCodes.FAILED, run("validate", "--project", file, dir.resolve("data/d.xml")),
                    "the project's schema allows one i only: " + out);
        }
    }

    @Nested
    @DisplayName("compare")
    class Compare {

        @Test
        @DisplayName("XML files by structure: equivalent 0, different 1 with line and XPath, broken 3")
        void files() throws Exception {
            Path a = write("a.xml", "<order id=\"1\" date=\"x\"><item>A</item></order>");
            Path b = write("b.xml", "<order date=\"x\" id=\"1\">\n  <item>A</item>\n</order>\n");
            Path c = write("c.xml", "<order id=\"2\" date=\"x\"><item>A</item></order>");
            assertEquals(ExitCodes.OK, run("compare", a, b), err);
            assertTrue(out.contains("equivalent"), out);

            assertEquals(ExitCodes.FAILED, run("compare", a, c), err);
            assertTrue(out.contains(a + ":1: Attribute 'id' changed: \"1\" → \"2\" (/order[1]/@id)"), out);
            assertEquals(ExitCodes.FAILED, run("compare", "-f", "json", a, c), err);
            assertTrue(out.contains("\"leftXPath\": \"/order[1]/@id\""), out);

            Path broken = write("broken.xml", "<order>");
            assertEquals(ExitCodes.NOT_RUN, run("compare", a, broken));
            assertTrue(err.contains("Not well-formed"), err);
            assertEquals(ExitCodes.USAGE, run("compare", a, dir));
        }

        @Test
        @DisplayName("folders file by file; formatting-only changes match")
        void folders() throws Exception {
            write("l/x.xml", "<a><b/></a>");
            write("r/x.xml", "<a>\n  <b/>\n</a>");
            assertEquals(ExitCodes.OK, run("compare", dir.resolve("l"), dir.resolve("r")), err);
            assertTrue(out.contains("the folders match"), out);
            assertEquals(ExitCodes.FAILED, run("compare", "--bytes", dir.resolve("l"), dir.resolve("r")), err);
            assertTrue(out.contains("differs    x.xml"), out);
            write("r/y.txt", "new");
            assertEquals(ExitCodes.FAILED, run("compare", dir.resolve("l"), dir.resolve("r")), err);
            assertTrue(out.contains("only right y.txt"), out);
        }
    }

    @Nested
    @DisplayName("merge")
    class Merge {

        @Test
        @DisplayName("clean merge exits 0; conflicts exit 1 with markers and labels")
        void merge() throws Exception {
            Path base = write("base.xml", "<a>\n  <x>1</x>\n  <y>1</y>\n  <z>1</z>\n</a>\n");
            Path mine = write("mine.xml", "<a>\n  <x>2</x>\n  <y>1</y>\n  <z>1</z>\n</a>\n");
            Path theirs = write("theirs.xml", "<a>\n  <x>1</x>\n  <y>1</y>\n  <z>3</z>\n</a>\n");
            Path merged = dir.resolve("merged.xml");
            assertEquals(ExitCodes.OK, run("merge", base, mine, theirs, "-o", merged), err);
            assertEquals("<a>\n  <x>2</x>\n  <y>1</y>\n  <z>3</z>\n</a>\n", Files.readString(merged));

            Path other = write("other.xml", "<a>\n  <x>9</x>\n  <y>1</y>\n  <z>1</z>\n</a>\n");
            assertEquals(ExitCodes.FAILED, run("merge", "-L", "ours", "-L", "base", "-L", "theirs", base, mine, other), err);
            assertTrue(out.contains("<<<<<<< ours\n  <x>2</x>\n||||||| base\n  <x>1</x>\n=======\n  <x>9</x>\n>>>>>>> theirs\n"),
                    out);
            assertTrue(err.contains("1 conflict"), err);
        }
    }

    @Nested
    @DisplayName("convert")
    class Convert {

        @Test
        @DisplayName("XML to JSON on standard output, YAML to XML into a folder, JSON back to XML")
        void conversions() throws Exception {
            assertEquals(ExitCodes.OK, run("convert", "--typed", valid), err);
            assertEquals("{\n  \"root\": {\n    \"n\": 3\n  }\n}\n", out);

            Path yaml = write("in/config.yaml", "server:\n  port: 8080\n");
            Path json = write("in/data.json", "{\"a\": [1, 2]}");
            Path target = dir.resolve("out");
            assertEquals(ExitCodes.OK, run("convert", "-d", target, "--root", "data", yaml, json), err);
            assertTrue(Files.readString(target.resolve("config.xml")).contains("<port>8080</port>"));
            assertTrue(Files.readString(target.resolve("data.xml")).contains("<data>\n  <a>1</a>\n  <a>2</a>\n</data>"),
                    Files.readString(target.resolve("data.xml")));

            assertEquals(ExitCodes.OK, run("convert", "-t", "json", "--indent", "0", yaml), err);
            assertEquals("{\"server\":{\"port\":8080}}", out);

            assertEquals(ExitCodes.OK, run("convert", "-t", "yaml", "--typed", "-d", target, valid), err);
            assertEquals("root:\n  n: 3\n", Files.readString(target.resolve("valid.yaml")));
        }

        @Test
        @DisplayName("W3C mapping round trip; errors exit 1, usage errors 2")
        void w3cAndErrors() throws Exception {
            Path json = write("w.json", "{\"a\": true}");
            Path xml = dir.resolve("w.xml");
            assertEquals(ExitCodes.OK, run("convert", "-m", "w3c", "-o", xml, json), err);
            assertTrue(Files.readString(xml).contains("<boolean key=\"a\">true</boolean>"), Files.readString(xml));
            assertEquals(ExitCodes.OK, run("convert", "-m", "w3c", xml), err);
            assertTrue(out.contains("\"a\": true"), out);

            assertEquals(ExitCodes.FAILED, run("convert", "-m", "w3c", valid), err);
            assertTrue(err.contains("W3C"), err);
            Path broken = write("broken.json", "{");
            assertEquals(ExitCodes.FAILED, run("convert", broken));
            assertEquals(ExitCodes.USAGE, run("convert", valid, json));
        }
    }

    @Nested
    @DisplayName("transform")
    class Transform {

        private static final String XSLT = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:param name="label" select="'sum'"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="$label"/>=<xsl:value-of select="sum(//n)"/></xsl:template>
                </xsl:stylesheet>
                """;

        @Test
        @DisplayName("XSLT with a parameter to standard output; output method from xsl:output")
        void xslt() throws Exception {
            Path xsl = write("t.xsl", XSLT);
            assertEquals(ExitCodes.OK, run("transform", "-s", xsl, "-p", "label=total", valid), err);
            assertEquals("total=3", out);
        }

        @Test
        @DisplayName("several inputs need --output-dir; results are named after the inputs")
        void outputDir() throws Exception {
            Path xsl = write("t.xsl", XSLT);
            assertEquals(ExitCodes.USAGE, run("transform", "-s", xsl, valid, invalid));
            Path target = dir.resolve("out");
            Path second = write("second.xml", "<root><n>4</n></root>");
            assertEquals(ExitCodes.OK, run("transform", "-s", xsl, "-d", target, valid, second), err);
            assertEquals("sum=3", Files.readString(target.resolve("valid.txt")));
            assertEquals("sum=4", Files.readString(target.resolve("second.txt")));
        }

        @Test
        @DisplayName("XQuery with an external variable, without input")
        void xquery() throws Exception {
            Path query = write("q.xq", "declare variable $x external; <r>{ $x }</r>");
            assertEquals(ExitCodes.OK, run("transform", "-s", query, "-p", "x=7"), err);
            assertTrue(out.contains("<r>7</r>"), out);
        }

        @Test
        @DisplayName("XProc pipeline")
        void xproc() throws Exception {
            Path pipeline = write("p.xpl", """
                    <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                      <p:input port="source"/><p:output port="result"/>
                      <p:identity/>
                    </p:declare-step>
                    """);
            assertEquals(ExitCodes.OK, run("transform", "-s", pipeline, valid), err);
            assertTrue(out.contains("<n>3</n>"), out);
        }

        @Test
        @DisplayName("a failing transformation exits 1; remote doc() stays blocked")
        void failure() throws Exception {
            Path xsl = write("remote.xsl", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                      <xsl:template match="/"><xsl:value-of select="doc('http://127.0.0.1:9/x.xml')"/></xsl:template>
                    </xsl:stylesheet>
                    """);
            assertEquals(ExitCodes.FAILED, run("transform", "-s", xsl, valid));
            assertTrue(err.contains("Blocked remote resource"), err);
        }

        @Test
        @DisplayName("XSLT without input and an unknown language are usage errors")
        void usage() throws Exception {
            Path xsl = write("t.xsl", XSLT);
            assertEquals(ExitCodes.USAGE, run("transform", "-s", xsl));
            Path unknown = write("t.txt", "x");
            assertEquals(ExitCodes.USAGE, run("transform", "-s", unknown, valid));
            assertTrue(err.contains("--language"), err);
        }
    }

    @Nested
    @DisplayName("xsd-doc and fop")
    class Output {

        @Test
        @DisplayName("HTML documentation site")
        void html() {
            Path site = dir.resolve("doc");
            assertEquals(ExitCodes.OK, run("xsd-doc", xsd, "-o", site), err);
            assertTrue(Files.isRegularFile(site.resolve("index.html")));
        }

        @Test
        @DisplayName("PDF documentation from the extension of --output")
        void pdf() throws Exception {
            Path pdf = dir.resolve("doc.pdf");
            assertEquals(ExitCodes.OK, run("xsd-doc", xsd, "-o", pdf), err);
            assertTrue(new String(Files.readAllBytes(pdf), 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        }

        @Test
        @DisplayName("PDF from an XSL-FO document; -i/-s and --fo exclude each other")
        void fop() throws Exception {
            Path fo = write("doc.fo", """
                    <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                      <fo:layout-master-set>
                        <fo:simple-page-master master-name="p" page-height="297mm" page-width="210mm">
                          <fo:region-body margin="20mm"/>
                        </fo:simple-page-master>
                      </fo:layout-master-set>
                      <fo:page-sequence master-reference="p">
                        <fo:flow flow-name="xsl-region-body"><fo:block>Hello</fo:block></fo:flow>
                      </fo:page-sequence>
                    </fo:root>
                    """);
            Path pdf = dir.resolve("out/hello.pdf");
            assertEquals(ExitCodes.OK, run("fop", "--fo", fo, "-o", pdf), err);
            assertTrue(Files.size(pdf) > 100);
            assertEquals(ExitCodes.USAGE, run("fop", "--fo", fo, "-i", valid, "-o", pdf));
        }
    }

    @Test
    @DisplayName("--version names the application")
    void version() {
        assertEquals(ExitCodes.OK, run("--version"));
        assertTrue(out.startsWith("FreeXmlToolkit "), out);
    }

    @Test
    @DisplayName("the log configuration writes to standard error only")
    void logConfiguration() throws Exception {
        try (var in = FxtCli.class.getClassLoader().getResourceAsStream(Logging.CONFIG)) {
            String config = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(config.contains("SYSTEM_ERR"));
            assertFalse(config.contains("SYSTEM_OUT"));
        }
    }

    @Test
    @DisplayName("severities of XSD, Schematron and JSON Schema findings")
    void severities() {
        assertEquals(ValidationReport.Severity.ERROR, ValidationReport.Severity.of("fatal"));
        assertEquals(ValidationReport.Severity.ERROR, ValidationReport.Severity.of(null));
        assertEquals(ValidationReport.Severity.WARNING, ValidationReport.Severity.of("warning"));
        assertEquals(ValidationReport.Severity.INFO, ValidationReport.Severity.of("info"));
        assertEquals(List.of("xsd", "schematron", "json-schema"),
                List.of(ReportWriters.ruleIdOf("XSD"), ReportWriters.ruleIdOf("Schematron"),
                        ReportWriters.ruleIdOf("JSON Schema")));
    }
}
