package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.XSpecRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The XSpec templates of File → New (type XML, saved as {@code .xspec}): offered for XML, rendered
 * with their defaults they are XSpec descriptions, and each one runs green against matching code.
 */
class XSpecTemplatesTest {

    @TempDir
    Path dir;

    private static XmlTemplate template(String name) {
        return TemplateRepository.getInstance().getAllTemplates().stream()
                .filter(t -> name.equals(t.getName())).findFirst()
                .orElseThrow(() -> new AssertionError(name + " is not a built-in template"));
    }

    @Test
    void offeredForXmlWithTheXSpecExtension() {
        for (String name : new String[] {"XSpec test (XSLT)", "XSpec test (XQuery)", "XSpec test (Schematron)"}) {
            XmlTemplate t = template(name);
            assertTrue(NewFileDialog.isDocumentTemplate(t), name);
            assertTrue(NewFileDialog.matchesType(t, EditorFileType.XML), name);
            assertFalse(NewFileDialog.matchesType(t, EditorFileType.XSLT), name);
            assertEquals(java.util.Set.of("xspec"), t.getFileExtensions());
            assertTrue(XSpecRunner.isXSpec(TemplateRunner.render(t, Map.of())), name);
        }
    }

    @Test
    void xsltTemplateRunsGreen() throws Exception {
        Files.writeString(dir.resolve("my-stylesheet.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="item"><result><xsl:value-of select="."/></result></xsl:template>
                </xsl:stylesheet>
                """);
        assertGreen(TemplateRunner.render(template("XSpec test (XSLT)"), Map.of()));
    }

    @Test
    void xqueryTemplateRunsGreen() throws Exception {
        Files.writeString(dir.resolve("my-module.xqm"), """
                module namespace m = "urn:my-module";
                declare function m:my-function($x) { $x };
                """);
        assertGreen(TemplateRunner.render(template("XSpec test (XQuery)"), Map.of()));
    }

    @Test
    void schematronTemplateRunsGreen() throws Exception {
        Files.writeString(dir.resolve("my-rules.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern><rule context="item">
                    <assert id="item-qty" test="@qty">An item needs a quantity.</assert>
                  </rule></pattern>
                </schema>
                """);
        assertGreen(TemplateRunner.render(template("XSpec test (Schematron)"), Map.of()));
    }

    private void assertGreen(String xspec) throws Exception {
        Path file = dir.resolve("test.xspec");
        Files.writeString(file, xspec);
        XSpecRunner.Result result = new XSpecRunner().run(xspec, file);
        assertNull(result.error(), result::error);
        assertTrue(result.count(XSpecRunner.Status.PASSED) > 0, result::summary);
        assertEquals(0, result.count(XSpecRunner.Status.FAILED), result::summary);
    }
}
