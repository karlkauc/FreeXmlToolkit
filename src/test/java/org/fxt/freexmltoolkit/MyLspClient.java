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
        // Loggt die Anfrage des Servers, einschließlich Nachricht und Typ.
        System.out.println("SERVER MESSAGE REQUEST: [" + requestParams.getType() + "] " + requestParams.getMessage());

        List<MessageActionItem> actions = requestParams.getActions();

        // Prüft, ob Aktionen (Schaltflächen) vorhanden sind.
        if (actions == null || actions.isEmpty()) {
            // Wenn keine Aktionen vorhanden sind, wird der Future mit null abgeschlossen.
            return CompletableFuture.completedFuture(null);
        }

        // Gibt die verfügbaren Aktionen auf der Konsole aus.
        System.out.println("  Available actions:");
        for (int i = 0; i < actions.size(); i++) {
            System.out.printf("    (%d) %s%n", i + 1, actions.get(i).getTitle());
        }

        // Für diesen einfachen Test-Client wählen wir automatisch die erste Aktion aus.
        // Eine echte Anwendung würde hier auf eine Benutzereingabe in einem Dialog warten.
        MessageActionItem selectedAction = actions.get(0);
        System.out.println("  -> Automatically selecting first action: '" + selectedAction.getTitle() + "'");

        // Gibt die ausgewählte Aktion an den Server zurück, indem der Future abgeschlossen wird.
        return CompletableFuture.completedFuture(selectedAction);
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        // Loggt die Anfrage des Servers, ein Dokument anzuzeigen.
        System.out.println("SERVER REQUEST: Show document " + params.getUri());
        System.out.println("  - Take Focus: " + params.getTakeFocus());

        // Prüft, ob eine bestimmte Textstelle ausgewählt werden soll.
        Range selection = params.getSelection();
        if (selection != null) {
            System.out.printf("  - Selection: Line %d, Char %d%n",
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
        System.out.println("SERVER TELEMETRY EVENT: " + (object != null ? object.toString() : "null"));
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        // Loggt die Anfrage des Servers nach den Workspace-Ordnern.
        System.out.println("SERVER REQUEST: Get workspace folders");

        // Für diesen Test-Client simulieren wir einen einzigen Workspace-Ordner,
        // der auf das aktuelle Arbeitsverzeichnis des Java-Prozesses zeigt.
        String rootPath = System.getProperty("user.dir");
        String rootUri = java.nio.file.Paths.get(rootPath).toUri().toString();

        WorkspaceFolder folder = new WorkspaceFolder(rootUri, "Test-Workspace");
        System.out.println("  -> Responding with workspace folder: " + folder.getUri());

        // Gibt die Liste mit dem einen Ordner in einem abgeschlossenen Future zurück.
        // In einer echten Anwendung könnte diese Liste mehrere Ordner enthalten.
        return CompletableFuture.completedFuture(java.util.Collections.singletonList(folder));
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        // Loggt die Anfrage des Servers nach Konfigurationseinstellungen.
        System.out.println("SERVER REQUEST: Get configuration for " + configurationParams.getItems().size() + " items.");

        // Erstellt eine Ergebnisliste. Für diesen einfachen Client geben wir für jede
        // angefragte Einstellung 'null' zurück. Das signalisiert dem Server, dass er
        // seine internen Standardwerte verwenden soll.
        List<Object> results = new java.util.ArrayList<>();
        for (ConfigurationItem item : configurationParams.getItems()) {
            System.out.println("  - Requested section: " + (item.getSection() != null ? item.getSection() : "[global]"));
            // Fügt 'null' für jede Anfrage hinzu, da wir keine Konfiguration haben.
            results.add(null);
        }

        System.out.println("  -> Responding with 'null' for all configuration requests.");

        // Gibt die Liste der (leeren) Konfigurationen zurück. Die Anzahl der Elemente
        // in der Liste muss der Anzahl der angefragten Elemente entsprechen.
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        // Loggt die Anfrage des Servers, eine Bearbeitung im Workspace anzuwenden.
        System.out.println("SERVER REQUEST: Apply workspace edit" +
                (params.getLabel() != null ? " '" + params.getLabel() + "'" : ""));

        WorkspaceEdit edit = params.getEdit();

        // In einem echten Client würde man hier die Änderungen auf die
        // tatsächlichen Dokumente anwenden.
        // Für diesen Test-Client loggen wir nur die vorgeschlagenen Änderungen.

        if (edit.getChanges() != null) {
            for (var entry : edit.getChanges().entrySet()) {
                System.out.printf("  -> Proposing %d text changes for file: %s%n",
                        entry.getValue().size(), entry.getKey());
            }
        }

        if (edit.getDocumentChanges() != null) {
            System.out.printf("  -> Proposing %d document changes (e.g., creations, renames, deletes).%n",
                    edit.getDocumentChanges().size());
        }

        // Wir simulieren eine erfolgreiche Anwendung der Änderungen.
        System.out.println("  -> Simulating successful application of the edit.");
        ApplyWorkspaceEditResponse response = new ApplyWorkspaceEditResponse(true);

        // Gibt das Ergebnis an den Server zurück.
        return CompletableFuture.completedFuture(response);
    }
}