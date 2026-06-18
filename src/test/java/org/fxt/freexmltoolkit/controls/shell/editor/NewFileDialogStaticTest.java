package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.Test;

/**
 * Tests for the pure (UI-free) helpers of {@link NewFileDialog}: file-type inference
 * from content and the template-to-type matching used to filter the template combo.
 */
class NewFileDialogStaticTest {

    @Test
    void inferTypeRecognizesRootConstructs() {
        assertEquals(EditorFileType.XSD, NewFileDialog.inferType(
                "<?xml version=\"1.0\"?><xs:schema xmlns:xs=\"...\"></xs:schema>"));
        assertEquals(EditorFileType.XSLT, NewFileDialog.inferType(
                "<xsl:stylesheet version=\"3.0\"></xsl:stylesheet>"));
        assertEquals(EditorFileType.SCHEMATRON, NewFileDialog.inferType(
                "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"></schema>"));
        assertEquals(EditorFileType.JSON, NewFileDialog.inferType("{ \"a\": 1 }"));
        assertEquals(EditorFileType.JSON, NewFileDialog.inferType("[1, 2, 3]"));
        assertEquals(EditorFileType.XML, NewFileDialog.inferType("<root><child/></root>"));
        assertEquals(EditorFileType.XML, NewFileDialog.inferType(null));
    }

    @Test
    void matchesTypeUsesDeclaredExtensionsWhenPresent() {
        XmlTemplate t = new XmlTemplate("X", "<anything/>", "Custom");
        t.setFileExtensions(Set.of("xsd"));
        assertTrue(NewFileDialog.matchesType(t, EditorFileType.XSD));
        assertFalse(NewFileDialog.matchesType(t, EditorFileType.XML));
    }

    @Test
    void matchesTypeFallsBackToContentHeuristic() {
        XmlTemplate xslt = new XmlTemplate("T", "<xsl:stylesheet version=\"3.0\"/>", "Transformation");
        assertTrue(NewFileDialog.matchesType(xslt, EditorFileType.XSLT));
        assertFalse(NewFileDialog.matchesType(xslt, EditorFileType.XML));

        XmlTemplate xml = new XmlTemplate("E", "<order><id/></order>", "Basic");
        assertTrue(NewFileDialog.matchesType(xml, EditorFileType.XML));
        assertFalse(NewFileDialog.matchesType(xml, EditorFileType.XSD));
    }

    @Test
    void matchesTypeIsNullSafe() {
        assertFalse(NewFileDialog.matchesType(null, EditorFileType.XML));
        assertFalse(NewFileDialog.matchesType(new XmlTemplate("A", "<a/>", "c"), null));
    }
}
