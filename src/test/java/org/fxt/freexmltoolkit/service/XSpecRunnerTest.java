package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.XSpecRunner.Kind;
import org.fxt.freexmltoolkit.service.XSpecRunner.Result;
import org.fxt.freexmltoolkit.service.XSpecRunner.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs real XSpec tests for XSLT, XQuery and Schematron. */
@DisplayName("XSpecRunner")
class XSpecRunnerTest {

    @TempDir
    Path dir;

    private final XSpecRunner runner = new XSpecRunner();

    @Test
    @DisplayName("XSLT: passing, failing and pending tests, nested scenarios, HTML and JUnit reports")
    void xslt() throws Exception {
        Files.writeString(dir.resolve("greet.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xsl:function name="f:greet" as="xs:string">
                    <xsl:param name="name" as="xs:string"/>
                    <xsl:sequence select="'Hello ' || $name"/>
                  </xsl:function>
                  <xsl:template match="person"><p><xsl:value-of select="f:greet(@name)"/></p></xsl:template>
                </xsl:stylesheet>
                """);
        String xspec = """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="greet.xsl">
                  <x:scenario label="greet">
                    <x:scenario label="a name">
                      <x:call function="f:greet"><x:param select="'Ada'"/></x:call>
                      <x:expect label="says hello" select="'Hello Ada'"/>
                      <x:expect label="is wrong on purpose" select="'Goodbye Ada'"/>
                    </x:scenario>
                  </x:scenario>
                  <x:scenario label="template">
                    <x:context><person name="Bob"/></x:context>
                    <x:expect label="a paragraph"><p>Hello Bob</p></x:expect>
                  </x:scenario>
                  <x:scenario label="later" pending="not written yet">
                    <x:call function="f:greet"><x:param select="'x'"/></x:call>
                    <x:expect label="pending" select="'?'"/>
                  </x:scenario>
                </x:description>
                """;
        Path file = Files.writeString(dir.resolve("greet.xspec"), xspec);
        Result result = runner.run(xspec, file);
        assertNull(result.error(), result.error());
        assertEquals(Kind.XSLT, result.kind());
        assertEquals("4 tests: 2 passed, 1 failed, 1 pending", result.summary());
        assertFalse(result.passed());
        var greet = result.scenarios().get(0);
        assertEquals("greet", greet.label());
        var wrong = greet.scenarios().get(0).tests().stream().filter(t -> t.status() == Status.FAILED).findFirst().orElseThrow();
        assertEquals("is wrong on purpose", wrong.label());
        assertTrue(wrong.actual().contains("Hello Ada"), wrong.actual());
        assertTrue(result.scenarios().get(2).pending());

        String html = runner.html(result);
        assertTrue(html.contains("<html") && html.contains("is wrong on purpose"), html.substring(0, Math.min(300, html.length())));
        String junit = runner.junit(result);
        assertTrue(junit.contains("<testsuite") && junit.contains("failure"), junit);
    }

    @Test
    @DisplayName("XQuery and Schematron tests run too")
    void xqueryAndSchematron() throws Exception {
        Files.writeString(dir.resolve("math.xqm"), """
                module namespace m = "urn:m";
                declare function m:double($n as xs:integer) as xs:integer { $n * 2 };
                """);
        String xq = """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:m="urn:m"
                               query="urn:m" query-at="math.xqm">
                  <x:scenario label="double">
                    <x:call function="m:double"><x:param select="21"/></x:call>
                    <x:expect label="42" select="42"/>
                  </x:scenario>
                </x:description>
                """;
        Result query = runner.run(xq, Files.writeString(dir.resolve("math.xspec"), xq));
        assertNull(query.error(), query.error());
        assertEquals(Kind.XQUERY, query.kind());
        assertTrue(query.passed(), query.summary());

        Files.writeString(dir.resolve("rules.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern><rule context="order"><assert id="has-id" test="@id">an order needs an id</assert></rule></pattern>
                </schema>
                """);
        String sch = """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" schematron="rules.sch">
                  <x:scenario label="order without id">
                    <x:context><order/></x:context>
                    <x:expect-assert id="has-id"/>
                  </x:scenario>
                  <x:scenario label="order with id">
                    <x:context><order id="1"/></x:context>
                    <x:expect-valid/>
                  </x:scenario>
                </x:description>
                """;
        Result schematron = runner.run(sch, Files.writeString(dir.resolve("rules.xspec"), sch));
        assertNull(schematron.error(), schematron.error());
        assertEquals(Kind.SCHEMATRON, schematron.kind());
        assertEquals("2 tests: 2 passed, 0 failed, 0 pending", schematron.summary());
    }

    @Test
    @DisplayName("errors: not XSpec, a stylesheet that does not compile")
    void errors() throws Exception {
        assertNotNull(runner.run("<a/>", dir.resolve("a.xspec")).error());
        Files.writeString(dir.resolve("bad.xsl"), "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:bogus/></xsl:stylesheet>");
        String xspec = """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" stylesheet="bad.xsl">
                  <x:scenario label="s"><x:context><a/></x:context><x:expect label="e" select="1"/></x:scenario>
                </x:description>
                """;
        Result result = runner.run(xspec, Files.writeString(dir.resolve("bad.xspec"), xspec));
        assertNotNull(result.error());
        assertTrue(result.summary().startsWith("The tests did not run"), result.summary());
        assertTrue(XSpecRunner.isXSpec(xspec));
    }
}
