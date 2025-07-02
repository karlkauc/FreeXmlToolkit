/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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


import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class SchemaPrefixFinderTest {
    /**
     * Ermittelt das Namespace-Präfix des Wurzelelements in einer XSD-Datei.
     *
     * @param filePath Der Pfad zur XSD-Datei.
     * @return Das Präfix ("xs", "xsd", etc.) oder null, wenn keines gefunden wurde oder ein Fehler auftrat.
     */
    public static String getSchemaPrefix(String filePath) {
        try {
            File xsdFile = new File(filePath);

            // 1. Factory und Builder erstellen
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Wichtig: Namespace-Unterstützung aktivieren
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 2. XSD-Datei parsen
            Document doc = builder.parse(xsdFile);

            // 3. Wurzelelement holen
            Element rootElement = doc.getDocumentElement();

            // 4. Überprüfen, ob es sich um das Schema-Element handelt
            // Der "local name" ist der Name ohne Präfix.
            if (rootElement != null && "schema".equals(rootElement.getLocalName())) {
                // 5. Das Präfix zurückgeben
                return rootElement.getPrefix();
            }

        } catch (Exception e) {
            System.err.println("Fehler beim Parsen der XML-Datei: " + e.getMessage());
            e.printStackTrace();
        }

        return null; // Falls etwas schiefgeht oder es kein Schema ist
    }

    @Test
    public void testFind() {
        // Passe den Pfad zu deiner FundsXML4.xsd Datei an
        String xsdPath = "C:/Data/src/FreeXmlToolkit/release/examples/xsd/FundsXML4.xsd";

        String prefix = getSchemaPrefix(xsdPath);

        if (prefix != null) {
            System.out.println("Das Schema verwendet das Präfix: \"" + prefix + "\"");

            if ("xs".equals(prefix)) {
                System.out.println("-> Es ist 'xs:'.");
            } else if ("xsd".equals(prefix)) {
                System.out.println("-> Es ist 'xsd:'.");
            } else {
                System.out.println("-> Es ist ein anderes Präfix als 'xs' oder 'xsd'.");
            }
        } else {
            System.out.println("Konnte das Schema-Präfix nicht ermitteln.");
        }
    }
}
