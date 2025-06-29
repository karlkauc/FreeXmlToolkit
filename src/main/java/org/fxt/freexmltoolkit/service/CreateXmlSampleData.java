/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.service;

import org.apache.xerces.impl.xs.XSImplementationImpl;
import org.apache.xerces.xs.*;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;

public class CreateXmlSampleData {

    private final XSModel schema;
    private final Random random = new Random();

    /**
     * Lädt das XSD-Schema aus der angegebenen Datei.
     *
     * @param xsdPath Pfad zur XSD-Datei.
     */
    public CreateXmlSampleData(String xsdPath) {
        System.setProperty(DOMImplementationRegistry.PROPERTY, "org.apache.xerces.dom.DOMXSImplementationSourceImpl");
        XSImplementation impl = new XSImplementationImpl();
        XSLoader schemaLoader = impl.createXSLoader(null);
        this.schema = schemaLoader.loadURI(xsdPath);
    }


    /**
     * Startet die Generierung, indem Wurzelelement und Namespace automatisch
     * aus dem Schema ausgelesen werden.
     *
     * @param outputFilePath Pfad zur Ausgabe-XML-Datei.
     */
    public void generate(String outputFilePath) throws Exception {
        // 1. Target-Namespace auslesen
        var namespaces = schema.getNamespaceItems();
        String targetNamespace = null;
        for (int i = 0; i < namespaces.getLength(); i++) {
            String ns = namespaces.item(i).getSchemaNamespace();
            // Wir suchen den Namespace, der nicht der Standard-XML-Schema-Namespace ist.
            if (ns != null && !ns.equals("http://www.w3.org/2001/XMLSchema")) {
                targetNamespace = ns;
                break;
            }
        }

        if (targetNamespace == null) {
            throw new IllegalStateException("Kein Target-Namespace im Schema gefunden.");
        }

        // 2. Das erste globale Element als Wurzelelement annehmen
        XSObjectList components = (XSObjectList) schema.getComponents(XSConstants.ELEMENT_DECLARATION);
        if (components.getLength() == 0) {
            throw new IllegalStateException("Keine globalen Elemente im Schema als mögliches Wurzelelement gefunden.");
        }
        XSElementDeclaration rootElement = (XSElementDeclaration) components.item(0);
        String rootElementName = rootElement.getName();

        System.out.println("Automatischer Start der Generierung:");
        System.out.println("  -> Namespace: " + targetNamespace);
        System.out.println("  -> Wurzelelement: " + rootElementName);

        // 3. Die ursprüngliche generate-Methode mit den gefundenen Werten aufrufen
        this.generate(rootElementName, targetNamespace, outputFilePath);
    }

    /**
     * Startet die Generierung des XML-Dokuments.
     *
     * @param rootElementName Name des Wurzelelements im Schema.
     * @param targetNamespace Namespace des Schemas.
     * @param outputFilePath  Pfad zur Ausgabe-XML-Datei.
     */
    public void generate(String rootElementName, String targetNamespace, String outputFilePath) throws Exception {
        XSElementDeclaration rootElement = schema.getElementDeclaration(rootElementName, targetNamespace);
        if (rootElement == null) {
            throw new IllegalArgumentException("Wurzelelement '" + rootElementName + "' im Schema nicht gefunden.");
        }

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try (FileWriter fileWriter = new FileWriter(outputFilePath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fileWriter);

            // "Pretty Printing" für lesbare Ausgabe
            writer = new IndentingXMLStreamWriter(writer);

            writer.writeStartDocument("UTF-8", "1.0");
            processElement(rootElement, writer); // Start der rekursiven Verarbeitung
            writer.writeEndDocument();

            writer.flush();
            writer.close();
            System.out.println("XML-Datei erfolgreich generiert: " + outputFilePath);
        }
    }

    /**
     * Rekursive Methode zur Verarbeitung eines Schema-Elements.
     */
    private void processElement(XSElementDeclaration element, XMLStreamWriter writer) throws Exception {
        if (element == null) return;

        // Start-Tag für das aktuelle Element schreiben
        writer.writeStartElement(element.getName());

        var typeDefinition = element.getTypeDefinition();

        if (typeDefinition.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
            processComplexType((XSComplexTypeDefinition) typeDefinition, writer);
        } else if (typeDefinition.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
            writer.writeCharacters(generateSampleValue((XSSimpleTypeDefinition) typeDefinition));
        }

        // End-Tag für das aktuelle Element schreiben
        writer.writeEndElement();
    }

    /**
     * Verarbeitet einen komplexen Typ, d.h. seine Attribute und Kind-Elemente.
     */
    private void processComplexType(XSComplexTypeDefinition complexType, XMLStreamWriter writer) throws Exception {
        // 1. Attribute verarbeiten
        processAttributes(complexType, writer);

        // 2. Kind-Elemente verarbeiten (innerhalb von <xs:sequence>, <xs:choice>, etc.)
        if (complexType.getParticle() != null) {
            XSTerm term = complexType.getParticle().getTerm();
            if (term instanceof XSModelGroup) {
                processModelGroup((XSModelGroup) term, writer);
            }
        }
    }

    /**
     * Verarbeitet eine Gruppe von Partikeln (typischerweise eine Sequenz).
     */
    private void processModelGroup(XSModelGroup modelGroup, XMLStreamWriter writer) throws Exception {
        var particles = modelGroup.getParticles();

        for (int i = 0; i < particles.getLength(); i++) {
            XSObject particle = (XSObject) particles.get(i);
            var term = ((XSTerm) particle);

            // Behandelt verschachtelte Gruppen (selten, aber möglich)
            if (term instanceof XSModelGroup) {
                processModelGroup((XSModelGroup) term, writer);
            }
            // Das häufigste Term ist eine Element-Deklaration
            else if (term instanceof XSElementDeclaration) {
                // minOccurs="0" prüfen
                /* ???
                if (particle.getMinOccurs() == 0 && shouldSkipOptionalElement()) {
                    continue; // Optionales Element überspringen
                }
                 */
                processElement((XSElementDeclaration) term, writer);
            }
        }
    }

    /**
     * Verarbeitet die Attribute eines komplexen Typs.
     */
    private void processAttributes(XSComplexTypeDefinition complexType, XMLStreamWriter writer) throws Exception {
        XSObjectList attributeUses = complexType.getAttributeUses();
        for (int i = 0; i < attributeUses.getLength(); i++) {
            XSAttributeUse attributeUse = (XSAttributeUse) attributeUses.item(i);
            if (attributeUse.getRequired()) { // Nur erforderliche Attribute generieren
                XSAttributeDeclaration attrDecl = attributeUse.getAttrDeclaration();
                String attrValue = generateSampleValue(attrDecl.getTypeDefinition());
                writer.writeAttribute(attrDecl.getName(), attrValue);
            }
        }
    }

    /**
     * Generiert einen Beispielwert basierend auf dem einfachen XSD-Typ.
     * DIES IST DAS "SET VON METHODEN", das Sie angefragt haben.
     */
    private String generateSampleValue(XSSimpleTypeDefinition simpleType) {
        String typeName = simpleType.getName();
        // Wenn der Typ anonym ist, den Basistyp verwenden
        if (typeName == null) {
            typeName = simpleType.getBuiltInKind() + "";
        }

        switch (Objects.requireNonNull(typeName)) {
            case "string":
            case "normalizedString":
                return "Beispieltext";
            case "integer":
            case "int":
                return String.valueOf(100 + random.nextInt(900));
            case "positiveInteger":
                return String.valueOf(1 + random.nextInt(100));
            case "decimal":
                return String.format("%.2f", 10.0 + 90.0 * random.nextDouble());
            case "boolean":
                return String.valueOf(random.nextBoolean());
            case "date":
                return LocalDate.now().minusDays(random.nextInt(365)).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "dateTime":
                return java.time.ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            // ... weitere Typen hier hinzufügen
            default:
                // Prüfen, ob es sich um einen der eingebauten Typen handelt
                switch (simpleType.getBuiltInKind()) {
                    case XSConstants.STRING_DT:
                        return "Beispieltext (built-in)";
                    case XSConstants.INT_DT:
                        return "123";
                    // ...
                }
                return "UnbekannterTyp: " + typeName;
        }
    }

    /**
     * Entscheidet zufällig, ob ein optionales Element (minOccurs="0") übersprungen wird.
     * Hier wird es immer generiert, kann aber angepasst werden.
     */
    private boolean shouldSkipOptionalElement() {
        // return random.nextBoolean(); // 50% Chance zum Überspringen
        return false; // Optionale Elemente immer generieren
    }
}
