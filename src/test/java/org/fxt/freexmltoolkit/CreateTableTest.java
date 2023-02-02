package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

public class CreateTableTest {
    final static String fileName = "src/test/resources/FundsXML4_2_0.xsd";

    @Test
    void createHtmlTable() {
        XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();
        xsdDocumentationService.setXsdFilePath(fileName);
        xsdDocumentationService.generateDocumentation();
    }
}
