package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for XsdSchemaAdapter.
 *
 * @since 2.0
 */
class XsdSchemaAdapterTest {

    @Test
    void testAdapterCreatesCompositorWithChildren() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="person">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                                <xs:element name="age" type="xs:int"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        // Convert to model using adapter
        XsdSchemaModel model = XsdSchemaAdapter.toSchemaModel(schema);

        assertNotNull(model);
        assertEquals(1, model.getGlobalElements().size());

        XsdElementModel personElement = model.getGlobalElements().get(0);
        assertEquals("person", personElement.getName());

        // Check that compositor was created
        System.out.println("Number of compositors: " + personElement.getCompositors().size());
        assertEquals(1, personElement.getCompositors().size(), "Element should have one compositor");

        XsdCompositorModel compositor = personElement.getCompositors().get(0);
        assertEquals(XsdCompositorModel.CompositorType.SEQUENCE, compositor.getType());

        // Check that compositor has children
        System.out.println("Children in order: " + compositor.getChildrenInOrder().size());
        System.out.println("Elements: " + compositor.getElements().size());

        for (Object child : compositor.getChildrenInOrder()) {
            System.out.println("  Child: " + child.getClass().getSimpleName() + " - " + child);
        }

        assertEquals(2, compositor.getChildrenInOrder().size(), "Compositor should have 2 children");
        assertEquals(2, compositor.getElements().size(), "Compositor should have 2 elements");

        // Verify element children
        XsdElementModel nameElement = compositor.getElements().get(0);
        assertEquals("name", nameElement.getName());
        assertEquals("xs:string", nameElement.getType());

        XsdElementModel ageElement = compositor.getElements().get(1);
        assertEquals("age", ageElement.getName());
        assertEquals("xs:int", ageElement.getType());
    }
}
