package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdLinkTag.
 */
class XsdLinkTagTest {

    @Test
    void testCreateLinkTag() {
        XsdLinkTag link = new XsdLinkTag("/FundsXML4/ControlData/DocumentID");

        assertEquals("/FundsXML4/ControlData/DocumentID", link.getXpathExpression());
        assertNull(link.getResolvedTargetId());
        assertFalse(link.isResolved());
    }

    @Test
    void testResolveTarget() {
        XsdLinkTag link = new XsdLinkTag("/FundsXML4/ControlData/DocumentID");

        link.setResolvedTargetId("element-123");

        assertEquals("element-123", link.getResolvedTargetId());
        assertTrue(link.isResolved());
    }

    @Test
    void testNullXPathThrowsException() {
        assertThrows(NullPointerException.class, () -> new XsdLinkTag(null));
    }

    @Test
    void testEquals() {
        XsdLinkTag link1 = new XsdLinkTag("/FundsXML4/Element1");
        XsdLinkTag link2 = new XsdLinkTag("/FundsXML4/Element1");
        XsdLinkTag link3 = new XsdLinkTag("/FundsXML4/Element2");

        assertEquals(link1, link2);
        assertNotEquals(link1, link3);
    }

    @Test
    void testHashCode() {
        XsdLinkTag link1 = new XsdLinkTag("/FundsXML4/Element1");
        XsdLinkTag link2 = new XsdLinkTag("/FundsXML4/Element1");

        assertEquals(link1.hashCode(), link2.hashCode());
    }

    @Test
    void testToString() {
        XsdLinkTag link = new XsdLinkTag("/FundsXML4/ControlData/DocumentID");

        assertEquals("{@link /FundsXML4/ControlData/DocumentID}", link.toString());
    }
}
