package org.fxt.freexmltoolkit;

import org.junit.jupiter.api.Test;

import java.io.File;

public class GenerateXmlDataTest {

    @Test
    void testCreate() {
        System.out.println("Starte Generierung von XML-Daten...");
        try {
            File xsdFile = new File("src/test/resources/FundsXML_428.xsd");
            // CreateXmlSampleData generator = new CreateXmlSampleData(xsdFile.toURI().toString());
            // generator.generate("FundsXML4", "http://www.altova.com/xml-schema-extensions",".");
            // generator.generate("beispiel-person-dynamisch.xml");
        } catch (Exception e) {
            System.err.println("Ein Fehler ist aufgetreten:");
            e.printStackTrace();
        }
    }
}
