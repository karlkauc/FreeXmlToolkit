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

package org.fxt.freexmltoolkit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class SVGTest {

    private final static Logger logger = LogManager.getLogger(SVGTest.class);

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    File outputDir = new File("output//svg//");

    @Test
    public void generateTemplate() throws IOException {
        logger.debug("DRINNEN!!!");

        xsdDocumentationService.setXsdFilePath("src/test/resources/FundsXML_306.xsd");
        xsdDocumentationService.generateXsdDocumentation(outputDir);
    }


}
