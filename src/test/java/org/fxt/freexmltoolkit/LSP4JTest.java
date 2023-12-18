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

import org.eclipse.lemminx.XMLServerLauncher;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.commons.TextDocument;
import org.eclipse.lemminx.customservice.AutoCloseTagResponse;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class LSP4JTest {

    @Test
    public void t1() {
        try {
            InputStream in = System.in;
            OutputStream out = System.out;

            PipedInputStream inClient = new PipedInputStream();
            PipedOutputStream outClient = new PipedOutputStream();
            PipedInputStream inServer = new PipedInputStream();
            PipedOutputStream outServer = new PipedOutputStream();

            inClient.connect(outServer);
            outClient.connect(inServer);

            var y = XMLServerLauncher.launch(inClient, outClient);
            System.out.println("y.toString() = " + y.toString());

        } catch (Exception e) {
            System.out.println("e.getMessage() = " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Test
    public void t2() throws BadLocationException {
        XMLLanguageService ls = new XMLLanguageService();
        var settings = new SharedSettings();

        String value = "<html><|div/></html>";
        value = "<div>|";

        value = """
                <FundsXML4 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://github.com/fundsxml/schema/releases/download/4.2.2/FundsXML.xsd">
                    <ControlData>|
                        <UniqueDocumentID>EAM_FUND_001</UniqueDocumentID>
                        <DocumentGenerated>2021-11-30T16:14:04</DocumentGenerated>
                        <ContentDate>2021-11-32</ContentDate>
                        <DataSupplier>
                            <SystemCountry>AT</SystemCountry>
                            <Short>EAM</Short>
                            <Name>Erste Asset Management GmbH</Name>
                            <Type>Asset Manager</Type>
                            <Contact>
                                <Email>datamanagement@erste-am.com</Email>
                            </Contact>
                        </DataSupplier>
                    </ControlData>
                </FundsXML4>
                """;

        TextDocument document = new TextDocument(value, "test://test/test.html");
        int offset = value.indexOf('|');
        Position position = document.positionAt(offset);
        position.setLine(2);

        DOMDocument htmlDoc = DOMParser.getInstance().parse(document, ls.getResolverExtensionManager());
        AutoCloseTagResponse response = ls.doTagComplete(htmlDoc, settings.getCompletionSettings(), position);

        System.out.println("position = " + position.getCharacter());
        if (response != null) {
            System.out.println("response = " + response.snippet);
        } else {
            System.out.println("NULL");
        }


        System.out.println("htmlDoc.getTextDocument().toString() = " + htmlDoc.getTextDocument().toString());
    }
}
