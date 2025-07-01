package org.fxt.freexmltoolkit;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implementiert den LanguageClient, um auf Nachrichten vom Server zu reagieren.
 * Empfängt und verarbeitet z. B. Fehlerdiagnosen.
 */
public class MyLspClient implements LanguageClient {

    private LanguageServer server;

    /**
     * Speichert eine Referenz auf den Server-Proxy, um bei Bedarf
     * Nachrichten an den Server senden zu können.
     *
     * @param server Der Language-Server-Proxy.
     */
    public void connect(LanguageServer server) {
        this.server = server;
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        System.out.println("SERVER MESSAGE: [" + messageParams.getType() + "] " + messageParams.getMessage());
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        System.out.println("SERVER LOG: " + messageParams.getMessage());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        System.out.println("💡 DIAGNOSTICS for " + diagnostics.getUri());
        for (Diagnostic d : diagnostics.getDiagnostics()) {
            System.out.printf("  -> [%s] %s @ Line %d%n",
                    d.getSeverity(), d.getMessage(), d.getRange().getStart().getLine());
        }
    }

    // -- Die restlichen Methoden der Schnittstelle sind hier nicht implementiert --
    // -- Sie haben Standardimplementierungen oder können bei Bedarf überschrieben werden --

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        return null;
    }

    @Override
    public void telemetryEvent(Object object) {
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        return new CompletableFuture<>();
    }
}