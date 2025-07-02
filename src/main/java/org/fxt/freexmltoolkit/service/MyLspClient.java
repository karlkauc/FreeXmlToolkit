package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private final static Logger logger = LogManager.getLogger(MyLspClient.class);
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
        logger.debug("SERVER MESSAGE: [" + messageParams.getType() + "] " + messageParams.getMessage());
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        logger.debug("SERVER LOG: " + messageParams.getMessage());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        logger.debug("💡 DIAGNOSTICS for " + diagnostics.getUri());
        for (Diagnostic d : diagnostics.getDiagnostics()) {
            logger.debug("  -> [{}] {} @ Line {}",
                    d.getSeverity(), d.getMessage(), d.getRange().getStart().getLine());
        }
    }

    // -- Die restlichen Methoden der Schnittstelle sind hier nicht implementiert --
    // -- Sie haben Standardimplementierungen oder können bei Bedarf überschrieben werden --
    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        // Loggt die Anfrage des Servers, einschließlich Nachricht und Typ.
        logger.debug("SERVER MESSAGE REQUEST: [" + requestParams.getType() + "] " + requestParams.getMessage());

        List<MessageActionItem> actions = requestParams.getActions();

        // Prüft, ob Aktionen (Schaltflächen) vorhanden sind.
        if (actions == null || actions.isEmpty()) {
            // Wenn keine Aktionen vorhanden sind, wird der Future mit null abgeschlossen.
            return CompletableFuture.completedFuture(null);
        }

        // Gibt die verfügbaren Aktionen auf der Konsole aus.
        logger.debug("  Available actions:");
        for (int i = 0; i < actions.size(); i++) {
            logger.debug("    ({}) {}", i + 1, actions.get(i).getTitle());
        }

        // Für diesen einfachen Test-Client wählen wir automatisch die erste Aktion aus.
        // Eine echte Anwendung würde hier auf eine Benutzereingabe in einem Dialog warten.
        MessageActionItem selectedAction = actions.get(0);
        logger.debug("  -> Automatically selecting first action: '{}'", selectedAction.getTitle());

        // Gibt die ausgewählte Aktion an den Server zurück, indem der Future abgeschlossen wird.
        return CompletableFuture.completedFuture(selectedAction);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        // Loggt die Anfrage des Servers, ein Dokument anzuzeigen.
        logger.debug("SERVER REQUEST: Show document " + params.getUri());
        logger.debug("  - Take Focus: " + params.getTakeFocus());

        // Prüft, ob eine bestimmte Textstelle ausgewählt werden soll.
        Range selection = params.getSelection();
        if (selection != null) {
            logger.debug("  - Selection: Line {}, Char {}",
                    selection.getStart().getLine(),
                    selection.getStart().getCharacter());
        }

        // In einem echten Client würde hier der Code stehen, um das Dokument
        // in einem Editor-Fenster zu öffnen und den Fokus zu setzen.

        // Da dies ein einfacher Konsolen-Client ist, simulieren wir den Erfolg.
        // Wir geben 'true' zurück, um dem Server zu signalisieren, dass die Anfrage
        // (simuliert) erfolgreich war.
        ShowDocumentResult result = new ShowDocumentResult(true);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void telemetryEvent(Object object) {
        // Loggt das vom Server gesendete Telemetrie-Ereignis.
        // Das 'object' kann eine beliebige JSON-serialisierbare Struktur sein.
        // Für eine einfache Ausgabe verwenden wir die toString()-Methode.
        logger.debug("SERVER TELEMETRY EVENT: " + (object != null ? object.toString() : "null"));
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        // Loggt die Anfrage des Servers nach den Workspace-Ordnern.
        logger.debug("SERVER REQUEST: Get workspace folders");

        // Für diesen Test-Client simulieren wir einen einzigen Workspace-Ordner,
        // der auf das aktuelle Arbeitsverzeichnis des Java-Prozesses zeigt.
        String rootPath = System.getProperty("user.dir");
        String rootUri = java.nio.file.Paths.get(rootPath).toUri().toString();

        WorkspaceFolder folder = new WorkspaceFolder(rootUri, "Test-Workspace");
        logger.debug("  -> Responding with workspace folder: " + folder.getUri());

        // Gibt die Liste mit dem einen Ordner in einem abgeschlossenen Future zurück.
        // In einer echten Anwendung könnte diese Liste mehrere Ordner enthalten.
        return CompletableFuture.completedFuture(java.util.Collections.singletonList(folder));
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        // Loggt die Anfrage des Servers nach Konfigurationseinstellungen.
        logger.debug("SERVER REQUEST: Get configuration for " + configurationParams.getItems().size() + " items.");

        // Erstellt eine Ergebnisliste. Für diesen einfachen Client geben wir für jede
        // angefragte Einstellung 'null' zurück. Das signalisiert dem Server, dass er
        // seine internen Standardwerte verwenden soll.
        List<Object> results = new java.util.ArrayList<>();
        for (ConfigurationItem item : configurationParams.getItems()) {
            logger.debug("  - Requested section: " + (item.getSection() != null ? item.getSection() : "[global]"));
            // Fügt 'null' für jede Anfrage hinzu, da wir keine Konfiguration haben.
            results.add(null);
        }

        logger.debug("  -> Responding with 'null' for all configuration requests.");

        // Gibt die Liste der (leeren) Konfigurationen zurück. Die Anzahl der Elemente
        // in der Liste muss der Anzahl der angefragten Elemente entsprechen.
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        // Loggt die Anfrage des Servers, eine Bearbeitung im Workspace anzuwenden.
        logger.debug("SERVER REQUEST: Apply workspace edit" +
                (params.getLabel() != null ? " '" + params.getLabel() + "'" : ""));

        WorkspaceEdit edit = params.getEdit();

        // In einem echten Client würde man hier die Änderungen auf die
        // tatsächlichen Dokumente anwenden.
        // Für diesen Test-Client loggen wir nur die vorgeschlagenen Änderungen.

        if (edit.getChanges() != null) {
            for (var entry : edit.getChanges().entrySet()) {
                logger.debug("  -> Proposing {} text changes for file: {}",
                        entry.getValue().size(), entry.getKey());
            }
        }

        if (edit.getDocumentChanges() != null) {
            logger.debug("  -> Proposing {} document changes (e.g., creations, renames, deletes).",
                    edit.getDocumentChanges().size());
        }

        // Wir simulieren eine erfolgreiche Anwendung der Änderungen.
        logger.debug("  -> Simulating successful application of the edit.");
        ApplyWorkspaceEditResponse response = new ApplyWorkspaceEditResponse(true);

        // Gibt das Ergebnis an den Server zurück.
        return CompletableFuture.completedFuture(response);
    }
}