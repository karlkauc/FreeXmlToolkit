package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdDocInfo.
 */
class XsdDocInfoTest {

    @Test
    void testEmptyDocInfo() {
        XsdDocInfo docInfo = new XsdDocInfo();

        assertTrue(docInfo.isEmpty());
        assertNull(docInfo.getSinceVersion());
        assertFalse(docInfo.isDeprecated());
        assertTrue(docInfo.getSeeReferences().isEmpty());
    }

    @Test
    void testSetSinceVersion() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.setSinceVersion("4.0.0");

        assertFalse(docInfo.isEmpty());
        assertEquals("4.0.0", docInfo.getSinceVersion());
    }

    @Test
    void testAddSeeReferencePlainText() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.addSeeReference("See also related elements");

        assertEquals(1, docInfo.getSeeReferences().size());
        XsdDocInfo.SeeReference ref = docInfo.getSeeReferences().get(0);
        assertEquals("See also related elements", ref.text());
        assertFalse(ref.hasLinks());
    }

    @Test
    void testAddSeeReferenceWithLink() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.addSeeReference("See {@link /FundsXML4/ControlData/DocumentID}");

        assertEquals(1, docInfo.getSeeReferences().size());
        XsdDocInfo.SeeReference ref = docInfo.getSeeReferences().get(0);
        assertTrue(ref.hasLinks());
        assertEquals(1, ref.linkTags().size());
        assertEquals("/FundsXML4/ControlData/DocumentID", ref.linkTags().get(0).getXpathExpression());
    }

    @Test
    void testAddSeeReferenceWithMultipleLinks() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.addSeeReference("See {@link /Path1} and {@link /Path2}");

        XsdDocInfo.SeeReference ref = docInfo.getSeeReferences().get(0);
        assertEquals(2, ref.linkTags().size());
        assertEquals("/Path1", ref.linkTags().get(0).getXpathExpression());
        assertEquals("/Path2", ref.linkTags().get(1).getXpathExpression());
    }

    @Test
    void testSetDeprecated() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.setDeprecated("Use {@link /FundsXML4/NewElement} instead");

        assertTrue(docInfo.isDeprecated());
        assertNotNull(docInfo.getDeprecationInfo());

        XsdDocInfo.DeprecationInfo deprecation = docInfo.getDeprecationInfo();
        assertEquals("Use {@link /FundsXML4/NewElement} instead", deprecation.message());
        assertTrue(deprecation.hasReplacements());
        assertEquals(1, deprecation.replacementLinks().size());
        assertEquals("/FundsXML4/NewElement", deprecation.replacementLinks().get(0).getXpathExpression());
    }

    @Test
    void testDeprecatedWithoutLinks() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.setDeprecated("This element is no longer supported");

        assertTrue(docInfo.isDeprecated());
        XsdDocInfo.DeprecationInfo deprecation = docInfo.getDeprecationInfo();
        assertFalse(deprecation.hasReplacements());
        assertTrue(deprecation.replacementLinks().isEmpty());
    }

    @Test
    void testDeprecatedWithMultipleReplacements() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.setDeprecated("Use {@link /Path1} and {@link /Path2} instead");

        XsdDocInfo.DeprecationInfo deprecation = docInfo.getDeprecationInfo();
        assertEquals(2, deprecation.replacementLinks().size());
    }

    @Test
    void testCompleteDocumentation() {
        XsdDocInfo docInfo = new XsdDocInfo();
        docInfo.setSinceVersion("4.0.0");
        docInfo.addSeeReference("See {@link /FundsXML4/RelatedElement}");
        docInfo.addSeeReference("Also see documentation at https://example.com");
        docInfo.setDeprecated("Use {@link /FundsXML4/NewElement} instead");

        assertFalse(docInfo.isEmpty());
        assertEquals("4.0.0", docInfo.getSinceVersion());
        assertEquals(2, docInfo.getSeeReferences().size());
        assertTrue(docInfo.isDeprecated());
    }

    @Test
    void testLinkTagParsing() {
        XsdDocInfo docInfo = new XsdDocInfo();

        // Test various {@link} formats
        docInfo.addSeeReference("{@link /FundsXML4/Element}");
        docInfo.addSeeReference("{@link   /FundsXML4/Element  }"); // with extra spaces
        docInfo.addSeeReference("Text {@link /Path} more text");

        assertEquals(3, docInfo.getSeeReferences().size());

        // All should have valid links extracted
        assertTrue(docInfo.getSeeReferences().get(0).hasLinks());
        assertTrue(docInfo.getSeeReferences().get(1).hasLinks());
        assertTrue(docInfo.getSeeReferences().get(2).hasLinks());

        // Verify trimmed XPath
        assertEquals("/FundsXML4/Element", docInfo.getSeeReferences().get(1).linkTags().get(0).getXpathExpression());
    }

    @Test
    void testSeeReferenceToString() {
        XsdDocInfo.SeeReference ref = new XsdDocInfo.SeeReference("See related element", null);
        assertEquals("@see See related element", ref.toString());
    }

    @Test
    void testDeprecationInfoToString() {
        XsdDocInfo.DeprecationInfo deprecation = new XsdDocInfo.DeprecationInfo("No longer supported", null);
        assertEquals("@deprecated No longer supported", deprecation.toString());
    }
}
