package org.fxt.freexmltoolkit;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Eine einfache Implementierung des LanguageClient.
 * In einer echten Anwendung würden diese Methoden z.B. UI-Elemente
 * aktualisieren, um Diagnosen (Fehler/Warnungen) anzuzeigen.
 */
public class MyLspClient implements LanguageClient {
    private LanguageServer server;

    public void connect(LanguageServer server) {
        this.server = server;
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        System.out.println("Server Message: [" + messageParams.getType() + "] " + messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        // Nicht implementiert für dieses Beispiel
        return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        // Nicht implementiert für dieses Beispiel
        return null;
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        System.out.println("Server Log: " + messageParams.getMessage());
    }

    @Override
    public void telemetryEvent(Object object) {
        // Nicht implementiert für dieses Beispiel
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        // Nicht implementiert für dieses Beispiel
        return new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        // Nicht implementiert für dieses Beispiel
        return new CompletableFuture<>();
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        System.out.println("DIAGNOSTICS for " + diagnostics.getUri());
        for (Diagnostic d : diagnostics.getDiagnostics()) {
            System.out.printf("  -> [%s] %s @ Line %d%n",
                    d.getSeverity(), d.getMessage(), d.getRange().getStart().getLine());
        }
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        // Nicht implementiert für dieses Beispiel
        return new CompletableFuture<>();
    }

    // Viele weitere Methoden mit Default-Implementierungen...
}