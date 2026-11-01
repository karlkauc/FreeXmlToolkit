package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** QNames mean what the file they were written in says, not just their local part. */
class XsdVisualTreeBuilderNamespaceTest {

    private static final String XS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    @TempDir
    Path dir;

    private Path write(String name, String text) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    private List<String> labelsOf(Path file) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(Files.readString(file), file, dir);
        VisualNode root = new XsdVisualTreeBuilder().buildFromSchema(schema, null, schema.getImportedSchemas());
        List<String> labels = new ArrayList<>();
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            node.setExpanded(true);
            labels.add(node.getLabel());
            node.getChildren().forEach(pending::push);
        }
        return labels;
    }

    private static String typeWith(String attribute) {
        return "<xs:complexType name=\"Same\"><xs:attribute name=\"" + attribute + "\" type=\"xs:string\"/></xs:complexType>";
    }

    @Test
    void equalNamesInTwoImportsStayApart() throws Exception {
        write("a.xsd", "<xs:schema " + XS + " targetNamespace=\"urn:a\">" + typeWith("fromA") + "</xs:schema>");
        write("b.xsd", "<xs:schema " + XS + " targetNamespace=\"urn:b\">" + typeWith("fromB") + "</xs:schema>");
        Path main = write("main.xsd", """
                <xs:schema %s xmlns:a="urn:a" xmlns:b="urn:b">
                  <xs:import namespace="urn:a" schemaLocation="a.xsd"/>
                  <xs:import namespace="urn:b" schemaLocation="b.xsd"/>
                  <xs:element name="root"><xs:complexType><xs:sequence>
                    <xs:element name="first" type="a:Same"/>
                    <xs:element name="second" type="b:Same"/>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>
                """.formatted(XS));

        assertTrue(labelsOf(main).containsAll(List.of("fromA", "fromB")));
    }

    @Test
    void localTypeIsNotReplacedByAnImportedOneOfTheSameName() throws Exception {
        write("a.xsd", "<xs:schema " + XS + " targetNamespace=\"urn:a\">" + typeWith("imported") + "</xs:schema>");
        Path main = write("main.xsd", """
                <xs:schema %s targetNamespace="urn:m" xmlns="urn:m">
                  <xs:import namespace="urn:a" schemaLocation="a.xsd"/>
                  %s
                  <xs:element name="root" type="Same"/>
                </xs:schema>
                """.formatted(XS, typeWith("own")));

        List<String> labels = labelsOf(main);

        assertTrue(labels.contains("own"), labels.toString());
        assertFalse(labels.contains("imported"), labels.toString());
    }

    @Test
    void includeUsesItsOwnPrefixes() throws Exception {
        write("part.xsd", """
                <s:schema xmlns:s="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:m" targetNamespace="urn:m">
                  <s:complexType name="Holder"><s:sequence>
                    <s:element name="text" type="s:string"/>
                    <s:element name="deep" type="t:Inner"/>
                  </s:sequence></s:complexType>
                  <s:complexType name="Inner"><s:attribute name="found" type="s:string"/></s:complexType>
                </s:schema>
                """);
        Path main = write("main.xsd", """
                <xs:schema %s targetNamespace="urn:m" xmlns:m="urn:m">
                  <xs:include schemaLocation="part.xsd"/>
                  <xs:element name="root" type="m:Holder"/>
                </xs:schema>
                """.formatted(XS));

        List<String> labels = labelsOf(main);

        assertTrue(labels.contains("found"), labels.toString());
        assertTrue(labels.stream().noneMatch(l -> l.startsWith("Unresolved")), labels.toString());
    }

    @Test
    void samePrefixMeansDifferentNamespacesInDifferentFiles() throws Exception {
        write("other.xsd", "<xs:schema " + XS + " targetNamespace=\"urn:o\">" + typeWith("other") + "</xs:schema>");
        write("part.xsd", """
                <xs:schema %s xmlns:p="urn:m" targetNamespace="urn:m">
                  <xs:complexType name="Same"><xs:attribute name="mine" type="xs:string"/></xs:complexType>
                  <xs:element name="partRoot" type="p:Same"/>
                </xs:schema>
                """.formatted(XS));
        Path main = write("main.xsd", """
                <xs:schema %s targetNamespace="urn:m" xmlns:p="urn:o" xmlns:m="urn:m">
                  <xs:include schemaLocation="part.xsd"/>
                  <xs:import namespace="urn:o" schemaLocation="other.xsd"/>
                  <xs:element name="root" type="p:Same"/>
                </xs:schema>
                """.formatted(XS));

        List<String> labels = labelsOf(main);

        assertTrue(labels.containsAll(List.of("mine", "other")), labels.toString());
    }

    @Test
    void chameleonIncludeResolves() throws Exception {
        write("part.xsd", """
                <xs:schema %s>
                  <xs:complexType name="Outer"><xs:sequence>
                    <xs:element name="x" type="Inner"/>
                  </xs:sequence></xs:complexType>
                  <xs:complexType name="Inner"><xs:attribute name="found" type="xs:string"/></xs:complexType>
                </xs:schema>
                """.formatted(XS));
        Path main = write("main.xsd", """
                <xs:schema %s targetNamespace="urn:m" xmlns:m="urn:m">
                  <xs:include schemaLocation="part.xsd"/>
                  <xs:element name="root" type="m:Outer"/>
                </xs:schema>
                """.formatted(XS));

        assertTrue(labelsOf(main).contains("found"));
    }

    @Test
    void schemaWithoutTargetNamespaceResolves() throws Exception {
        Path main = write("main.xsd", """
                <schema xmlns="http://www.w3.org/2001/XMLSchema">
                  <element name="root" type="Mine"/>
                  <complexType name="Mine"><attribute name="found" type="string"/></complexType>
                </schema>
                """);

        List<String> labels = labelsOf(main);

        assertTrue(labels.contains("found"), labels.toString());
    }

    @Test
    void aPrefixedNameDoesNotFallBackToAnotherNamespace() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema %s xmlns:gone="urn:gone">
                  %s
                  <xs:element name="root" type="gone:Same"/>
                </xs:schema>
                """.formatted(XS, typeWith("wrong")));

        List<String> labels = labelsOf(main);

        assertTrue(labels.contains("Unresolved type: gone:Same"), labels.toString());
        assertFalse(labels.contains("wrong"), labels.toString());
    }
}
