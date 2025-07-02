package org.fxt.freexmltoolkit;

import org.eclipse.lemminx.XMLServerLauncher;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EmbeddedLemminxExample {

    public static void main(String[] args) throws Exception {
        // --- Datei für das Beispiel erstellen ---
        Path beispielXmlPath = Paths.get("beispiel.xml");
        String fileContent = "<root>\n  <element attr='val' />\n</root"; // Absichtlich fehlerhaft
        Files.writeString(beispielXmlPath, fileContent);
        String fileUri = beispielXmlPath.toUri().toString();

        // 1. Server und Client instanziieren

        MyLspClient client = new MyLspClient();

        // 2. In-Memory-Streams für die bidirektionale Kommunikation erstellen
        PipedInputStream clientInputStream = new PipedInputStream();
        OutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
        PipedInputStream serverInputStream = new PipedInputStream();
        OutputStream clientOutputStream = new PipedOutputStream(serverInputStream);

        // 3. Launcher für den Client mit dem Builder erstellen
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Launcher<LanguageServer> launcher = new LSPLauncher.Builder<LanguageServer>()
                .setLocalService(client)
                .setRemoteInterface(LanguageServer.class)
                .setInput(clientInputStream)
                .setOutput(clientOutputStream)
                .setExecutorService(executor)
                .create();

        // 4. Server-Proxy holen und mit dem Client verbinden
        LanguageServer serverProxy = launcher.getRemoteProxy();
        client.connect(serverProxy);

        // 5. Client-Listener-Thread starten
        Future<?> clientListening = launcher.startListening();

        // 6. Server starten und mit den Streams verbinden
        XMLServerLauncher.launch(serverInputStream, serverOutputStream);
        System.out.println("🚀 Server und Client gestartet und verbunden.");

        // 7. Initialisierungsparameter vorbereiten (moderner Stil)
        InitializeParams initParams = new InitializeParams();
        initParams.setProcessId((int) ProcessHandle.current().pid());

        // Workspace Folder definieren
        WorkspaceFolder workspaceFolder = new WorkspaceFolder(Paths.get(".").toUri().toString(), "lemminx-project");
        initParams.setWorkspaceFolders(Collections.singletonList(workspaceFolder));

        // Client-Fähigkeiten setzen, um Workspace-Folder-Support zu signalisieren
        ClientCapabilities capabilities = new ClientCapabilities();

        /*
        WorkspaceCapabilities workspaceCapabilities = new WorkspaceCapabilities();
        WorkspaceFolderCapabilities workspaceFolderCapabilities = new WorkspaceFolderCapabilities();
        workspaceFolderCapabilities.setSupported(true);
        workspaceCapabilities.setWorkspaceFolders(workspaceFolderCapabilities);
        capabilities.setWorkspace(workspaceCapabilities);
        initParams.setCapabilities(capabilities);
        */

        // 8. LSP-Handshake durchführen
        System.out.println("🤝 Sende 'initialize' Anfrage...");
        InitializeResult initResult = serverProxy.initialize(initParams).get();
        serverProxy.initialized(new InitializedParams());
        System.out.println("...Initialisierung abgeschlossen.");

        // 9. Dokument öffnen, um eine Analyse auszulösen
        System.out.println("📂 Sende 'textDocument/didOpen' für: " + fileUri);
        TextDocumentItem textDocument = new TextDocumentItem(fileUri, "xml", 1, fileContent);
        serverProxy.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument));

        // Kurz warten, damit der Server die Diagnosen verarbeiten und senden kann
        Thread.sleep(2000);

        // 10. Server und Client sauber herunterfahren
        System.out.println("🔌 Fahre Server herunter...");
        serverProxy.shutdown().get();
        serverProxy.exit();
        System.out.println("...Server heruntergefahren.");

        // 11. Threads und Ressourcen freigeben
        clientListening.cancel(true);
        executor.shutdown();
        System.out.println("✅ Beispiel beendet.");
    }
}