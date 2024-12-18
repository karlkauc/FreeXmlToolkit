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

import org.fxt.freexmltoolkit.service.FOPService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;

public class FopTest {

    @Test
    void createPdfTest() {
        File xmlfile = new File("src/test/resources/projectteam.xml");
        File xsltfile = new File("src/test/resources/projectteam2fo.xsl");
        File pdffile = new File("output/ResultXML2PDF.pdf");

        HashMap<String, String> parameter = new HashMap<>();
        parameter.put("versionParam", "3");

        FOPService fopService = new FOPService();
        fopService.createPdfFile(xmlfile, xsltfile, pdffile, null);
    }
}
