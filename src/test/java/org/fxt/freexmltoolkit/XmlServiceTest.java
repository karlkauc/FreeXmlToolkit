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

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // Überprüfung der spezifischen Fehlermeldung. .get(0) ist kompatibler als .getFirst().
        assertTrue(errors.get(0).getMessage().contains("Content is not allowed in prolog"), "Die Fehlermeldung sollte auf ein Problem im Prolog hinweisen.");
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
}