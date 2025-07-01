package org.fxt.freexmltoolkit;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EmbeddedLemminxExample {

    public static void main(String[] args) throws Exception {
        // 1. Erstellen Sie den Server und den Client
        LemminxServer server = new LemminxServer();
        MyLspClient client = new MyLspClient();

        // 2. Erstellen Sie In-Memory-Streams für die Kommunikation (unverändert)
        PipedInputStream clientInputStream = new PipedInputStream();
        PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
        PipedInputStream serverInputStream = new PipedInputStream();
        PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);


        // 3. Erstellen Sie den Launcher für den Client mit dem Builder
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Launcher<LanguageServer> launcher = new LSPLauncher.Builder<LanguageServer>()
                .setLocalService(client)
                .setRemoteInterface(LanguageServer.class)
                .setInput(clientInputStream)
                .setOutput(clientOutputStream)
                .setExecutorService(executor)
                .create();

        // 4. Holen Sie sich den Server-Proxy und verbinden Sie ihn mit dem Client
        LanguageServer serverProxy = launcher.getRemoteProxy();
        client.connect(serverProxy); // Geben Sie dem Client eine Referenz auf den Server

        // 5. Starten Sie den Listener-Thread des Clients (dies funktioniert jetzt)
        Future<?> clientListening = launcher.startListening();

        // 6. Verbinden und starten Sie den Server (unverändert)
        server.start(serverInputStream, serverOutputStream);


        // --- Initialisierung des Language Servers (Handshake) ---

        // 7. Senden Sie die "initialize" Anfrage
        InitializeParams initParams = new InitializeParams();
        initParams.setProcessId((int) ProcessHandle.current().pid());
        initParams.setRootUri(Paths.get(".").toUri().toString()); // Projekt-Root
        initParams.setCapabilities(new ClientCapabilities()); // Leere Capabilities für das Beispiel
        // Für Leminx-spezifische Einstellungen können Sie hier JSON übergeben:
        // initParams.setInitializationOptions(getLemminxSettings());

        CompletableFuture<InitializeResult> initResult = serverProxy.initialize(initParams);
        initResult.get();

        // 8. Senden Sie die "initialized" Benachrichtigung
        serverProxy.initialized(new InitializedParams());

        System.out.println("Server-Client-Verbindung hergestellt.");

        // --- Nun können Sie mit dem Server arbeiten ---

        // 9. Simulieren Sie das Öffnen eines XML-Dokuments
        String fileUri = Paths.get("beispiel.xml").toUri().toString();
        String fileContent = "<root>\n  <element attr='val' />\n</root"; // Absichtlich fehlerhaft

        TextDocumentItem textDocument = new TextDocumentItem(fileUri, "xml", 1, fileContent);
        serverProxy.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument));

        // Warten Sie einen Moment, damit der Server Zeit hat, die Diagnosen zu senden
        Thread.sleep(2000);

        // 10. Fahren Sie den Server sauber herunter
        System.out.println("Server wird heruntergefahren...");
        serverProxy.shutdown().get();
        serverProxy.exit();

        // Aufräumen
        clientListening.cancel(true);
        executor.shutdown();
        System.out.println("Beendet.");
    }
}