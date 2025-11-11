package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdAssert.
 *
 * @since 2.0
 */
class XsdAssertTest {

    private XsdAssert xsdAssert;

    @BeforeEach
    void setUp() {
        xsdAssert = new XsdAssert("@price > 0");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdAssert a = new XsdAssert();
        assertEquals("assert", a.getName());
        assertNull(a.getTest());
    }

    @Test
    @DisplayName("constructor with test should set test expression")
    void testConstructorWithTest() {
        XsdAssert a = new XsdAssert("@quantity >= 1");
        assertEquals("assert", a.getName());
        assertEquals("@quantity >= 1", a.getTest());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return ASSERT")
    void testGetNodeType() {
        assertEquals(XsdNodeType.ASSERT, xsdAssert.getNodeType());
    }

    // ========== Test Property Tests ==========

    @Test
    @DisplayName("getTest() should return test expression")
    void testGetTest() {
        assertEquals("@price > 0", xsdAssert.getTest());
    }

    @Test
    @DisplayName("setTest() should set test expression")
    void testSetTest() {
        xsdAssert.setTest("@status = 'active'");
        assertEquals("@status = 'active'", xsdAssert.getTest());
    }

    @Test
    @DisplayName("setTest() should fire PropertyChangeEvent")
    void testSetTestFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("test", evt.getPropertyName());
            assertEquals("@price > 0", evt.getOldValue());
            assertEquals("@discount < 100", evt.getNewValue());
            eventFired.set(true);
        };

        xsdAssert.addPropertyChangeListener(listener);
        xsdAssert.setTest("@discount < 100");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setTest() should fire event with correct old and new values")
    void testSetTestMultipleTimes() {
        xsdAssert.setTest("@value1 > 0");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("test", evt.getPropertyName());
            assertEquals("@value1 > 0", evt.getOldValue());
            assertEquals("@value2 > 0", evt.getNewValue());
            eventFired.set(true);
        };

        xsdAssert.addPropertyChangeListener(listener);
        xsdAssert.setTest("@value2 > 0");

        assertTrue(eventFired.get());
        assertEquals("@value2 > 0", xsdAssert.getTest());
    }

    @Test
    @DisplayName("setTest() should accept null")
    void testSetTestNull() {
        xsdAssert.setTest(null);
        assertNull(xsdAssert.getTest());
    }

    @Test
    @DisplayName("setTest() should accept empty string")
    void testSetTestEmpty() {
        xsdAssert.setTest("");
        assertEquals("", xsdAssert.getTest());
    }

    @Test
    @DisplayName("setTest() should accept complex XPath 2.0 expressions")
    void testSetTestComplexXPath() {
        String complexXPath = "if (@type='premium') then @price > 100 else @price > 0";
        xsdAssert.setTest(complexXPath);
        assertEquals(complexXPath, xsdAssert.getTest());
    }

    // ========== XpathDefaultNamespace Property Tests ==========

    @Test
    @DisplayName("xpathDefaultNamespace should be null by default")
    void testXpathDefaultNamespaceDefaultValue() {
        XsdAssert a = new XsdAssert();
        assertNull(a.getXpathDefaultNamespace());
    }

    @Test
    @DisplayName("setXpathDefaultNamespace() should set namespace")
    void testSetXpathDefaultNamespace() {
        xsdAssert.setXpathDefaultNamespace("http://example.com/ns");
        assertEquals("http://example.com/ns", xsdAssert.getXpathDefaultNamespace());
    }

    @Test
    @DisplayName("setXpathDefaultNamespace() should fire PropertyChangeEvent")
    void testSetXpathDefaultNamespaceFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("xpathDefaultNamespace", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("http://example.com/ns", evt.getNewValue());
            eventFired.set(true);
        };

        xsdAssert.addPropertyChangeListener(listener);
        xsdAssert.setXpathDefaultNamespace("http://example.com/ns");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setXpathDefaultNamespace() should fire event with correct old and new values")
    void testSetXpathDefaultNamespaceMultipleTimes() {
        xsdAssert.setXpathDefaultNamespace("http://first.com");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("xpathDefaultNamespace", evt.getPropertyName());
            assertEquals("http://first.com", evt.getOldValue());
            assertEquals("http://second.com", evt.getNewValue());
            eventFired.set(true);
        };

        xsdAssert.addPropertyChangeListener(listener);
        xsdAssert.setXpathDefaultNamespace("http://second.com");

        assertTrue(eventFired.get());
        assertEquals("http://second.com", xsdAssert.getXpathDefaultNamespace());
    }

    @Test
    @DisplayName("setXpathDefaultNamespace() should accept null")
    void testSetXpathDefaultNamespaceNull() {
        xsdAssert.setXpathDefaultNamespace("http://example.com");
        xsdAssert.setXpathDefaultNamespace(null);
        assertNull(xsdAssert.getXpathDefaultNamespace());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        xsdAssert.setXpathDefaultNamespace("http://example.com/ns");
        xsdAssert.setDocumentation("Price must be positive");

        XsdAssert copy = (XsdAssert) xsdAssert.deepCopy("");

        assertNotNull(copy);
        assertNotSame(xsdAssert, copy);
        assertEquals("@price > 0", copy.getTest());
        assertEquals("http://example.com/ns", copy.getXpathDefaultNamespace());
        assertEquals("Price must be positive", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdAssert copy = (XsdAssert) xsdAssert.deepCopy("_copy");

        assertEquals("assert_copy", copy.getName());
        assertEquals("@price > 0", copy.getTest());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdAssert copy = (XsdAssert) xsdAssert.deepCopy("");

        assertNotEquals(xsdAssert.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdAssert copy = (XsdAssert) xsdAssert.deepCopy("");
        copy.setTest("@modified > 100");
        copy.setXpathDefaultNamespace("http://modified.com");

        assertEquals("@price > 0", xsdAssert.getTest());
        assertNull(xsdAssert.getXpathDefaultNamespace());
        assertEquals("@modified > 100", copy.getTest());
        assertEquals("http://modified.com", copy.getXpathDefaultNamespace());
    }

    @Test
    @DisplayName("deepCopy() should copy null properties")
    void testDeepCopyWithNullProperties() {
        XsdAssert a = new XsdAssert();
        XsdAssert copy = (XsdAssert) a.deepCopy("");

        assertNull(copy.getTest());
        assertNull(copy.getXpathDefaultNamespace());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("assert should be addable as child to complexType")
    void testAssertAsChildOfComplexType() {
        XsdComplexType complexType = new XsdComplexType("ProductType");
        complexType.addChild(xsdAssert);

        assertEquals(complexType, xsdAssert.getParent());
        assertTrue(complexType.getChildren().contains(xsdAssert));
    }

    @Test
    @DisplayName("assert should be addable as child to restriction")
    void testAssertAsChildOfRestriction() {
        XsdRestriction restriction = new XsdRestriction();
        restriction.addChild(xsdAssert);

        assertEquals(restriction, xsdAssert.getParent());
        assertTrue(restriction.getChildren().contains(xsdAssert));
    }

    @Test
    @DisplayName("multiple asserts should be independent")
    void testMultipleAssertsIndependence() {
        XsdComplexType complexType = new XsdComplexType("ProductType");
        XsdAssert assert1 = new XsdAssert("@price > 0");
        XsdAssert assert2 = new XsdAssert("@quantity >= 1");

        complexType.addChild(assert1);
        complexType.addChild(assert2);

        assertEquals(2, complexType.getChildren().size());
        assertEquals("@price > 0", assert1.getTest());
        assertEquals("@quantity >= 1", assert2.getTest());
    }

    // ========== XPath 2.0 Expression Tests ==========

    @Test
    @DisplayName("assert should support XPath 2.0 conditional expressions")
    void testXPath20ConditionalExpression() {
        String xpath = "if (@type='premium') then @price >= 100 else @price >= 10";
        xsdAssert.setTest(xpath);
        assertEquals(xpath, xsdAssert.getTest());
    }

    @Test
    @DisplayName("assert should support XPath 2.0 quantified expressions")
    void testXPath20QuantifiedExpression() {
        String xpath = "every $item in items/item satisfies $item/@price > 0";
        xsdAssert.setTest(xpath);
        assertEquals(xpath, xsdAssert.getTest());
    }

    @Test
    @DisplayName("assert should support XPath 2.0 sequence operations")
    void testXPath20SequenceOperations() {
        String xpath = "count(items/item) > 0 and sum(items/item/@price) > 100";
        xsdAssert.setTest(xpath);
        assertEquals(xpath, xsdAssert.getTest());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("assert with whitespace test expression")
    void testWhitespaceTest() {
        xsdAssert.setTest("   ");
        assertEquals("   ", xsdAssert.getTest());
    }

    @Test
    @DisplayName("assert with very long XPath expression")
    void testVeryLongXPath() {
        String longXPath = "(@field1 > 0) and (@field2 < 100) and (@field3 != '') and (@field4 >= 10) and (@field5 <= 99)";
        xsdAssert.setTest(longXPath);
        assertEquals(longXPath, xsdAssert.getTest());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        xsdAssert.addPropertyChangeListener(evt -> listener1Fired.set(true));
        xsdAssert.addPropertyChangeListener(evt -> listener2Fired.set(true));

        xsdAssert.setTest("@newValue > 0");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("complete assert constraint in product schema")
    void testCompleteAssertInProductSchema() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("Product");
        XsdComplexType complexType = new XsdComplexType("ProductType");

        XsdAssert priceAssert = new XsdAssert("@price > 0");
        priceAssert.setXpathDefaultNamespace("http://example.com/product");
        priceAssert.setDocumentation("Price must be positive");

        complexType.addChild(priceAssert);
        element.addChild(complexType);
        schema.addChild(element);

        // Verify structure
        assertEquals(1, complexType.getChildren().size());
        XsdAssert retrievedAssert = (XsdAssert) complexType.getChildren().get(0);
        assertEquals("@price > 0", retrievedAssert.getTest());
        assertEquals("http://example.com/product", retrievedAssert.getXpathDefaultNamespace());
        assertEquals("Price must be positive", retrievedAssert.getDocumentation());
    }
}
