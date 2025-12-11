/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class XmlServiceTest {

    private XmlService xmlService;

    @BeforeEach
    void setUp() {
        // Initialisiert eine neue Service-Instanz vor jedem Test
        xmlService = new XmlServiceImpl();
    }

    @DisplayName("Sollte wohlgeformte XML-Strings ohne Fehler validieren")
    @ParameterizedTest(name = "Input: {0}")
    @ValueSource(strings = {
            "<a><b>text</b></a>",
            "<root><child/></root>",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><note><to>Tove</to><from>Jani</from></note>",
            "<a/>",
            "<!-- comment --><root/>"
    })
    void testWellFormedXml(String validXml) {
        // Wir übergeben 'null' für die Schema-Datei, um nur die Wohlgeformtheit zu prüfen.
        List<SAXParseException> errors = xmlService.validateText(validXml, null);

        // KORREKTUR: Die Assertion-Nachricht wird nur generiert, wenn der Test fehlschlägt.
        // Das ist effizienter und liefert deutlich bessere und besser formatierte Testberichte.
        assertTrue(errors.isEmpty(),
                () -> "Ein wohlgeformter XML-String sollte keine Fehler haben, aber es wurden folgende gefunden:\n" +
                        errors.stream()
                                .map(e -> String.format("- Zeile %d, Spalte %d: %s", e.getLineNumber(), e.getColumnNumber(), e.getMessage()))
                                .collect(Collectors.joining("\n")));
    }

    @DisplayName("Sollte fehlerhafte XML-Strings mit Fehlern validieren")
    @ParameterizedTest(name = "Input: {0}")
    @ValueSource(strings = {
            "<a><b></a>", // Falsch geschlossenes Tag
            "<root><child></root>", // Fehlendes schließendes Tag für 'child'
            "Text vor dem Root-Element<root/>", // Textinhalt außerhalb des Root-Elements
            "<root><unclosed>", // Nicht geschlossenes Tag
            "<a><b><c></a></b></c>", // Falsch verschachtelte Tags
            "<?xml version=\"1.0\"?><root><a></b></root>" // Falsch geschlossenes Tag im Inneren
    })
    void testMalformedXml(String malformedXml) {
        List<SAXParseException> errors = xmlService.validateText(malformedXml, null);
        assertFalse(errors.isEmpty(), "Ein fehlerhafter XML-String sollte mindestens einen Validierungsfehler haben.");
    }

    @Test
    @DisplayName("Sollte XML mit führendem Leerzeichen vor der Deklaration als fehlerhaft erkennen")
    void testXmlWithLeadingWhitespace() {
        String xmlWithLeadingSpace = " <?xml version=\"1.0\"?><root/>";
        List<SAXParseException> errors = xmlService.validateText(xmlWithLeadingSpace, null);
        assertFalse(errors.isEmpty(), "XML mit führendem Leerzeichen vor der Deklaration sollte als fehlerhaft gelten.");
        // Check for error message indicating problem in prolog or processing instruction
        String errorMsg = errors.get(0).getMessage().toLowerCase();
        assertTrue(errorMsg.contains("prolog") || errorMsg.contains("content") ||
                        errorMsg.contains("processing instruction") || errorMsg.contains("target"),
                "Die Fehlermeldung sollte auf ein Problem im Prolog oder der XML-Deklaration hinweisen. Actual: " + errors.get(0).getMessage());
    }

    @Test
    @DisplayName("Sollte einen leeren String als fehlerhaft erkennen")
    void testEmptyStringIsMalformed() {
        String emptyXml = "";
        List<SAXParseException> errors = xmlService.validateText(emptyXml, null);
        assertFalse(errors.isEmpty(), "Ein leerer String ist kein wohlgeformtes XML und sollte einen Fehler erzeugen.");
        assertTrue(errors.get(0).getMessage().contains("Premature end of file"), "Die Fehlermeldung für einen leeren String sollte 'Premature end of file' sein.");
    }

    @Test
    @DisplayName("Sollte einen String, der nur aus Leerzeichen besteht, als fehlerhaft erkennen")
    void testWhitespaceStringIsMalformed() {
        String whitespaceXml = "   \n\t   ";
        List<SAXParseException> errors = xmlService.validateText(whitespaceXml, null);
        assertFalse(errors.isEmpty(), "Ein String nur mit Leerzeichen ist kein wohlgeformtes XML und sollte einen Fehler erzeugen.");
        assertTrue(errors.get(0).getMessage().contains("Premature end of file"), "Die Fehlermeldung für einen reinen Leerzeichen-String sollte 'Premature end of file' sein.");
    }

    @Test
    @DisplayName("Should load local XSD schema from XML file with relative schema location")
    void testLoadLocalSchemaFromXMLFile() {
        // Arrange: Get the test XML file with local schema reference
        File testXmlFile = Paths.get("src/test/resources/FundsXML_428.xml").toFile();
        File expectedXsdFile = Paths.get("src/test/resources/FundsXML_428.xsd").toFile();

        assertTrue(testXmlFile.exists(), "Test XML file should exist: " + testXmlFile.getAbsolutePath());
        assertTrue(expectedXsdFile.exists(), "Test XSD file should exist: " + expectedXsdFile.getAbsolutePath());

        // Act: Set the XML file and try to load the schema
        xmlService.setCurrentXmlFile(testXmlFile);
        boolean schemaLoaded = xmlService.loadSchemaFromXMLFile();

        // Assert: Schema should be loaded successfully
        assertTrue(schemaLoaded, "Schema should be loaded from local file");

        File loadedXsdFile = xmlService.getCurrentXsdFile();
        assertNotNull(loadedXsdFile, "Loaded XSD file should not be null");
        assertEquals(expectedXsdFile.getAbsolutePath(), loadedXsdFile.getAbsolutePath(),
                "Loaded XSD file should match the expected local schema file");

        // Verify the schema name was detected correctly
        String schemaName = xmlService.getSchemaNameFromCurrentXMLFile().orElse(null);
        assertNotNull(schemaName, "Schema name should be detected from XML file");
        assertTrue(schemaName.startsWith("file://") || schemaName.contains("FundsXML_428.xsd"),
                "Schema name should be a file:// URL or contain the XSD filename");
    }

    @Test
    @DisplayName("Should correctly format XML with excessive whitespace in text nodes")
    void testPrettyFormatRemovesExcessiveWhitespace() {
        // This test case matches the user's reported issue
        String inputXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n   h\n   </root>";

        String formatted = XmlService.prettyFormat(inputXml, 2);

        assertNotNull(formatted, "Formatted XML should not be null");

        // The formatted output should have the text "h" without excessive surrounding whitespace
        // It should look like: <root>\n  h\n</root>
        assertTrue(formatted.contains("<root>"), "Formatted XML should contain root element");
        assertTrue(formatted.contains("h"), "Formatted XML should contain the text content 'h'");

        // Verify that excessive whitespace is removed - the formatted output should not
        // have multiple spaces/newlines around the text "h"
        assertFalse(formatted.contains("   h   "),
                "Formatted XML should not contain excessive whitespace around text content");

        // Verify the text content is trimmed
        assertTrue(formatted.contains(">h<"),
                "Text content should be trimmed (no whitespace between tags and content)");
    }

    @Test
    @DisplayName("Should preserve meaningful whitespace in mixed content")
    void testPrettyFormatPreservesMeaningfulWhitespace() {
        String inputXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><text>Hello World</text></root>";

        String formatted = XmlService.prettyFormat(inputXml, 2);

        assertNotNull(formatted, "Formatted XML should not be null");
        assertTrue(formatted.contains("Hello World"),
                "Formatted XML should preserve meaningful whitespace in text content");
    }

    @Test
    @DisplayName("Should handle nested elements with whitespace correctly")
    void testPrettyFormatNestedElementsWithWhitespace() {
        String inputXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>  \n  <child>  value  </child>  \n</root>";

        String formatted = XmlService.prettyFormat(inputXml, 2);

        assertNotNull(formatted, "Formatted XML should not be null");
        assertTrue(formatted.contains("<root>"), "Formatted XML should contain root element");
        assertTrue(formatted.contains("<child>"), "Formatted XML should contain child element");
        assertTrue(formatted.contains("value"), "Formatted XML should contain text value");

        // Verify whitespace-only nodes are removed and text is trimmed
        assertFalse(formatted.contains("  value  "),
                "Excessive whitespace around text should be removed");
        assertTrue(formatted.contains(">value<"),
                "Text should be trimmed in formatted output");
    }

    // ==================== Single-Line XML Detection Tests ====================

    @Test
    @DisplayName("Should detect single-line XML (no newlines)")
    void testIsSingleLineXml_singleLine() {
        String singleLine = "<root><child>text</child></root>";
        assertTrue(XmlService.isSingleLineXml(singleLine),
                "XML with no newlines should be detected as single-line");
    }

    @Test
    @DisplayName("Should detect XML with 2 lines as single-line")
    void testIsSingleLineXml_twoLines() {
        String twoLines = "<root>\n<child>text</child></root>";
        assertTrue(XmlService.isSingleLineXml(twoLines),
                "XML with 2 lines should be detected as single-line");
    }

    @Test
    @DisplayName("Should detect XML with 3 lines as single-line")
    void testIsSingleLineXml_threeLines() {
        String threeLines = "<root>\n<child>text</child>\n</root>";
        assertTrue(XmlService.isSingleLineXml(threeLines),
                "XML with 3 lines should be detected as single-line");
    }

    @Test
    @DisplayName("Should NOT detect multi-line XML as single-line")
    void testIsSingleLineXml_multiLine() {
        String multiLine = "<root>\n  <child1>text1</child1>\n  <child2>text2</child2>\n</root>";
        assertFalse(XmlService.isSingleLineXml(multiLine),
                "XML with 4+ lines should NOT be detected as single-line");
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void testIsSingleLineXml_null() {
        assertFalse(XmlService.isSingleLineXml(null),
                "Null input should return false");
    }

    @Test
    @DisplayName("Should handle empty input gracefully")
    void testIsSingleLineXml_empty() {
        assertFalse(XmlService.isSingleLineXml(""),
                "Empty input should return false");
    }

    @Test
    @DisplayName("Should detect properly formatted XML as NOT single-line")
    void testIsSingleLineXml_formattedXml() {
        String formatted = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <child>text</child>
                </root>
                """;
        assertFalse(XmlService.isSingleLineXml(formatted),
                "Properly formatted XML should NOT be detected as single-line");
    }
}