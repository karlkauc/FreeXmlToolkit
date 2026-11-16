package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.fxt.freexmltoolkit.plugin.ElementDescriber;
import org.junit.jupiter.api.Test;

/** The field-label hover: which element a tag name belongs to and what the describers say about it. */
class DescriberHoverTest {

    private static final String XML = "<r:Report xmlns:r=\"urn:x\">\n  <Fund id=\"1\"><Rate>5</Rate></Fund>\n  <Fund><Rate>6</Rate></Fund>\n</r:Report>";

    @Test
    void tagNamesMapToTheElementPath() {
        assertEquals(List.of("Report", "Fund", "Rate"), DescriberHover.pathAt(XML, XML.lastIndexOf("<Rate>") + 2));
        assertEquals(List.of("Report", "Fund", "Rate"), DescriberHover.pathAt(XML, XML.lastIndexOf("</Rate>") + 3), "end tag");
        assertEquals(List.of("Report"), DescriberHover.pathAt(XML, 3), "prefixed root");
        assertNull(DescriberHover.pathAt(XML, XML.indexOf(">5<") + 1), "element text");
        assertNull(DescriberHover.pathAt(XML, XML.indexOf("id=") + 1), "attribute name");
        assertEquals(List.of("Doc", "b"), DescriberHover.localNames("/a:Doc[1]/b[2]"));
    }

    @Test
    void describersAnswerWithTitleAndBody() {
        java.util.function.Function<ElementDescriber.ElementInfo, List<String>> describer = info ->
                info.name().equals("Rate") ? List.of("Field 7 · Rate\nM/C/O/F: M\n\nThe rate.") : List.of();
        assertArrayEquals(new String[] {"Field 7 · Rate", "M/C/O/F: M\n\nThe rate."},
                DescriberHover.describeAt(XML, XML.indexOf("<Rate>") + 1, describer));
        assertNull(DescriberHover.describeAt(XML, XML.indexOf("<Fund") + 1, describer), "nothing to say");
    }
}
