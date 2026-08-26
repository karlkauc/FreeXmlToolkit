package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XmlRootElementSnifferTest {
    @Test void namespacedRoot() {
        var r = XmlRootElementSniffer.sniff("<?xml version='1.0'?><!-- c --><x:Scene xmlns:x='http://www.web3d.org/specifications/x3d-4.0.xsd'/>").orElseThrow();
        assertEquals("http://www.web3d.org/specifications/x3d-4.0.xsd", r.namespace());
        assertEquals("Scene", r.localName());
    }
    @Test void defaultNamespaceAndNoNamespace() {
        assertEquals("urn:d", XmlRootElementSniffer.sniff("<a xmlns='urn:d'><b/></a>").orElseThrow().namespace());
        assertEquals("", XmlRootElementSniffer.sniff("<invoice/>").orElseThrow().namespace());
    }
    @Test void malformedOrEmptyIsEmpty() {
        assertTrue(XmlRootElementSniffer.sniff("<").isEmpty());
        assertTrue(XmlRootElementSniffer.sniff("").isEmpty());
        assertTrue(XmlRootElementSniffer.sniff("not xml").isEmpty());
    }
}
