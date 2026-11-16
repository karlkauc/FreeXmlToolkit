package org.fxt.freexmltoolkit.plugins.regreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Grid formulas and filters inserted into and removed from the text, keeping everything else. */
class GridAnnotationsTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- <Fund> in a comment -->
            <r:Report xmlns:r="urn:x">
              <Fund><Rate>1</Rate></Fund>
              <Fund/>
              <Other><![CDATA[<Fund>]]></Other>
            </r:Report>
            """;

    @Test
    void addAfterTheStartTagWithItsIndentationAndRemoveAgain() {
        List<GridAnnotations.Annotation> list = List.of(
                GridAnnotations.Annotation.formula("Report", "Count", "count(Fund) > 1"),
                GridAnnotations.Annotation.filter("Report", "Fund[Rate = \"1\"]"),
                GridAnnotations.Annotation.formula("Fund", "Sum", "sum(Rate)"));
        String added = GridAnnotations.add(XML, list);
        assertTrue(added.contains("<r:Report xmlns:r=\"urn:x\">\n  <?fxt-filter expression=\"Fund[Rate = &quot;1&quot;]\"?>\n"
                + "  <?fxt-formula name=\"Count\" store=\"false\" expression=\"count(Fund) &gt; 1\"?>\n  <Fund>"), added);
        assertTrue(added.contains("<Fund><?fxt-formula name=\"Sum\" store=\"false\" expression=\"sum(Rate)\"?><Rate>"), added);
        assertTrue(added.contains("<!-- <Fund> in a comment -->") && added.contains("<![CDATA[<Fund>]]>"), "comments and CDATA untouched");
        assertTrue(added.contains("<Fund/>"), "an empty element gets nothing");
        assertTrue(GridAnnotations.has(added));
        assertEquals(added, GridAnnotations.add(added, list), "idempotent");
        assertEquals(XML, GridAnnotations.remove(added), "byte for byte as before");
        assertFalse(GridAnnotations.has(XML));
    }
}
