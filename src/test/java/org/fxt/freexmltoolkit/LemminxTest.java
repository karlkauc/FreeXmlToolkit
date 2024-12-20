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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lemminx.XMLLanguageServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class LemminxTest {

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void testStart() {
        logger.debug("Starting LemMinX Server");
        try {
            // Start the LemMinX server
            XMLLanguageServer server = new XMLLanguageServer();

            // Create input and output streams for communication
            InputStream in = System.in;
            OutputStream out = System.out;

            // Load and process the XML file
            String xmlFilePath = "src/test/resources/FundsXML_306.xml";
            Path p = Paths.get(xmlFilePath);

            FileInputStream fis = new FileInputStream(p.toFile());

            // Launch the server
            Launcher<LanguageServer> serverLauncher = Launcher.createLauncher(server, LanguageServer.class, fis, out);
            LanguageServer languageServer = serverLauncher.getRemoteProxy();
            serverLauncher.startListening();
            RemoteEndpoint remoteEndpoint = serverLauncher.getRemoteEndpoint();


            TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier();
            textDocumentIdentifier.setUri(p.toUri().toString());

            // Create CompletionParams
            CompletionParams completionParams = new CompletionParams(
                    textDocumentIdentifier,
                    new Position(0, 0) // Adjust the position as needed
            );

            // Call textDocument/completion
            CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion = languageServer.getTextDocumentService().completion(completionParams);
            Either<List<CompletionItem>, CompletionList> completionResult = completion.get(5, TimeUnit.SECONDS);
            System.out.println("Completion result: " + completionResult);

            // Call textDocument/hover
            /*HoverParams hoverParams = new HoverParams(new TextDocumentIdentifier(xmlFilePath), new Position(0, 0));
            CompletableFuture<Hover> hover = languageServer.getTextDocumentService().hover(hoverParams);
            Hover hoverResult = hover.get();
            System.out.println("Hover result: " + hoverResult);
             */

            // Call textDocument/formatting
            DocumentFormattingParams formattingParams = new DocumentFormattingParams(new TextDocumentIdentifier(xmlFilePath), new FormattingOptions(2, true));
            var formatting = languageServer.getTextDocumentService().formatting(formattingParams);
            var formattingResult = formatting.get();
            System.out.println("Formatting result: " + formattingResult);

            // Call textDocument/documentSymbol
            DocumentSymbolParams symbolParams = new DocumentSymbolParams(new TextDocumentIdentifier(xmlFilePath));
            CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> symbols = languageServer.getTextDocumentService().documentSymbol(symbolParams);
            List<Either<SymbolInformation, DocumentSymbol>> symbolsResult = symbols.get();
            System.out.println("Document symbols result: " + symbolsResult);

            //Either<List<CompletionItem>, CompletionList> result = completion.get();
            //System.out.println("Completion result: " + result);

            System.out.println("LemMinX server started and XML file processed.");

            // server.exit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
