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
import org.junit.jupiter.api.Test;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class XmlServiceTest {

    @Test
    public void testUnPretty() throws IOException, TransformerException {
        File fundsXML420File = new File("src/test/resources/FundsXML_420.xml");
        var content = Files.readAllLines(fundsXML420File.toPath());

        var mini = XmlService.convertXmlToOneLine(String.join("", content));

        System.out.println("mini = " + mini);
        Files.write(Path.of("test22.xml"), mini.getBytes());

    }

}
