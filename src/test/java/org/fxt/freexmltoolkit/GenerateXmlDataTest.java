package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.service.CreateXmlSampleData;
import org.junit.jupiter.api.Test;

import java.io.File;

public class GenerateXmlDataTest {

    @Test
    void testCreate() {
        try {
            // Pfad zur XSD-Datei im resources-Ordner holen
            File xsdFile = new File("src/test/resources/FundsXML_428.xml");

            // Generator instanziieren
            CreateXmlSampleData generator = new CreateXmlSampleData(xsdFile.toURI().toString());

            // XML-Generierung starten
            generator.generate("FundsXML4", null, "beispiel-person-dynamisch.xml");

        } catch (Exception e) {
            System.err.println("Ein Fehler ist aufgetreten:");
            e.printStackTrace();
        }
    }
}
