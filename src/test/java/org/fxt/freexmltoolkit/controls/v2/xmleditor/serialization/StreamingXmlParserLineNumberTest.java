package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link StreamingXmlParser} records 1-based source line numbers on
 * elements, and that a line number can be resolved to the deepest matching element
 * (the logic used to jump from a validation error to a node in the graphic view).
 */
class StreamingXmlParserLineNumberTest {

    @Test
    void recordsAscendingSourceLineNumbers() {
        String xml = """
                <root>
                    <child>
                        <leaf/>
                    </child>
                </root>""";
        XmlDocument doc = new StreamingXmlParser().parse(xml);

        XmlElement root = doc.getRootElement();
        XmlElement child = root.getChildElements().get(0);
        XmlElement leaf = child.getChildElements().get(0);

        assertEquals(1, root.getSourceLineNumber(), "root start tag is on line 1");
        assertEquals(2, child.getSourceLineNumber(), "child start tag is on line 2");
        assertEquals(3, leaf.getSourceLineNumber(), "leaf start tag is on line 3");
    }

    @Test
    void resolvesLineToDeepestMatchingElement() {
        String xml = """
                <root>
                    <child>
                        <leaf/>
                    </child>
                </root>""";
        XmlElement root = new StreamingXmlParser().parse(xml).getRootElement();

        // Line 3 (the <leaf/> line) should resolve to the leaf element.
        assertEquals("leaf", findElementByLine(root, 3).getName());
        // Line 2 resolves to child.
        assertEquals("child", findElementByLine(root, 2).getName());
        // Line 1 resolves to root.
        assertEquals("root", findElementByLine(root, 1).getName());
        // A line beyond the last element start tag falls back to the deepest start tag seen (leaf on line 3).
        assertEquals("leaf", findElementByLine(root, 4).getName());
    }

    @Test
    void returnsNullWhenNoElementQualifies() {
        XmlElement root = new StreamingXmlParser().parse("<root><child/></root>").getRootElement();
        // All elements are on line 1; line 0 is below every start tag.
        assertNull(findElementByLine(root, 0));
    }

    // Mirrors XmlUnifiedTab.findElementByLine: greatest sourceLineNumber <= line wins.
    private XmlElement findElementByLine(XmlElement root, int line) {
        XmlElement best = null;
        for (XmlElement e : flatten(root)) {
            int l = e.getSourceLineNumber();
            if (l > 0 && l <= line && (best == null || l >= best.getSourceLineNumber())) {
                best = e;
            }
        }
        return best;
    }

    private List<XmlElement> flatten(XmlElement element) {
        List<XmlElement> out = new ArrayList<>();
        out.add(element);
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement childElement) {
                out.addAll(flatten(childElement));
            }
        }
        return out;
    }
}
