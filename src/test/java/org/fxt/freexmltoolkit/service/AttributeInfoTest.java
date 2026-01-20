package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttributeInfoTest {

    @Test
    void testConstructorAndGetters() {
        AttributeInfo info = new AttributeInfo("testAttr", "testNs");
        assertEquals("testAttr", info.getName());
        assertEquals("testNs", info.getNamespace());
        assertEquals("testNs:testAttr", info.getQualifiedName());
    }

    @Test
    void testSetters() {
        AttributeInfo info = new AttributeInfo();
        info.setName("name");
        info.setRequired(true);
        info.setTypeConfidence(0.8);
        
        assertEquals("name", info.getName());
        assertTrue(info.isRequired());
        assertEquals(0.8, info.getTypeConfidence());
    }
}
