package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class XmlServiceAdvancedTest {

    private XmlServiceImpl xmlService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        xmlService = new XmlServiceImpl();
    }

    @Test
    @DisplayName("Sollte UTF-8 BOM erkennen und entfernen")
    void testBomHandling() throws IOException {
        Path bomFile = tempDir.resolve("bom_test.xml");
        byte[] bomContent = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<', 'r', 'o', 'o', 't', '/', '>'};
        Files.write(bomFile, bomContent);

        assertTrue(XmlServiceImpl.isContainBOM(bomFile), "BOM sollte erkannt werden");

        xmlService.removeBom(bomFile);

        assertFalse(XmlServiceImpl.isContainBOM(bomFile), "BOM sollte entfernt worden sein");
        byte[] result = Files.readAllBytes(bomFile);
        assertEquals('<', result[0], "Erstes Zeichen sollte '<' sein");
        assertEquals(7, result.length, "Datei sollte 7 Byte groß sein");
    }

    @Test
    @DisplayName("Sollte xsi:noNamespaceSchemaLocation extrahieren")
    void testGetSchemaNameFromNoNamespace() {
        File xmlFile = Paths.get("src/test/resources/advanced_tests/no_ns_test.xml").toFile();
        xmlService.setCurrentXmlFile(xmlFile);
        
        Optional<String> schemaName = xmlService.getSchemaNameFromCurrentXMLFile();
        
        assertTrue(schemaName.isPresent());
        assertEquals("test.xsd", schemaName.get());
    }

    @Test
    @DisplayName("Sollte XSD aus xmlns-Attribut extrahieren")
    void testGetSchemaNameFromXmlns() {
        File xmlFile = Paths.get("src/test/resources/advanced_tests/xmlns_test.xml").toFile();
        xmlService.setCurrentXmlFile(xmlFile);
        
        Optional<String> schemaName = xmlService.getSchemaNameFromCurrentXMLFile();
        
        assertTrue(schemaName.isPresent());
        assertEquals("http://www.example.org/schema.xsd", schemaName.get());
    }

    @Test
    @DisplayName("Sollte xml-stylesheet Processing Instruction extrahieren")
    void testGetLinkedStylesheet() {
        File xmlFile = Paths.get("src/test/resources/advanced_tests/pi_test.xml").toFile();
        xmlService.setCurrentXmlFile(xmlFile);
        
        // Da style.xsl nicht existiert, wird resolveStylesheetPath Optional.empty() zurückgeben,
        // aber wir können prüfen, ob die Extraktion an sich funktioniert, indem wir eine Datei anlegen.
        try {
            Files.createFile(xmlFile.getParentFile().toPath().resolve("style.xsl"));
        } catch (IOException ignored) {}

        Optional<String> stylesheet = xmlService.getLinkedStylesheetFromCurrentXMLFile();
        
        assertTrue(stylesheet.isPresent(), "Stylesheet sollte gefunden werden");
        assertTrue(stylesheet.get().endsWith("style.xsl"));
    }

    @Test
    @DisplayName("Sollte verschiedene Knotentypen per XPath extrahieren")
    void testGetXmlFromXpath() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root attr=\"val\"><!-- comment --><child>text</child></root>";
        
        // Element
        String child = xmlService.getXmlFromXpath(xml, "/root/child");
        assertTrue(child.contains("<child>text</child>"), "Sollte Child-Element enthalten");
        
        // Attribut
        String attr = xmlService.getXmlFromXpath(xml, "/root/@attr");
        assertEquals("val" + System.lineSeparator(), attr, "Sollte Attributwert enthalten");
        
        // Kommentar
        String comment = xmlService.getXmlFromXpath(xml, "/root/comment()");
        assertEquals("<!-- comment -->" + System.lineSeparator(), comment, "Sollte Kommentar enthalten");
        
        // Text
        String text = xmlService.getXmlFromXpath(xml, "/root/child/text()");
        assertEquals("text" + System.lineSeparator(), text, "Sollte Textinhalt enthalten");
    }

    @Test
    @DisplayName("Sollte XPath auf einem bestimmten Knoten evaluieren")
    void testGetNodeFromXpathWithContext() {
        File xmlFile = Paths.get("src/test/resources/advanced_tests/xpath_test.xml").toFile();
        xmlService.setCurrentXmlFile(xmlFile);
        
        Node root = xmlService.getXmlDocument().getDocumentElement();
        Node child = xmlService.getNodeFromXpath("child", root);
        
        assertNotNull(child);
        assertEquals("child", child.getNodeName());
        assertEquals("text", child.getTextContent());
    }

    @Test
    @DisplayName("Sollte XSD-Dokumentation im Root aktualisieren")
    void testUpdateRootDocumentation() throws Exception {
        Path xsdPath = tempDir.resolve("doc_test.xsd");
        Files.write(xsdPath, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"root\"/></xs:schema>".getBytes());
        
        xmlService.updateRootDocumentation(xsdPath.toFile(), "New Root Doc");
        
        String content = Files.readString(xsdPath);
        assertTrue(content.contains("documentation") && content.contains("New Root Doc"), "Dokumentation sollte enthalten sein");
        assertTrue(content.contains("annotation"), "Annotation sollte enthalten sein");
    }
}
