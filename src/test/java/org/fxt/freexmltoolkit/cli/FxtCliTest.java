package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    private static final String LANG_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string">
                <xs:annotation>
                  <xs:documentation xml:lang="de">Wurzel</xs:documentation>
                  <xs:documentation xml:lang="en">Root</xs:documentation>
                </xs:annotation>
              </xs:element>
            </xs:schema>
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
        @DisplayName("YAML files: syntax, --json-schema and the schema they declare")
        void yaml() throws Exception {
            Path schema = write("y.schema.json", "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}");
            Path good = write("good.yaml", "a: x\n");
            Path bad = write("bad.yml", "# comment\na: 1\n");
            Path declared = write("declared.yaml", "# yaml-language-server: $schema=y.schema.json\na: 1\n");
            Path broken = write("broken.yaml", "a: [1, 2\n");
            assertEquals(ExitCodes.OK, run("validate", "-j", schema, good), out + err);
            assertEquals(ExitCodes.FAILED, run("validate", "-j", schema, bad));
            assertTrue(out.contains("[JSON Schema]") && out.contains(":2"), out);
            assertEquals(ExitCodes.FAILED, run("validate", declared));
            assertTrue(out.contains("[JSON Schema]"), out);
            assertEquals(ExitCodes.OK, run("validate", "--no-schema-lookup", declared), out + err);
            assertEquals(ExitCodes.FAILED, run("validate", broken));
            assertTrue(out.contains("[YAML]"), out);
        }

        @Test
        @DisplayName("JSON and YAML files: a declared $schema and a Schema Library file-name pattern")
        void jsonSchemaByDeclarationAndFileName() throws Exception {
            Path schema = write("n.schema.json", "{\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"string\"}}}");
            Path declaredJson = write("declared.json", "{\"$schema\": \"n.schema.json\", \"n\": 1}");
            assertEquals(ExitCodes.FAILED, run("validate", declaredJson));
            assertTrue(out.contains("[JSON Schema]"), out);
            assertEquals(ExitCodes.OK, run("validate", "--no-schema-lookup", declaredJson), out + err);
            Path missing = write("missing.json", "{\"$schema\": \"nowhere.schema.json\"}");
            assertNotEquals(ExitCodes.OK, run("validate", missing));
            assertTrue(out.contains("nowhere.schema.json"), out);

            var original = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            var library = new org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl(dir.resolve("lib.json"),
                    new org.fxt.freexmltoolkit.service.SchemaResourceCache(dir.resolve("cache")),
                    () -> new java.io.ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes(StandardCharsets.UTF_8)));
            library.addEntry(org.fxt.freexmltoolkit.domain.SchemaLibraryEntry.user("", schema.toString(),
                    org.fxt.freexmltoolkit.domain.SchemaKind.JSON_SCHEMA, "n", null)
                    .withFileMatch(java.util.List.of("settings*.yml")));
            org.fxt.freexmltoolkit.di.ServiceRegistry.register(org.fxt.freexmltoolkit.service.SchemaLibraryService.class, library);
            try {
                Path byName = write("conf/settings.prod.yml", "n: 1\n");
                Path other = write("conf/other.yml", "n: 1\n");
                assertEquals(ExitCodes.FAILED, run("validate", byName));
                assertTrue(out.contains("[JSON Schema]"), out);
                assertEquals(ExitCodes.OK, run("validate", other), out + err);
                assertEquals(ExitCodes.OK, run("validate", "--no-schema-lookup", byName), out + err);
                // folders include YAML by default
                assertEquals(ExitCodes.FAILED, run("validate", dir.resolve("conf")));
                assertTrue(out.contains("settings.prod.yml") && out.contains("2 files"), out);
            } finally {
                org.fxt.freexmltoolkit.di.ServiceRegistry.register(org.fxt.freexmltoolkit.service.SchemaLibraryService.class, original);
            }
        }

        @Test
        @DisplayName("--schemastore: the catalog chooses the schema by file name")
        void schemaStore() throws Exception {
            Path catalogFile = dir.resolve("catalog/catalog.json");
            Files.createDirectories(catalogFile.getParent());
            Files.writeString(catalogFile, """
                    {"schemas": [{"name": "GitHub Workflow", "fileMatch": [".github/workflows/*.yml"],
                                  "url": "https://json.schemastore.org/github-workflow.json"}]}
                    """);
            Files.setLastModifiedTime(catalogFile, java.nio.file.attribute.FileTime.from(java.time.Instant.now()));
            java.util.concurrent.atomic.AtomicInteger downloads = new java.util.concurrent.atomic.AtomicInteger();
            org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog.setShared(
                    new org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog(catalogFile, uri -> {
                        downloads.incrementAndGet();
                        throw new java.io.IOException("offline");
                    }));
            try {
                Path workflow = write(".github/workflows/ci.yml", "on: push\n");
                assertEquals(ExitCodes.OK, run("validate", workflow), out + err);
                // tests do not download: the schema the catalog names is reported as not loaded
                assertNotEquals(ExitCodes.OK, run("validate", "--schemastore", workflow));
                assertTrue(out.contains("GitHub Workflow") && out.contains(".github/workflows/*.yml"), out);
                assertEquals(0, downloads.get(), "a fresh catalog is not downloaded again");
                assertNotEquals(ExitCodes.OK, run("validate", "--schemastore", "--schemastore-url",
                        "https://mirror.example/catalog.json", workflow));
                assertEquals(1, downloads.get());
                assertTrue(err.contains("not updated"), err);
            } finally {
                org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog.setShared(null);
            }
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

        @Test
        @DisplayName("profiles an XQuery, with or without input")
        void profileXQuery() throws Exception {
            Path xq = write("p.xq", """
                    declare function local:twice($x) { $x * 2 };
                    string-join((1 to 3) ! string(local:twice(.)), ',')
                    """);
            Path json = dir.resolve("xq-profile.json");
            assertEquals(ExitCodes.OK, run("transform", "-s", xq, "--profile", json, "--warmup", "0"), err);
            assertTrue(out.contains("2,4,6"), out);
            String report = Files.readString(json);
            assertTrue(report.contains("local:twice#1") && report.contains("(query body)"), report);

            assertEquals(ExitCodes.OK, run("transform", "-s", xq, "--profile", "-", valid), err);
            assertTrue(err.contains("function local:twice#1"), err);
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

        @Test
        @DisplayName("scenario --check compares with the expected result; --accept writes it; JUnit report")
        void check() throws Exception {
            write("sum.xsl", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                      <xsl:template match="/order">
                        <summary created="{current-dateTime()}"><total><xsl:value-of select="sum(item/@price)"/></total></summary>
                      </xsl:template>
                    </xsl:stylesheet>
                    """);
            write("orders/a.xml", "<order><item price=\"1\"/><item price=\"2\"/></order>");
            write("expected/a.xml", "<summary created=\"${xmlunit.isDateTime}\"><total>3</total></summary>");
            var project = org.fxt.freexmltoolkit.service.project.FxtProject.create(dir.resolve("c.fxtproj"), "C");
            var scenario = new org.fxt.freexmltoolkit.service.project.FxtProject.Scenario("sum", "sum.xsl",
                    "${file}", java.util.Map.of(), null, null);
            scenario.expected = "expected/${name}.xml";
            project.putScenario(scenario);
            project.bind(new org.fxt.freexmltoolkit.service.project.FxtProject.SchemaBinding("orders/*.xml", null, null, "sum"));
            project.save();
            Path file = dir.resolve("c.fxtproj");

            assertEquals(ExitCodes.OK, run("scenario", file, "--check"), err + out);
            assertTrue(out.contains("passed   sum") && out.contains("1 passed, 0 failed"), out);
            assertEquals(ExitCodes.OK, run("scenario", file, "--list"), err);
            assertTrue(out.contains("= expected/${name}.xml"), out);

            write("orders/a.xml", "<order><item price=\"1\"/><item price=\"5\"/></order>");
            Path report = dir.resolve("reports/junit.xml");
            assertEquals(ExitCodes.FAILED, run("scenario", file, "--check", "-f", "junit", "-r", report), err + out);
            assertTrue(out.contains("FAILED   sum") && out.contains("Text changed: \"3\" → \"6\""), out);
            String junit = Files.readString(report);
            assertTrue(junit.contains("failures=\"1\"") && junit.contains("<failure message=\"1 difference\""), junit);

            write("orders/b.xml", "<order><item price=\"4\"/></order>");
            assertEquals(ExitCodes.FAILED, run("scenario", file, "--check", "-f", "json"), err + out);
            assertTrue(out.contains("\"status\": \"no_expected\"") && out.contains("\"status\": \"failed\""), out);

            assertEquals(ExitCodes.OK, run("scenario", file, "--accept"), err + out);
            assertTrue(out.contains("accepted sum"), out);
            assertTrue(Files.readString(dir.resolve("expected/a.xml")).contains("${xmlunit.isDateTime}"),
                    "accepting keeps the placeholder");
            // a new expected file has no placeholder yet: the timestamp is fixed until one is put in
            assertEquals(ExitCodes.FAILED, run("scenario", file, "--check"), err + out);
            assertTrue(out.contains("Attribute 'created' changed"), out);
            Path b = dir.resolve("expected/b.xml");
            Files.writeString(b, Files.readString(b).replaceAll("created=\"[^\"]*\"", "created=\"\\${xmlunit.isDateTime}\""));
            assertEquals(ExitCodes.OK, run("scenario", file, "--check"), err + out);
            assertTrue(out.contains("2 passed, 0 failed"), out);

            Path withoutExpected = dir.resolve("e.fxtproj");
            var empty = org.fxt.freexmltoolkit.service.project.FxtProject.create(withoutExpected, "E");
            empty.putScenario(new org.fxt.freexmltoolkit.service.project.FxtProject.Scenario("x", "sum.xsl",
                    "orders/a.xml", java.util.Map.of(), null, null));
            empty.save();
            assertEquals(ExitCodes.USAGE, run("scenario", withoutExpected, "--check"));
            assertEquals(ExitCodes.NOT_RUN, run("scenario", withoutExpected, "x", "--check"), out);
            assertEquals(ExitCodes.USAGE, run("scenario", withoutExpected, "-r", dir.resolve("r.xml")));
        }

        @Test
        @DisplayName("scenario --input runs once per file (named or bound scenario); validate --project uses bound Schematron and JSON Schema")
        void bindings() throws Exception {
            write("x/count.xsl", """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                      <xsl:output method="text"/>
                      <xsl:template match="/"><xsl:value-of select="count(//i)"/></xsl:template>
                    </xsl:stylesheet>
                    """);
            write("data/a.xml", "<r><i/></r>");
            write("data/b.xml", "<r><i/><i/></r>");
            write("other/c.xml", "<r/>");
            write("rules.sch", """
                    <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                      <pattern><rule context="r"><assert test="count(i) le 1">at most one i</assert></rule></pattern>
                    </schema>
                    """);
            write("j.schema.json", "{ \"type\": \"object\", \"required\": [\"id\"] }");
            write("data/d.json", "{ \"name\": \"x\" }");
            var project = org.fxt.freexmltoolkit.service.project.FxtProject.create(dir.resolve("p.fxtproj"), "P");
            project.putScenario(new org.fxt.freexmltoolkit.service.project.FxtProject.Scenario("count", "x/count.xsl",
                    "${file}", java.util.Map.of(), null, "out/${name}.txt"));
            project.bind(new org.fxt.freexmltoolkit.service.project.FxtProject.SchemaBinding("data/*.xml", null,
                    "rules.sch", "count"));
            project.bind(new org.fxt.freexmltoolkit.service.project.FxtProject.SchemaBinding("**/*.json",
                    "j.schema.json", null, null));
            project.save();
            Path file = dir.resolve("p.fxtproj");

            assertEquals(ExitCodes.OK, run("scenario", file, "count", "--input", dir.resolve("data")), err);
            assertEquals("1", Files.readString(dir.resolve("out/a.txt")));
            assertEquals("2", Files.readString(dir.resolve("out/b.txt")));
            assertEquals(ExitCodes.FAILED, run("scenario", file, "-i", dir.resolve("data/a.xml"), dir.resolve("other/c.xml")),
                    "c.xml has no bound scenario: " + err);
            assertTrue(err.contains("no scenario is bound"), err);

            assertEquals(ExitCodes.OK, run("validate", "--project", file, dir.resolve("data/a.xml")), out);
            assertEquals(ExitCodes.FAILED, run("validate", "--project", file, dir.resolve("data/b.xml")), out);
            assertTrue(out.contains("at most one i"), out);
            assertEquals(ExitCodes.FAILED, run("validate", "--project", file, dir.resolve("data/d.json")), out);
            assertEquals(ExitCodes.OK, run("validate", dir.resolve("data/d.json")), "without the project: well-formed only");
        }
    }

    @Nested
    @DisplayName("DTD and RELAX NG")
    class Grammars {

        @Test
        @DisplayName("-x takes .dtd/.rng/.rnc; a declared DOCTYPE is used; --no-schema-lookup skips it")
        void grammars() throws Exception {
            write("book.dtd", "<!ELEMENT book (title)>\n<!ELEMENT title (#PCDATA)>\n");
            Path declared = write("declared.xml", "<!DOCTYPE book SYSTEM \"book.dtd\">\n<book><chapter/></book>");
            assertEquals(ExitCodes.FAILED, run("validate", declared), out);
            assertTrue(out.contains("DTD"), out);
            assertEquals(ExitCodes.OK, run("validate", "--no-schema-lookup", declared), out);

            Path rnc = write("book.rnc", "start = element book { element title { text } }\n");
            Path plain = write("plain.xml", "<book><title>T</title></book>");
            assertEquals(ExitCodes.OK, run("validate", "-x", rnc, plain), out);
            Path wrong = write("wrong.xml", "<book><para/></book>");
            assertEquals(ExitCodes.FAILED, run("validate", "--schema", rnc, wrong), out);
            assertTrue(out.contains("RELAX NG"), out);
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
        @DisplayName("JSON and YAML files by their data, also JSON against YAML")
        void dataFiles() throws Exception {
            Path a = write("a.yaml", "# deploy\nname: api\nreplicas: 2\nports: [80, 443]\n");
            Path b = write("b.json", "{\"ports\": [80, 443], \"replicas\": 2.0, \"name\": \"api\"}");
            Path c = write("c.yml", "name: api\nreplicas: 3\nports:\n  - 80\n  - 8443\n  - 443\n");
            assertEquals(ExitCodes.OK, run("compare", a, b), err);
            assertTrue(out.contains("same data"), out);
            assertEquals(ExitCodes.FAILED, run("compare", a, c), err);
            assertTrue(out.contains(a + ":3: Member 'replicas' changed: \"2\" → \"3\" ($.replicas)"), out);
            assertTrue(out.contains("Item 1 added"), out);
            assertTrue(out.contains("2 differences"), out);
            assertEquals(ExitCodes.FAILED, run("compare", "-f", "json", a, c), err);
            assertTrue(out.contains("\"node\": \"member\""), out);
            assertEquals(ExitCodes.USAGE, run("compare", "--ignore", "//x", a, c));
            Path broken = write("broken.yaml", "a: [1");
            assertEquals(ExitCodes.NOT_RUN, run("compare", a, broken));
        }

        @Test
        @DisplayName("placeholders in LEFT, --ignore, --tolerance; similar differences are marked")
        void xmlunitOptions() throws Exception {
            Path expected = write("expected.xml", """
                    <report created="${xmlunit.isDateTime}" run="${xmlunit.ignore}">
                      <total>${xmlunit.isNumber}</total><avg>3.33</avg><stamp>x</stamp>
                    </report>""");
            Path actual = write("actual.xml", """
                    <report created="2026-11-09T10:00:00" run="77">
                      <total>12</total><avg>3.334</avg><stamp>y</stamp>
                    </report>""");
            assertEquals(ExitCodes.FAILED, run("compare", expected, actual), err);
            assertTrue(out.contains("2 differences"), out);
            assertEquals(ExitCodes.OK, run("compare", expected, actual, "--ignore", "//stamp", "--tolerance", "0.01"), err);
            assertEquals(ExitCodes.FAILED, run("compare", expected, actual, "--ignore", "//stamp", "--tolerance", "0.01",
                    "--no-placeholders"), err);
            assertEquals(ExitCodes.USAGE, run("compare", expected, actual, "--ignore", "//["));
            assertTrue(err.contains("--ignore"), err);
            assertEquals(ExitCodes.USAGE, run("compare", expected, actual, "--tolerance", "-1"));

            Path moved = write("moved.xml", "<r><b/><a/></r>");
            Path original = write("original.xml", "<r><a/><b/></r>");
            assertEquals(ExitCodes.FAILED, run("compare", original, moved, "-f", "json"), err);
            assertTrue(out.contains("\"similar\": true"), out);
            assertEquals(ExitCodes.FAILED, run("compare", original, moved), err);
            assertTrue(out.contains("[similar]") && out.contains("similar)"), out);
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

        @Test
        @DisplayName("folders: JSON and YAML files by their data")
        void dataFolders() throws Exception {
            write("dl/c.yaml", "# c\nname: api\nport: 80\n");
            write("dr/c.yaml", "port: 80\nname: \"api\"\n");
            write("dl/d.json", "{\"a\": 1}");
            write("dr/d.json", "{\n  \"a\": 2\n}");
            assertEquals(ExitCodes.FAILED, run("compare", dir.resolve("dl"), dir.resolve("dr")), err);
            assertFalse(out.contains("c.yaml"), out);
            assertTrue(out.contains("differs    d.json (1 data difference)"), out);
            assertTrue(out.contains("1 equivalent"), out);
            Files.writeString(dir.resolve("dr/d.json"), "{\"a\" : 1}");
            assertEquals(ExitCodes.OK, run("compare", dir.resolve("dl"), dir.resolve("dr")), err);
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
        @DisplayName("--processor runs an external XSLT processor from the settings; --list-processors lists them")
        void externalProcessor() throws Exception {
            var store = org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore.settings();
            var fake = org.fxt.freexmltoolkit.service.xsltproc.ExternalXsltRunnerTest.fake("ok", 60);
            store.save(java.util.List.of(fake));
            try {
                Path xsl = write("v1.xsl", XSLT.replace("version=\"3.0\"", "version=\"1.0\"").replace("sum(//n)", "count(//n)"));
                assertEquals(ExitCodes.OK, run("transform", "-s", xsl, "-p", "label=n", "--processor", fake.name(), valid), err);
                assertEquals("n=1", out.strip());
                assertTrue(err.contains("Message: started"), "the program's messages: " + err);
                assertEquals(ExitCodes.OK, run("transform", "--list-processors"), err);
                assertTrue(out.contains("builtin") && out.contains(fake.name()), out);
                assertEquals(ExitCodes.USAGE, run("transform", "-s", xsl, "--processor", "No Such Processor", valid));
                assertTrue(err.contains("No XSLT processor named"), err);
                assertEquals(ExitCodes.USAGE, run("transform", "-s", xsl, "--processor", fake.name(), "--profile", "-", valid));
                assertEquals(ExitCodes.USAGE, run("transform", "-s", xsl, "--processor", fake.name(), "--lang-version", "1.0", valid));
            } finally {
                store.save(java.util.List.of());
            }
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
        @DisplayName("Word documentation, diagrams can be left out")
        void word() throws Exception {
            Path docx = dir.resolve("doc.docx");
            assertEquals(ExitCodes.OK, run("xsd-doc", xsd, "-o", docx), err);
            assertTrue(mediaCount(docx) > 0, "diagrams as images");
            Path plain = dir.resolve("plain.docx");
            assertEquals(ExitCodes.OK, run("xsd-doc", xsd, "-o", plain, "-f", "docx",
                    "--no-schema-diagram", "--no-element-diagrams"), err);
            assertEquals(0, mediaCount(plain));
        }

        private long mediaCount(Path docx) throws Exception {
            try (var zip = new java.util.zip.ZipFile(docx.toFile())) {
                return zip.stream().filter(e -> e.getName().startsWith("word/media/")).count();
            }
        }

        @Test
        @DisplayName("--list-languages prints the xml:lang values; --language filters the site")
        void languages() throws Exception {
            Path schema = write("lang.xsd", LANG_XSD);
            assertEquals(ExitCodes.OK, run("xsd-doc", schema, "--list-languages"), err);
            assertEquals(List.of("de", "en"), out.lines().filter(l -> !l.equals("default")).toList());

            Path site = dir.resolve("de-site");
            assertEquals(ExitCodes.OK, run("xsd-doc", schema, "-o", site, "--language", "de"), err);
            String languages = Files.readString(site.resolve("languages.json"));
            assertTrue(languages.contains("\"de\""), languages);
            assertFalse(languages.contains("\"en\""), languages);
        }

        @Test
        @DisplayName("options of another format are ignored with a warning; bad values are usage errors")
        void optionChecks() {
            Path site = dir.resolve("site");
            assertEquals(ExitCodes.OK, run("xsd-doc", xsd, "-o", site, "--watermark", "draft"), err);
            assertTrue(err.contains("--watermark does not apply to html output"), err);
            assertEquals(ExitCodes.USAGE, run("xsd-doc", xsd, "-o", dir.resolve("a.pdf"), "--font-size", "20"));
            assertTrue(err.contains("--font-size must be between 9 and 12"), err);
            assertEquals(ExitCodes.USAGE, run("xsd-doc", xsd));
            assertTrue(err.contains("--output"), err);
        }

        @Test
        @DisplayName("the command line maps onto the generator options")
        void optionMapping() {
            var commandLine = FxtCli.newCommandLine(new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()));
            var parsed = commandLine.parseArgs("xsd-doc", xsd.toString(), "-o", "x.pdf", "--landscape",
                    "--watermark", "confidential", "--color-scheme", "green", "--font-size", "9", "--no-toc",
                    "--markdown", "marked", "--image-format", "png", "--element-reference", "all",
                    "--word-header-style", "colorful", "--language", "de,en");
            XsdDocCommand command = parsed.subcommand().commandSpec().commandLine().getCommand();
            var options = command.toOptions(XsdDocCommand.DocFormat.PDF);
            var pdf = options.pdf();
            assertEquals(org.fxt.freexmltoolkit.domain.PdfDocumentationConfig.Orientation.LANDSCAPE, pdf.getOrientation());
            assertEquals(org.fxt.freexmltoolkit.domain.PdfDocumentationConfig.Watermark.CONFIDENTIAL, pdf.getWatermark());
            assertEquals(org.fxt.freexmltoolkit.domain.PdfDocumentationConfig.ColorScheme.GREEN, pdf.getColorScheme());
            assertEquals(org.fxt.freexmltoolkit.domain.PdfDocumentationConfig.FontSize.PT_9, pdf.getFontSize());
            assertFalse(pdf.isIncludeToc());
            assertFalse(options.word().isIncludeToc());
            assertTrue(pdf.isIncludeSchemaDiagram(), "on unless --no-schema-diagram");
            assertFalse(pdf.isVectorDiagrams(), "png diagrams");
            assertEquals(org.fxt.freexmltoolkit.domain.PdfDocumentationConfig.ElementReference.ALL_OCCURRENCES,
                    pdf.getElementReference());
            assertEquals(org.fxt.freexmltoolkit.domain.WordDocumentationConfig.HeaderStyle.COLORFUL,
                    options.word().getHeaderStyle());
            assertEquals(org.fxt.freexmltoolkit.service.XsdDocumentationService.MarkdownMode.PER_NODE, options.markdown());
            assertEquals(java.util.Set.of("de", "en"), options.languages());
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
            assertEquals(ExitCodes.USAGE, run("fop", "--fo", fo), "the PDF is required unless listing processors");

            assertEquals(ExitCodes.OK, run("fop", "--fo", fo, "-o", pdf, "--processor", "builtin"), err);
            assertEquals(ExitCodes.FAILED, run("fop", "--fo", fo, "-o", dir.resolve("out/none.pdf"),
                    "--processor", "No Such Formatter"));
            assertTrue(err.contains("No Such Formatter"), err);
            assertEquals(ExitCodes.OK, run("fop", "--list-processors"), err);
            assertTrue(out.contains("builtin"), out);
        }
    }

    @Nested
    @DisplayName("query")
    class Query {

        @Test
        @DisplayName("XPath on files and folders; exit 0 found, 1 empty, 3 broken; file names with several inputs")
        void xpath() throws Exception {
            Path a = write("q/a.xml", "<r><i>1</i><i>2</i><i>3</i></r>");
            Path b = write("q/b.xml", "<r><i>9</i></r>");
            assertEquals(ExitCodes.OK, run("query", "-e", "count(//i)", a), err);
            assertEquals("3\n", out);
            assertEquals(ExitCodes.OK, run("query", "-e", "//i[. > 2]/string()", dir.resolve("q")), err);
            assertEquals(a + ": 3\n" + b + ": 9\n", out);
            assertEquals(ExitCodes.OK, run("query", "-e", "count(//i)", "--no-with-filename", a, b), err);
            assertEquals("3\n1\n", out);
            assertEquals(ExitCodes.FAILED, run("query", "-e", "//missing", a), err);
            assertEquals("", out);
            assertEquals(ExitCodes.NOT_RUN, run("query", "-e", "count(//i", a));
            assertTrue(err.contains("error:"), err);
            Path broken = write("q2/broken.xml", "<r>");
            assertEquals(ExitCodes.NOT_RUN, run("query", "-e", "count(//i)", broken, a));
            assertTrue(out.contains("3") && err.contains(broken + ": error:"), out + " / " + err);
        }

        @Test
        @DisplayName("XQuery inline, from a file with --var, and without input")
        void xquery() throws Exception {
            Path a = write("x/a.xml", "<r><i>1</i><i>2</i></r>");
            assertEquals(ExitCodes.OK, run("query", "-l", "xquery", "-e", "sum(for $i in //i return xs:int($i))", a), err);
            assertEquals("3\n", out);
            Path q = write("x/label.xq", "declare variable $label external; $label || '=' || count(//i)");
            assertEquals(ExitCodes.OK, run("query", "--query-file", q, "--var", "label=items", a), err);
            assertEquals("items=2\n", out);
            assertEquals(ExitCodes.OK, run("query", "-l", "xquery", "-e", "1 + 1"), err);
            assertEquals("2\n", out);
            Path broken = write("x/broken.xml", "<r>");
            assertEquals(ExitCodes.NOT_RUN, run("query", "-l", "xquery", "-e", "count(//i)", broken));
            assertTrue(err.contains("error:"), err);
        }

        @Test
        @DisplayName("JSONPath for YAML files; XPath on YAML is refused")
        void yamlInputs() throws Exception {
            Path yaml = write("y/ci.yml", "# a workflow\njobs:\n  build: {runs-on: ubuntu-latest}\n  test:\n    runs-on: windows-latest\n");
            assertEquals(ExitCodes.OK, run("query", "-e", "$.jobs.*.runs-on", yaml), err);
            assertTrue(out.contains("ubuntu-latest") && out.contains("windows-latest"), out);
            assertEquals(ExitCodes.OK, run("query", "-e", "$.jobs.build.runs-on", dir.resolve("y")), err);
            assertTrue(out.contains("ubuntu-latest"), out);
            assertEquals(ExitCodes.NOT_RUN, run("query", "-l", "xpath", "-e", "//jobs", yaml));
            assertTrue(err.contains("JSONPath"), err);
            Path broken = write("y/broken.yaml", "a: [1, 2\n");
            assertEquals(ExitCodes.NOT_RUN, run("query", "-e", "$.a", broken));
        }

        @Test
        @DisplayName("JSONPath for .json, standard input, JSON report, output file, usage errors")
        void moreInputsAndOutputs() throws Exception {
            Path json = write("j/d.json", "{\"items\": [{\"name\": \"pen\"}, {\"name\": \"ink\"}]}");
            assertEquals(ExitCodes.OK, run("query", "-e", "$.items[*].name", json), err);
            assertTrue(out.contains("pen") && out.contains("ink"), out);

            QueryCommand.stdin = () -> new java.io.ByteArrayInputStream("<r><t>hello</t></r>".getBytes(StandardCharsets.UTF_8));
            try {
                assertEquals(ExitCodes.OK, run("query", "-e", "//t/string()", "-"), err);
                assertEquals("hello\n", out);
            } finally {
                QueryCommand.stdin = () -> System.in;
            }

            Path a = write("j/a.xml", "<r><i/></r>");
            assertEquals(ExitCodes.OK, run("query", "-e", "count(//i)", "-f", "json", a), err);
            assertTrue(out.contains("\"result\": \"1\"") && out.contains("\"file\""), out);
            Path target = dir.resolve("out/result.txt");
            assertEquals(ExitCodes.OK, run("query", "-e", "count(//i)", "-o", target, a), err);
            assertEquals("1\n", Files.readString(target));

            assertEquals(ExitCodes.USAGE, run("query", a));
            assertEquals(ExitCodes.USAGE, run("query", "-e", "1"));
            assertEquals(ExitCodes.USAGE, run("query", "-e", "1", "--var", "x=1", a));
        }
    }

    @Nested
    @DisplayName("help")
    class Help {

        private static final List<String> COMMANDS = List.of(
                "validate", "transform", "query", "xsd-doc", "convert", "compare", "merge", "xspec", "scenario", "fop",
                "sign", "verify", "encrypt", "decrypt");

        @Test
        @DisplayName("every command has -h/--help with a description, options and examples")
        void everyCommand() {
            for (String command : COMMANDS) {
                for (String flag : List.of("--help", "-h")) {
                    assertEquals(ExitCodes.OK, run(command, flag), command + " " + flag + ": " + err);
                    assertTrue(out.contains("Usage: fxt-cli " + command), command + ":\n" + out);
                    assertTrue(out.contains("Options:"), command + ":\n" + out);
                    assertTrue(out.contains("Examples:"), command + ":\n" + out);
                    assertTrue(out.contains("fxt-cli " + command + " "), "an example of " + command);
                }
                String viaFlag = out;
                assertEquals(ExitCodes.OK, run("help", command));
                assertEquals(viaFlag, out, "help " + command);
            }
        }

        @Test
        @DisplayName("the usage-error hint names a working --help")
        void hintWorks() {
            assertEquals(ExitCodes.USAGE, run("xsd-doc", xsd, "--no-such-option"));
            assertTrue(err.contains("Try 'fxt-cli xsd-doc --help'"), err);
            assertEquals(ExitCodes.OK, run("xsd-doc", "--help"));
        }

        @Test
        @DisplayName("the overview lists the commands and how to get more help")
        void overview() {
            assertEquals(ExitCodes.OK, run("--help"));
            for (String command : COMMANDS) {
                assertTrue(out.contains("  " + command + " "), command + " in\n" + out);
            }
            assertTrue(out.contains("fxt-cli <command> --help"), out);
            assertTrue(out.contains("Exit codes:"), out);
        }

        @Test
        @DisplayName("xsd-doc groups its options and shows defaults")
        void xsdDocGroups() {
            assertEquals(ExitCodes.OK, run("xsd-doc", "--help"));
            for (String heading : List.of("Content:", "Diagrams:", "HTML site:", "PDF and Word:", "PDF only:", "Word only:")) {
                assertTrue(out.contains(heading), heading + " in\n" + out);
            }
            assertTrue(out.contains("--[no-]toc"), out);
            assertTrue(out.contains("Default: blue"), out);
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
