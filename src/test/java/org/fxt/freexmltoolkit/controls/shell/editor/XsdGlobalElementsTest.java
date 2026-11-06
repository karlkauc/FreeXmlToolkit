package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link XsdGlobalElements}: which root elements a schema can validate. */
class XsdGlobalElementsTest {

    private static File write(Path dir, String name, String body) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" " + body);
        return p.toFile();
    }

    @Test
    void findsAGlobalElementWithoutNamespace(@TempDir Path dir) throws Exception {
        File xsd = write(dir, "a.xsd", """
                ><xs:element name="root"><xs:complexType><xs:sequence>
                  <xs:element name="child" type="xs:string"/>
                </xs:sequence></xs:complexType></xs:element></xs:schema>""");
        assertTrue(XsdGlobalElements.declares(xsd, "", "root"));
        assertFalse(XsdGlobalElements.declares(xsd, "", "child"), "a local element is no root");
        assertFalse(XsdGlobalElements.declares(xsd, "urn:x", "root"), "wrong namespace");
    }

    @Test
    void honoursTheTargetNamespace(@TempDir Path dir) throws Exception {
        File xsd = write(dir, "ns.xsd", """
                targetNamespace="urn:x"><xs:element name="root" type="xs:string"/></xs:schema>""");
        assertTrue(XsdGlobalElements.declares(xsd, "urn:x", "root"));
        assertFalse(XsdGlobalElements.declares(xsd, null, "root"));
    }

    @Test
    void followsIncludesAndImports(@TempDir Path dir) throws Exception {
        write(dir, "sub/inc.xsd", """
                ><xs:element name="fromInclude" type="xs:string"/></xs:schema>""");
        write(dir, "imp.xsd", """
                targetNamespace="urn:other"><xs:element name="fromImport" type="xs:string"/></xs:schema>""");
        File main = write(dir, "main.xsd", """
                targetNamespace="urn:main">
                  <xs:include schemaLocation="sub/inc.xsd"/>
                  <xs:import namespace="urn:other" schemaLocation="imp.xsd"/>
                  <xs:import namespace="urn:remote" schemaLocation="http://example.invalid/remote.xsd"/>
                </xs:schema>""");
        assertTrue(XsdGlobalElements.declares(main, "urn:main", "fromInclude"),
                "an included schema without targetNamespace takes over the includer's");
        assertTrue(XsdGlobalElements.declares(main, "urn:other", "fromImport"));
        assertFalse(XsdGlobalElements.declares(main, "urn:main", "fromImport"));
    }

    @Test
    void endsOnAnIncludeCycle(@TempDir Path dir) throws Exception {
        write(dir, "b.xsd", """
                ><xs:include schemaLocation="a.xsd"/></xs:schema>""");
        File a = write(dir, "a.xsd", """
                ><xs:include schemaLocation="b.xsd"/></xs:schema>""");
        assertFalse(XsdGlobalElements.declares(a, "", "nowhere"));
    }

    @Test
    void unreadableSchemaDeclaresNothing(@TempDir Path dir) throws Exception {
        Path broken = dir.resolve("broken.xsd");
        Files.writeString(broken, "<not-closed");
        assertFalse(XsdGlobalElements.declares(broken.toFile(), "", "root"));
        assertFalse(XsdGlobalElements.declares(dir.resolve("missing.xsd").toFile(), "", "root"));
    }

    @Test
    void readsTheRootElementOfADocument() {
        assertEquals(new QName("urn:x", "r"), XsdGlobalElements.rootElement(
                "\n<?xml version=\"1.0\"?><!-- c --><p:r xmlns:p=\"urn:x\"><a/></p:r>"));
        assertEquals(new QName("", "plain"), XsdGlobalElements.rootElement("<plain/>"));
        assertNull(XsdGlobalElements.rootElement("not xml"));
    }
}
