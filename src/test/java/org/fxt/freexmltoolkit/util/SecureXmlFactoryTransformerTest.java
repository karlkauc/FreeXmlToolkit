package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * {@link SecureXmlFactory#createSecureTransformerFactory()} resolves the JAXP implementation once and then
 * instantiates that class directly (the lookup scans every jar and took seconds per call on Windows), while
 * still handing out a fresh, securely configured factory each time.
 */
class SecureXmlFactoryTransformerTest {

    @Test
    void laterFactoriesAreFreshInstancesOfTheSameImplementation() throws Exception {
        TransformerFactory first = SecureXmlFactory.createSecureTransformerFactory();
        TransformerFactory second = SecureXmlFactory.createSecureTransformerFactory();

        assertNotSame(first, second, "factories are not thread-safe: never share one");
        assertEquals(first.getClass(), second.getClass());
        assertEquals(TransformerFactory.newInstance().getClass(), second.getClass(),
                "the cached class must be what the regular JAXP lookup resolves");
        assertTrue(second.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void cachedFactoryStillSerializes() throws Exception {
        SecureXmlFactory.createSecureTransformerFactory(); // warm the cache
        Transformer transformer = SecureXmlFactory.createSecureTransformerFactory().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        doc.appendChild(doc.createElement("root"));
        StringWriter out = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        assertEquals("<root/>", out.toString().strip());
    }
}
