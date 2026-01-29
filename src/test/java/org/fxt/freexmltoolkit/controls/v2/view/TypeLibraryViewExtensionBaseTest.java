package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageFinder;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.UsageReferenceType;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TypeLibraryView usage detection to verify that extension base types
 * are correctly detected as "used" (fixes bug where positiveDecimals was shown as unused).
 *
 * @since 2.0
 */
class TypeLibraryViewExtensionBaseTest {

    private XsdSchema schema;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");
    }

    @Test
    @DisplayName("TypeUsageFinder correctly detects extension base usage - simple case")
    void testTypeUsageFinderDetectsExtensionBase() {
        // Create the positiveDecimals SimpleType
        XsdSimpleType positiveDecimals = new XsdSimpleType("positiveDecimals");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:decimal");
        positiveDecimals.addChild(restriction);
        schema.addChild(positiveDecimals);

        // Create a ComplexType with extension base
        XsdComplexType derivedType = new XsdComplexType("DerivedType");
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        XsdExtension extension = new XsdExtension();
        extension.setBase("positiveDecimals");
        simpleContent.addChild(extension);
        derivedType.addChild(simpleContent);
        schema.addChild(derivedType);

        // Use TypeUsageFinder to find usages
        TypeUsageFinder finder = new TypeUsageFinder(schema);

        // Assert that positiveDecimals is used
        assertTrue(finder.isTypeUsed("positiveDecimals"),
            "positiveDecimals should be detected as used (extension base)");

        assertEquals(1, finder.countUsages("positiveDecimals"),
            "positiveDecimals should have exactly 1 usage");

        var usages = finder.findUsages("positiveDecimals");
        assertEquals(1, usages.size(), "Should find 1 usage location");
        assertEquals(UsageReferenceType.EXTENSION_BASE,
            usages.get(0).referenceType(),
            "Usage should be EXTENSION_BASE type");
    }

    @Test
    @DisplayName("Extension base in nested anonymous complex type is detected (real-world case)")
    void testExtensionBaseInNestedAnonymousComplexType() {
        // Create the positiveDecimals SimpleType
        XsdSimpleType positiveDecimals = new XsdSimpleType("positiveDecimals");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:decimal");
        XsdFacet minInclusive = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0");
        restriction.addChild(minInclusive);
        positiveDecimals.addChild(restriction);
        schema.addChild(positiveDecimals);

        // Create the PositiveAmountType ComplexType that uses positiveDecimals as extension base
        XsdComplexType positiveAmountType = new XsdComplexType("PositiveAmountType");
        XsdSequence sequence = new XsdSequence();

        // Create the Amount element with anonymous complex type
        XsdElement amountElement = new XsdElement("Amount");
        amountElement.setMaxOccurs(XsdNode.UNBOUNDED);

        // Anonymous complex type with simpleContent/extension
        XsdComplexType anonymousComplexType = new XsdComplexType(null); // anonymous
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        XsdExtension extension = new XsdExtension();
        extension.setBase("positiveDecimals"); // THIS IS THE KEY USAGE!

        // Add attributes to the extension
        XsdAttribute ccyAttr = new XsdAttribute("ccy");
        ccyAttr.setType("ISOCurrencyCodeType");
        ccyAttr.setUse("required");
        extension.addChild(ccyAttr);

        simpleContent.addChild(extension);
        anonymousComplexType.addChild(simpleContent);
        amountElement.addChild(anonymousComplexType);

        sequence.addChild(amountElement);
        positiveAmountType.addChild(sequence);
        schema.addChild(positiveAmountType);

        // Verify the schema structure
        assertNotNull(schema.getChildren(), "Schema should have children");
        assertEquals(2, schema.getChildren().size(), "Schema should have 2 types");

        // Verify the extension is properly set up
        XsdExtension ext = (XsdExtension) simpleContent.getChildren().get(0);
        assertEquals("positiveDecimals", ext.getBase(),
            "Extension base should be positiveDecimals");

        // Use TypeUsageFinder to find usages
        TypeUsageFinder finder = new TypeUsageFinder(schema);

        // THIS IS THE KEY ASSERTION: positiveDecimals should be detected as used
        assertTrue(finder.isTypeUsed("positiveDecimals"),
            "positiveDecimals should be detected as used (extension base in nested anonymous complexType)");

        assertEquals(1, finder.countUsages("positiveDecimals"),
            "positiveDecimals should have exactly 1 usage");

        var usages = finder.findUsages("positiveDecimals");
        assertEquals(1, usages.size(), "Should find 1 usage location");
        assertEquals(UsageReferenceType.EXTENSION_BASE,
            usages.get(0).referenceType(),
            "Usage should be EXTENSION_BASE type");
    }
}
