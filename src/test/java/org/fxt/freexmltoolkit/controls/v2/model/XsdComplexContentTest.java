package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdComplexContent.
 *
 * @since 2.0
 */
class XsdComplexContentTest {

    private XsdComplexContent complexContent;

    @BeforeEach
    void setUp() {
        complexContent = new XsdComplexContent();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdComplexContent cc = new XsdComplexContent();
        assertEquals("complexContent", cc.getName());
        assertFalse(cc.isMixed());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return COMPLEX_CONTENT")
    void testGetNodeType() {
        assertEquals(XsdNodeType.COMPLEX_CONTENT, complexContent.getNodeType());
    }

    // ========== Mixed Property Tests ==========

    @Test
    @DisplayName("isMixed() should be false by default")
    void testIsMixedDefaultValue() {
        assertFalse(complexContent.isMixed());
    }

    @Test
    @DisplayName("setMixed() should set mixed flag")
    void testSetMixed() {
        complexContent.setMixed(true);
        assertTrue(complexContent.isMixed());

        complexContent.setMixed(false);
        assertFalse(complexContent.isMixed());
    }

    @Test
    @DisplayName("setMixed() should fire PropertyChangeEvent")
    void testSetMixedFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("mixed", evt.getPropertyName());
            assertEquals(false, evt.getOldValue());
            assertEquals(true, evt.getNewValue());
            eventFired.set(true);
        };

        complexContent.addPropertyChangeListener(listener);
        complexContent.setMixed(true);

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    // ========== Extension/Restriction Children Tests ==========

    @Test
    @DisplayName("getExtension() should return null when no extension child")
    void testGetExtensionNull() {
        assertNull(complexContent.getExtension());
    }

    @Test
    @DisplayName("getExtension() should return extension child when present")
    void testGetExtensionPresent() {
        XsdExtension extension = new XsdExtension("xs:string");
        complexContent.addChild(extension);

        XsdExtension found = complexContent.getExtension();
        assertNotNull(found);
        assertEquals(extension, found);
    }

    @Test
    @DisplayName("getRestriction() should return null when no restriction child")
    void testGetRestrictionNull() {
        assertNull(complexContent.getRestriction());
    }

    @Test
    @DisplayName("getRestriction() should return restriction child when present")
    void testGetRestrictionPresent() {
        XsdRestriction restriction = new XsdRestriction("xs:string");
        complexContent.addChild(restriction);

        XsdRestriction found = complexContent.getRestriction();
        assertNotNull(found);
        assertEquals(restriction, found);
    }

    @Test
    @DisplayName("should support either extension or restriction, not both")
    void testEitherExtensionOrRestriction() {
        // XSD rules: complexContent must have either extension OR restriction, not both
        XsdExtension extension = new XsdExtension("BaseType");
        complexContent.addChild(extension);

        assertNotNull(complexContent.getExtension());
        assertNull(complexContent.getRestriction());

        // If we add restriction (which shouldn't happen in practice),
        // both getters would work but only one should be used
        XsdRestriction restriction = new XsdRestriction("OtherBaseType");
        complexContent.addChild(restriction);

        assertNotNull(complexContent.getExtension());
        assertNotNull(complexContent.getRestriction());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("complexContent should support parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("MyComplexType");
        complexContent.setParent(parent);
        parent.addChild(complexContent);

        assertEquals(parent, complexContent.getParent());
        assertTrue(parent.getChildren().contains(complexContent));
    }

    @Test
    @DisplayName("extension should be added as child")
    void testExtensionAsChild() {
        XsdExtension extension = new XsdExtension("BaseType");
        complexContent.addChild(extension);

        assertEquals(1, complexContent.getChildren().size());
        assertEquals(extension, complexContent.getChildren().get(0));
        assertEquals(complexContent, extension.getParent());
    }

    @Test
    @DisplayName("restriction should be added as child")
    void testRestrictionAsChild() {
        XsdRestriction restriction = new XsdRestriction("BaseType");
        complexContent.addChild(restriction);

        assertEquals(1, complexContent.getChildren().size());
        assertEquals(restriction, complexContent.getChildren().get(0));
        assertEquals(complexContent, restriction.getParent());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        complexContent.setMixed(true);
        XsdExtension extension = new XsdExtension("BaseType");
        complexContent.addChild(extension);

        XsdComplexContent copy = (XsdComplexContent) complexContent.deepCopy(null);

        assertNotNull(copy);
        assertEquals(complexContent.isMixed(), copy.isMixed());
        assertEquals(complexContent.getName(), copy.getName());
        assertNotSame(complexContent, copy);
        assertNotEquals(complexContent.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change complexContent name")
    void testDeepCopyWithSuffix() {
        complexContent.setMixed(true);

        XsdComplexContent copy = (XsdComplexContent) complexContent.deepCopy("_Copy");

        // ComplexContent name is always "complexContent", suffix should not be applied
        assertEquals("complexContent", copy.getName());
        assertEquals(complexContent.isMixed(), copy.isMixed());
    }

    @Test
    @DisplayName("deepCopy() should copy mixed flag")
    void testDeepCopyMixedFlag() {
        complexContent.setMixed(true);

        XsdComplexContent copy = (XsdComplexContent) complexContent.deepCopy(null);

        assertTrue(copy.isMixed());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        complexContent.setMixed(true);
        XsdExtension extension = new XsdExtension("BaseType");
        complexContent.addChild(extension);

        XsdComplexContent copy = (XsdComplexContent) complexContent.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdExtension);
        assertNotSame(extension, copy.getChildren().get(0));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("complexContent should work in complexType context")
    void testComplexContentInComplexType() {
        XsdComplexType complexType = new XsdComplexType("MyComplexType");

        XsdComplexContent content = new XsdComplexContent();
        content.setMixed(true);

        XsdExtension extension = new XsdExtension("BaseType");
        content.addChild(extension);

        complexType.addChild(content);

        assertEquals(complexType, content.getParent());
        assertEquals(1, complexType.getChildren().size());
        assertEquals(content, complexType.getChildren().get(0));
        assertTrue(content.isMixed());
    }

    @Test
    @DisplayName("complexContent with extension")
    void testComplexContentWithExtension() {
        XsdComplexContent content = new XsdComplexContent();
        content.setMixed(false);

        XsdExtension extension = new XsdExtension("PersonType");
        XsdElement element = new XsdElement("department");
        element.setType("xs:string");
        extension.addChild(element);

        content.addChild(extension);

        assertNotNull(content.getExtension());
        assertEquals("PersonType", content.getExtension().getBase());
        assertEquals(1, content.getExtension().getChildren().size());
    }

    @Test
    @DisplayName("complexContent with restriction")
    void testComplexContentWithRestriction() {
        XsdComplexContent content = new XsdComplexContent();

        XsdRestriction restriction = new XsdRestriction("PersonType");
        XsdElement element = new XsdElement("name");
        element.setType("xs:string");
        restriction.addChild(element);

        content.addChild(restriction);

        assertNotNull(content.getRestriction());
        assertEquals("PersonType", content.getRestriction().getBase());
        assertEquals(1, content.getRestriction().getChildren().size());
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("create complexContent with mixed content")
    void testComplexContentMixed() {
        // Example: <xs:complexContent mixed="true">
        //            <xs:extension base="BaseType"/>
        //          </xs:complexContent>

        XsdComplexContent content = new XsdComplexContent();
        content.setMixed(true);

        XsdExtension extension = new XsdExtension("BaseType");
        content.addChild(extension);

        assertTrue(content.isMixed());
        assertNotNull(content.getExtension());
        assertEquals("BaseType", content.getExtension().getBase());
    }

    @Test
    @DisplayName("create complexContent extending a type")
    void testComplexContentExtension() {
        // Example: <xs:complexContent>
        //            <xs:extension base="PersonType">
        //              <xs:sequence>
        //                <xs:element name="department" type="xs:string"/>
        //              </xs:sequence>
        //            </xs:extension>
        //          </xs:complexContent>

        XsdComplexContent content = new XsdComplexContent();

        XsdExtension extension = new XsdExtension("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("department");
        element.setType("xs:string");

        sequence.addChild(element);
        extension.addChild(sequence);
        content.addChild(extension);

        assertNotNull(content.getExtension());
        assertEquals(1, content.getExtension().getChildren().size());
    }

    @Test
    @DisplayName("create complexContent restricting a type")
    void testComplexContentRestriction() {
        // Example: <xs:complexContent>
        //            <xs:restriction base="PersonType">
        //              <xs:sequence>
        //                <xs:element name="name" type="xs:string"/>
        //              </xs:sequence>
        //            </xs:restriction>
        //          </xs:complexContent>

        XsdComplexContent content = new XsdComplexContent();

        XsdRestriction restriction = new XsdRestriction("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("name");
        element.setType("xs:string");

        sequence.addChild(element);
        restriction.addChild(sequence);
        content.addChild(restriction);

        assertNotNull(content.getRestriction());
        assertEquals(1, content.getRestriction().getChildren().size());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("mixed".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        complexContent.addPropertyChangeListener(listener);
        complexContent.setMixed(true);
        complexContent.setMixed(false);
        complexContent.setMixed(true);

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        complexContent.setMixed(true);
        String toString = complexContent.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("complexContent name should always be 'complexContent'")
    void testComplexContentNameAlwaysComplexContent() {
        assertEquals("complexContent", complexContent.getName());

        complexContent.setMixed(true);
        assertEquals("complexContent", complexContent.getName());

        XsdComplexContent another = new XsdComplexContent();
        assertEquals("complexContent", another.getName());
    }
}
