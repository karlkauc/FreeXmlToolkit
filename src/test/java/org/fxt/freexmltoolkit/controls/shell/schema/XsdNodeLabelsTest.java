package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.icons.IconifyIconService;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XsdNodeLabels}: every node type maps to a resolvable icon, and
 * the display text shows name, cardinality and (for elements) type.
 */
class XsdNodeLabelsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void everyNodeTypeMapsToAResolvableBootstrapIcon() {
        IconifyIconService icons = IconifyIconService.getInstance();
        for (XsdNodeType type : XsdNodeType.values()) {
            String icon = XsdNodeLabels.icon(type);
            assertNotNull(icon, () -> type + " has null icon");
            assertTrue(icon.startsWith("bi-"), () -> type + " icon must be a Bootstrap icon");
            assertTrue(icons.exists(icon), () -> type + " -> unknown icon '" + icon + "'");
        }
    }

    @Test
    void displayTextShowsNameCardinalityAndType() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        XsdNode item = find(schema, "item");
        assertNotNull(item, "test setup: 'item' element must exist");

        String text = XsdNodeLabels.displayText(item);
        assertTrue(text.contains("item"), text);
        assertTrue(text.contains("*"), "unbounded maxOccurs should render as *: " + text);
        assertTrue(text.contains("xs:string"), "element type should be shown: " + text);
    }

    @Test
    void requiredSingleElementHasNoCardinalitySuffix() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        XsdNode root = find(schema, "root");
        assertNotNull(root);
        assertFalse(XsdNodeLabels.displayText(root).contains("["),
                "1..1 cardinality should be omitted");
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
