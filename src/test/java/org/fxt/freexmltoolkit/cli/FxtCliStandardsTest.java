package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The --lang-version / --xsd-version / --xml-version / --schematron-binding options of fxt-cli. */
class FxtCliStandardsTest {

    private static final String XSD11 = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence><xs:element name="n" type="xs:int"/></xs:sequence>
                  <xs:assert test="n gt 0"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;
    private String out;
    private String err;

    private Path write(String name, String text) throws Exception {
        Path file = dir.resolve(name);
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

    @Test
    void queryLangVersionRejectsNewerXPath() throws Exception {
        Path xml = write("a.xml", "<r><a>1</a><a>2</a></r>");
        assertEquals(ExitCodes.OK, run("query", "--lang-version", "3.1", "-e", "head(//a)/string()", xml), err);
        assertEquals("1", out.strip());
        assertEquals(ExitCodes.NOT_RUN, run("query", "--lang-version", "2.0", "-e", "head(//a)/string()", xml));
        assertTrue(err.contains("head"), err);
        assertEquals(ExitCodes.OK, run("query", "--lang-version", "1.0", "-e", "count(//a)", xml), err);
        assertEquals("2", out.strip());
    }

    @Test
    void queryLangVersionForXQuery() throws Exception {
        Path xml = write("a.xml", "<r><a>1</a></r>");
        assertEquals(ExitCodes.NOT_RUN, run("query", "-l", "xquery", "--lang-version", "1.0", "-e",
                "for $a in //a group by $k := $a return $k", xml));
        assertTrue(err.toLowerCase(java.util.Locale.ROOT).contains("group by"), err);
    }

    @Test
    void unknownVersionIsAUsageError() throws Exception {
        Path xml = write("a.xml", "<r/>");
        assertEquals(ExitCodes.USAGE, run("query", "--lang-version", "4.2", "-e", "1", xml));
        assertTrue(err.contains("1.0, 2.0, 3.0, 3.1"), err);
        assertEquals(ExitCodes.USAGE, run("query", "-l", "jsonpath", "--lang-version", "1.0", "-e", "$", xml));
    }

    @Test
    void transformLangVersion() throws Exception {
        Path xml = write("a.xml", "<r><a>1</a></r>");
        Path xsl = write("s.xsl", """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="upper-case('x')"/></xsl:template>
                </xsl:stylesheet>
                """);
        assertEquals(ExitCodes.OK, run("transform", xml, "-s", xsl, "--lang-version", "2.0"), err);
        assertEquals("X", out.strip());
        assertEquals(ExitCodes.FAILED, run("transform", xml, "-s", xsl, "--lang-version", "1.0"));
        assertTrue(err.contains("XSLT 1.0"), err);
    }

    @Test
    void validateXsdVersion() throws Exception {
        Path xsd = write("s.xsd", XSD11);
        Path xml = write("v.xml", "<root><n>1</n></root>");
        assertEquals(ExitCodes.OK, run("validate", "-x", xsd, xml), out + err);
        assertEquals(ExitCodes.OK, run("validate", "-x", xsd, "--xsd-version", "1.1", xml), out + err);
        int code = run("validate", "-x", xsd, "--xsd-version", "1.0", xml);
        assertTrue(code != ExitCodes.OK, out);
        assertTrue((out + err).contains("XSD 1.0 is selected"), out + err);
    }

    @Test
    void validateXmlVersion() throws Exception {
        Path xml = write("v11.xml", "<?xml version=\"1.1\"?><root/>");
        assertEquals(ExitCodes.OK, run("validate", xml), out + err);
        assertEquals(ExitCodes.FAILED, run("validate", "--xml-version", "1.0", xml));
        assertTrue(out.contains("XML 1.0 is selected"), out);
    }

    @Test
    void validateSchematronBinding() throws Exception {
        Path xml = write("d.xml", "<r><a>1</a></r>");
        Path sch = write("r.sch", """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <pattern><rule context="r"><assert test="exists(head(a))">a</assert></rule></pattern>
                </schema>
                """);
        assertEquals(ExitCodes.OK, run("validate", "-s", sch, xml), out + err);
        assertEquals(ExitCodes.FAILED, run("validate", "-s", sch, "--schematron-binding", "xslt2", xml));
        assertTrue(out.contains("head"), out);
    }
}
