package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class StartTagAttributesTest {

    @Test
    void collectsAttributesBeforeAndAfterTheCaret() {
        assertEquals(List.of("name", "as", "select"),
                present("<x><xsl:variable name=\"v\" as=\"xs:string\" ", " select=\"1\"/></x>"));
    }

    @Test
    void ignoresTheElementNameAndOtherTags() {
        assertEquals(List.of(), present("<a b=\"1\"><xsl:if ", "></xsl:if><c d=\"2\"/>"));
    }

    @Test
    void quotedValuesMayContainGreaterThanSpacesAndEquals() {
        assertEquals(List.of("test", "select", "mode"),
                present("<xsl:if test=\"a > b and c = 'x y'\" ", "select='p > q' mode=\"m\">"));
    }

    @Test
    void skipsThePartialNameAtTheCaret() {
        assertEquals(List.of("name", "as"), present("<xsl:param name=\"p\" sel", "ect as=\"item()\"/>"));
    }

    @Test
    void multiLineTags() {
        assertEquals(List.of("match", "mode"), present("<xsl:template\n    match=\"/\"\n    ", "\n\tmode = \"m\">"));
    }

    @Test
    void noTagNoAttributes() {
        assertEquals(List.of(), present("plain text ", ""));
    }

    private static List<String> present(String before, String after) {
        return List.copyOf(StartTagAttributes.present(before, after));
    }
}
