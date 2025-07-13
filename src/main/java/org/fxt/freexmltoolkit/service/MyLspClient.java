package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.fxt.freexmltoolkit.controller.XmlController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implementiert den LanguageClient, um auf Nachrichten vom Server zu reagieren.
 */
public class MyLspClient implements LanguageClient {

    private final static Logger logger = LogManager.getLogger(MyLspClient.class);
    private LanguageServer server;

    private final XmlController xmlController;

    /**
     * Konstruktor, der den XmlController für die Kommunikation entgegennimmt.
     *
     * @param xmlController Die Instanz des XmlControllers.
     */
    public MyLspClient(XmlController xmlController) {
        this.xmlController = xmlController;
    }

    public void connect(LanguageServer server) {
        this.server = server;
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        logger.debug("SERVER MESSAGE: [" + messageParams.getType() + "] " + messageParams.getMessage());
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        logger.debug("SERVER LOG: " + messageParams.getMessage());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        logger.debug("\uD83D\uDCA1 DIAGNOSTICS for {}", diagnostics.getUri());
        for (Diagnostic d : diagnostics.getDiagnostics()) {
            logger.debug("  -> [{}] {} @ Line {}",
                    d.getSeverity(), d.getMessage(), d.getRange().getStart().getLine());
        }
        if (xmlController != null) {
            xmlController.publishDiagnostics(diagnostics);
        }
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        logger.debug("SERVER MESSAGE REQUEST: [" + requestParams.getType() + "] " + requestParams.getMessage());
        List<MessageActionItem> actions = requestParams.getActions();
        if (actions == null || actions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(actions.get(0));
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        logger.debug("SERVER REQUEST: Show document " + params.getUri());
        return CompletableFuture.completedFuture(new ShowDocumentResult(true));
    }

    @Override
    public void telemetryEvent(Object object) {
        logger.debug("SERVER TELEMETRY EVENT: " + (object != null ? object.toString() : "null"));
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        String rootPath = System.getProperty("user.dir");
        String rootUri = java.nio.file.Paths.get(rootPath).toUri().toString();
        WorkspaceFolder folder = new WorkspaceFolder(rootUri, "Test-Workspace");
        return CompletableFuture.completedFuture(java.util.Collections.singletonList(folder));
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        List<Object> results = new java.util.ArrayList<>();
        configurationParams.getItems().forEach(item -> results.add(null));
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        logger.debug("SERVER REQUEST: Apply workspace edit");
        return CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(true));
    }
}