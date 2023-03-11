/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class ValidationTest {

    XmlService xmlService = XmlServiceImpl.getInstance();
    File fundsXML306File = new File("src/test/resources/FundsXML_306.xml");
    File fundsXml306Schema = new File("src/test/resources/FundsXML_306.xsd");

    File fundsXML420File = new File("src/test/resources/FundsXML_420.xml");
    File fundsXML420ErrorFile = new File("src/test/resources/FundsXML_420_Error.xml");
    File fundsXMl420Schema = new File("src/test/resources/FundsXML_420.xsd");

    @Test
    public void testFiles() {
        Assertions.assertNotNull(xmlService);
        Assertions.assertTrue(fundsXML306File.exists());
        Assertions.assertTrue(fundsXml306Schema.exists());

        Assertions.assertTrue(fundsXML420File.exists());
        Assertions.assertTrue(fundsXMl420Schema.exists());
    }

    @Test
    public void testFundsXML420SchemaValid() {
        xmlService.setCurrentXmlFile(fundsXML420File);
        xmlService.setCurrentXsdFile(fundsXMl420Schema);

        var errors = xmlService.validate();
        Assertions.assertEquals(0, errors.size());

        try {
            String xmlFileContent = Files.readString(fundsXML420File.toPath());
            assert xmlFileContent.length() > 1;
            errors = xmlService.validateText(xmlFileContent);
            Assertions.assertEquals(0, errors.size());
        } catch (IOException ignore) {
        }

        try {
            String xmlFileContent = Files.readString(fundsXML420File.toPath());
            assert xmlFileContent.length() > 1;

            errors = xmlService.validateText(xmlFileContent, fundsXMl420Schema);
            Assertions.assertEquals(0, errors.size());
        } catch (IOException ignore) {
        }
    }

    @Test
    public void testFundsXML420SchemaInvalid() {
        this.xmlService.setCurrentXmlFile(fundsXML420ErrorFile);
        Assertions.assertTrue(this.xmlService.loadSchemaFromXMLFile());

        var errors = xmlService.validate();
        System.out.println("errors = " + errors);
        Assertions.assertEquals(2, errors.size());
    }

    @Test
    public void testSchemaLocationFinder422() {
        xmlService.setCurrentXmlFile(fundsXML420File);
        Assertions.assertNotNull(xmlService.getCurrentXmlFile());

        var remoteXsdLocation = xmlService.getRemoteXsdLocation();
        Assertions.assertEquals("https://github.com/fundsxml/schema/releases/download/4.2.2/FundsXML.xsd", remoteXsdLocation);
    }

    @Test
    public void testSchemaLocationFinder306() {
        xmlService.setCurrentXmlFile(fundsXML306File);
        assert xmlService.getCurrentXmlFile() != null;

        var remoteXsdLocation = xmlService.getRemoteXsdLocation();
        System.out.println("remoteXsdLocation = " + remoteXsdLocation);
        Assertions.assertEquals("https://github.com/fundsxml/schema/releases/download/3.0.6/FundsXML.xsd", remoteXsdLocation);
    }

    @Disabled("fix FundsXML 306 XML File before!")
    @Test
    public void setFundsXML306SchemaValidation() {
        xmlService.setCurrentXmlFile(fundsXML306File);
        xmlService.setCurrentXsdFile(fundsXml306Schema);

        Assertions.assertTrue(xmlService.getCurrentXmlFile().exists());
        Assertions.assertTrue(xmlService.getCurrentXsdFile().exists());

        Assertions.assertTrue(xmlService.getCurrentXmlFile().length() > 0);
        Assertions.assertTrue(xmlService.getCurrentXsdFile().length() > 0);

        List<SAXParseException> errors = null;
        // default validation
        errors = xmlService.validate();
        errors.forEach(e -> System.out.println(e.getMessage()));

        Assertions.assertEquals(0, errors.size());

    }
}
