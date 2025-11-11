package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdAlternative.
 *
 * @since 2.0
 */
class XsdAlternativeTest {

    private XsdAlternative alternative;

    @BeforeEach
    void setUp() {
        alternative = new XsdAlternative("@type='premium'", "PremiumType");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdAlternative alt = new XsdAlternative();
        assertEquals("alternative", alt.getName());
        assertNull(alt.getTest());
        assertNull(alt.getType());
    }

    @Test
    @DisplayName("constructor with test should set test expression")
    void testConstructorWithTest() {
        XsdAlternative alt = new XsdAlternative("@status='active'");
        assertEquals("alternative", alt.getName());
        assertEquals("@status='active'", alt.getTest());
        assertNull(alt.getType());
    }

    @Test
    @DisplayName("constructor with test and type should set both")
    void testConstructorWithTestAndType() {
        XsdAlternative alt = new XsdAlternative("@category='special'", "SpecialType");
        assertEquals("alternative", alt.getName());
        assertEquals("@category='special'", alt.getTest());
        assertEquals("SpecialType", alt.getType());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return ALTERNATIVE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.ALTERNATIVE, alternative.getNodeType());
    }

    // ========== Test Property Tests ==========

    @Test
    @DisplayName("getTest() should return test expression")
    void testGetTest() {
        assertEquals("@type='premium'", alternative.getTest());
    }

    @Test
    @DisplayName("setTest() should set test expression")
    void testSetTest() {
        alternative.setTest("@level > 5");
        assertEquals("@level > 5", alternative.getTest());
    }

    @Test
    @DisplayName("setTest() should fire PropertyChangeEvent")
    void testSetTestFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("test", evt.getPropertyName());
            assertEquals("@type='premium'", evt.getOldValue());
            assertEquals("@category='vip'", evt.getNewValue());
            eventFired.set(true);
        };

        alternative.addPropertyChangeListener(listener);
        alternative.setTest("@category='vip'");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setTest() should accept null")
    void testSetTestNull() {
        alternative.setTest(null);
        assertNull(alternative.getTest());
    }

    @Test
    @DisplayName("setTest() should accept complex XPath 2.0 expressions")
    void testSetTestComplexXPath() {
        String complexXPath = "if (@role='admin') then true() else @verified='true'";
        alternative.setTest(complexXPath);
        assertEquals(complexXPath, alternative.getTest());
    }

    // ========== Type Property Tests ==========

    @Test
    @DisplayName("getType() should return type reference")
    void testGetType() {
        assertEquals("PremiumType", alternative.getType());
    }

    @Test
    @DisplayName("setType() should set type reference")
    void testSetType() {
        alternative.setType("StandardType");
        assertEquals("StandardType", alternative.getType());
    }

    @Test
    @DisplayName("setType() should fire PropertyChangeEvent")
    void testSetTypeFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("type", evt.getPropertyName());
            assertEquals("PremiumType", evt.getOldValue());
            assertEquals("BasicType", evt.getNewValue());
            eventFired.set(true);
        };

        alternative.addPropertyChangeListener(listener);
        alternative.setType("BasicType");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setType() should fire event with correct old and new values")
    void testSetTypeMultipleTimes() {
        alternative.setType("FirstType");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("type", evt.getPropertyName());
            assertEquals("FirstType", evt.getOldValue());
            assertEquals("SecondType", evt.getNewValue());
            eventFired.set(true);
        };

        alternative.addPropertyChangeListener(listener);
        alternative.setType("SecondType");

        assertTrue(eventFired.get());
        assertEquals("SecondType", alternative.getType());
    }

    @Test
    @DisplayName("setType() should accept null")
    void testSetTypeNull() {
        alternative.setType(null);
        assertNull(alternative.getType());
    }

    @Test
    @DisplayName("setType() should accept qualified type names")
    void testSetTypeQualifiedName() {
        alternative.setType("ns:CustomType");
        assertEquals("ns:CustomType", alternative.getType());
    }

    // ========== Inline Type Tests ==========

    @Test
    @DisplayName("getSimpleType() should return null when no simpleType child")
    void testGetSimpleTypeNull() {
        assertNull(alternative.getSimpleType());
    }

    @Test
    @DisplayName("getSimpleType() should return simpleType child")
    void testGetSimpleType() {
        XsdSimpleType simpleType = new XsdSimpleType("StringType");
        alternative.addChild(simpleType);

        assertEquals(simpleType, alternative.getSimpleType());
    }

    @Test
    @DisplayName("getComplexType() should return null when no complexType child")
    void testGetComplexTypeNull() {
        assertNull(alternative.getComplexType());
    }

    @Test
    @DisplayName("getComplexType() should return complexType child")
    void testGetComplexType() {
        XsdComplexType complexType = new XsdComplexType("ProductType");
        alternative.addChild(complexType);

        assertEquals(complexType, alternative.getComplexType());
    }

    @Test
    @DisplayName("alternative can have either simpleType or complexType")
    void testAlternativeWithInlineTypes() {
        XsdAlternative alt1 = new XsdAlternative("@format='simple'");
        XsdSimpleType simpleType = new XsdSimpleType();
        alt1.addChild(simpleType);

        XsdAlternative alt2 = new XsdAlternative("@format='complex'");
        XsdComplexType complexType = new XsdComplexType("InlineComplexType");
        alt2.addChild(complexType);

        assertNotNull(alt1.getSimpleType());
        assertNull(alt1.getComplexType());
        assertNull(alt2.getSimpleType());
        assertNotNull(alt2.getComplexType());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        alternative.setDocumentation("Premium type for special customers");

        XsdAlternative copy = (XsdAlternative) alternative.deepCopy("");

        assertNotNull(copy);
        assertNotSame(alternative, copy);
        assertEquals("@type='premium'", copy.getTest());
        assertEquals("PremiumType", copy.getType());
        assertEquals("Premium type for special customers", copy.getDocumentation());
    }

    @Test
    @DisplayName("deepCopy() should create copy with suffix")
    void testDeepCopyWithSuffix() {
        XsdAlternative copy = (XsdAlternative) alternative.deepCopy("_copy");

        assertEquals("alternative_copy", copy.getName());
        assertEquals("@type='premium'", copy.getTest());
        assertEquals("PremiumType", copy.getType());
    }

    @Test
    @DisplayName("deepCopy() should create independent copy with different ID")
    void testDeepCopyDifferentId() {
        XsdAlternative copy = (XsdAlternative) alternative.deepCopy("");

        assertNotEquals(alternative.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() modifications should not affect original")
    void testDeepCopyIndependence() {
        XsdAlternative copy = (XsdAlternative) alternative.deepCopy("");
        copy.setTest("@modified='yes'");
        copy.setType("ModifiedType");

        assertEquals("@type='premium'", alternative.getTest());
        assertEquals("PremiumType", alternative.getType());
        assertEquals("@modified='yes'", copy.getTest());
        assertEquals("ModifiedType", copy.getType());
    }

    @Test
    @DisplayName("deepCopy() should copy inline types")
    void testDeepCopyWithInlineType() {
        XsdSimpleType simpleType = new XsdSimpleType("InlineType");
        alternative.addChild(simpleType);

        XsdAlternative copy = (XsdAlternative) alternative.deepCopy("");

        assertNotNull(copy.getSimpleType());
        assertNotSame(simpleType, copy.getSimpleType());
    }

    @Test
    @DisplayName("deepCopy() should copy null properties")
    void testDeepCopyWithNullProperties() {
        XsdAlternative alt = new XsdAlternative();
        XsdAlternative copy = (XsdAlternative) alt.deepCopy("");

        assertNull(copy.getTest());
        assertNull(copy.getType());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("alternative should be addable as child to element")
    void testAlternativeAsChildOfElement() {
        XsdElement element = new XsdElement("Product");
        element.addChild(alternative);

        assertEquals(element, alternative.getParent());
        assertTrue(element.getChildren().contains(alternative));
    }

    @Test
    @DisplayName("multiple alternatives should be independent")
    void testMultipleAlternativesIndependence() {
        XsdElement element = new XsdElement("Item");
        XsdAlternative alt1 = new XsdAlternative("@level='basic'", "BasicType");
        XsdAlternative alt2 = new XsdAlternative("@level='premium'", "PremiumType");

        element.addChild(alt1);
        element.addChild(alt2);

        assertEquals(2, element.getChildren().size());
        assertEquals("BasicType", alt1.getType());
        assertEquals("PremiumType", alt2.getType());
    }

    // ========== Conditional Type Assignment Tests ==========

    @Test
    @DisplayName("alternative with type reference")
    void testAlternativeWithTypeReference() {
        XsdAlternative alt = new XsdAlternative("@category='special'", "SpecialType");
        assertEquals("@category='special'", alt.getTest());
        assertEquals("SpecialType", alt.getType());
        assertNull(alt.getSimpleType());
        assertNull(alt.getComplexType());
    }

    @Test
    @DisplayName("alternative with inline simpleType")
    void testAlternativeWithInlineSimpleType() {
        XsdAlternative alt = new XsdAlternative("@format='text'");
        XsdSimpleType simpleType = new XsdSimpleType();
        XsdRestriction restriction = new XsdRestriction("xs:string");
        simpleType.addChild(restriction);
        alt.addChild(simpleType);

        assertNotNull(alt.getSimpleType());
        assertNull(alt.getType());
    }

    @Test
    @DisplayName("alternative with inline complexType")
    void testAlternativeWithInlineComplexType() {
        XsdAlternative alt = new XsdAlternative("@structure='detailed'");
        XsdComplexType complexType = new XsdComplexType("DetailedType");
        XsdSequence sequence = new XsdSequence();
        complexType.addChild(sequence);
        alt.addChild(complexType);

        assertNotNull(alt.getComplexType());
        assertNull(alt.getType());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("alternative with empty test expression")
    void testEmptyTest() {
        alternative.setTest("");
        assertEquals("", alternative.getTest());
    }

    @Test
    @DisplayName("alternative with whitespace test expression")
    void testWhitespaceTest() {
        alternative.setTest("   ");
        assertEquals("   ", alternative.getTest());
    }

    @Test
    @DisplayName("multiple PropertyChangeListeners should all be notified")
    void testMultipleListeners() {
        AtomicBoolean listener1Fired = new AtomicBoolean(false);
        AtomicBoolean listener2Fired = new AtomicBoolean(false);

        alternative.addPropertyChangeListener(evt -> listener1Fired.set(true));
        alternative.addPropertyChangeListener(evt -> listener2Fired.set(true));

        alternative.setTest("@new='value'");

        assertTrue(listener1Fired.get());
        assertTrue(listener2Fired.get());
    }

    // ========== Integration Scenario Tests ==========

    @Test
    @DisplayName("complete alternative in element with conditional types")
    void testCompleteAlternativeScenario() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("Product");

        // Alternative for premium products
        XsdAlternative premiumAlt = new XsdAlternative("@category='premium'", "PremiumProductType");
        premiumAlt.setDocumentation("Premium products have extended attributes");

        // Alternative for basic products
        XsdAlternative basicAlt = new XsdAlternative("@category='basic'", "BasicProductType");
        basicAlt.setDocumentation("Basic products have minimal attributes");

        element.addChild(premiumAlt);
        element.addChild(basicAlt);
        schema.addChild(element);

        // Verify structure
        assertEquals(2, element.getChildren().size());

        XsdAlternative retrievedPremium = (XsdAlternative) element.getChildren().get(0);
        assertEquals("@category='premium'", retrievedPremium.getTest());
        assertEquals("PremiumProductType", retrievedPremium.getType());

        XsdAlternative retrievedBasic = (XsdAlternative) element.getChildren().get(1);
        assertEquals("@category='basic'", retrievedBasic.getTest());
        assertEquals("BasicProductType", retrievedBasic.getType());
    }

    @Test
    @DisplayName("alternative with default case (no test)")
    void testAlternativeDefaultCase() {
        // Default alternative has no test attribute (catches all unmatched cases)
        XsdAlternative defaultAlt = new XsdAlternative();
        defaultAlt.setType("DefaultType");

        assertNull(defaultAlt.getTest());
        assertEquals("DefaultType", defaultAlt.getType());
    }
}
