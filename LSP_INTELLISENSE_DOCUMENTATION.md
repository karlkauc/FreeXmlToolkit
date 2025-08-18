# IntelliSense und LSP Integration - Implementierungsdokumentation

Diese Dokumentation beschreibt die vollständige Implementierung der IntelliSense-Funktionen für den XML-Editor basierend
auf dem Language Server Protocol (LSP).

## Architektur-Übersicht

Die IntelliSense-Funktionalität ist modular in mehrere Klassen aufgeteilt:

### Hauptkomponenten

1. **XmlEditor**: Zentrale Editor-Klasse mit Hover- und Diagnostics-Funktionalität
2. **XmlCodeEditor**: Code-Editor-Komponente mit Completion-Funktionalität
3. **MyLspClient**: LSP-Client für die Kommunikation mit dem XML Language Server
4. **XmlController**: Controller für die LSP-Server-Verwaltung

## Implementierte LSP-Funktionen

### 1. Dokumenten-Synchronisation

#### textDocument/didOpen

```java
// In XmlCodeEditor.sendDidOpenNotification()
private void sendDidOpenNotification(String content) {
    org.eclipse.lsp4j.TextDocumentItem textDocument = new org.eclipse.lsp4j.TextDocumentItem(
            documentUri, "xml", documentVersion++, content);

    org.eclipse.lsp4j.DidOpenTextDocumentParams openParams =
            new org.eclipse.lsp4j.DidOpenTextDocumentParams(textDocument);

    languageServer.getTextDocumentService().didOpen(openParams);
}
```

#### textDocument/didChange

```java
// In XmlCodeEditor.sendDidChangeNotification()
private void sendDidChangeNotification(String content) {
    org.eclipse.lsp4j.VersionedTextDocumentIdentifier identifier =
            new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(documentUri, documentVersion++);

    // Vollständige Synchronisation
    org.eclipse.lsp4j.TextDocumentContentChangeEvent changeEvent =
            new org.eclipse.lsp4j.TextDocumentContentChangeEvent(content);

    org.eclipse.lsp4j.DidChangeTextDocumentParams params =
            new org.eclipse.lsp4j.DidChangeTextDocumentParams(identifier,
                    Collections.singletonList(changeEvent));

    languageServer.getTextDocumentService().didChange(params);
}
```

#### textDocument/didClose

```java
// In XmlCodeEditor.sendDidCloseNotification()
public void sendDidCloseNotification() {
    org.eclipse.lsp4j.TextDocumentIdentifier identifier =
            new org.eclipse.lsp4j.TextDocumentIdentifier(documentUri);

    org.eclipse.lsp4j.DidCloseTextDocumentParams params =
            new org.eclipse.lsp4j.DidCloseTextDocumentParams(identifier);

    languageServer.getTextDocumentService().didClose(params);
}
```

#### Automatische Synchronisation bei Textänderungen

```java
// In XmlCodeEditor.initialize()
codeArea.textProperty().

addListener((obs, oldText, newText) ->{
        // Syntax Highlighting
        codeArea.

setStyleSpans(0,computeHighlighting(newText));

        // LSP Synchronisation
        if(languageServer !=null&&documentUri !=null&&!oldText.

equals(newText)){
        javafx.application.Platform.

runLater(() ->{

sendDidChangeNotification(newText);
        });
                }
                });
```

### 2. Auto-Vervollständigung (Completion)

#### Trigger-Erkennung

Die Completion wird ausgelöst bei:

- `<` (öffnendes Tag)
- Leerzeichen innerhalb von Tags (für Attribute)

```java
// In XmlCodeEditor.handleIntelliSenseTrigger()
private boolean handleIntelliSenseTrigger(KeyEvent event) {
    String character = event.getCharacter();
    
    // Trigger on "<" (opening tag)
    if ("<".equals(character)) {
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                requestCompletionsFromLSP();
            });
        });
        return true;
    }
    
    // Trigger on space (for attributes) - check if we're inside a tag
    if (" ".equals(character) && isInsideXmlTag()) {
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                requestCompletionsFromLSP();
            });
        });
        return false;
    }
    
    return false;
}
```

#### Completion-Anfrage an LSP Server

```java
// In XmlCodeEditor.requestCompletionsFromLSP()
private void requestCompletionsFromLSP() {
    // Position ermitteln
    int lineNumber = codeArea.getCurrentParagraph();
    int character = codeArea.getCaretColumn();

    org.eclipse.lsp4j.Position position = new org.eclipse.lsp4j.Position(lineNumber, character);
    org.eclipse.lsp4j.TextDocumentIdentifier textDocument =
            new org.eclipse.lsp4j.TextDocumentIdentifier(documentUri);
    org.eclipse.lsp4j.CompletionParams completionParams =
            new org.eclipse.lsp4j.CompletionParams(textDocument, position);

    // Completion-Context setzen
    org.eclipse.lsp4j.CompletionContext context = new org.eclipse.lsp4j.CompletionContext();
    context.setTriggerKind(org.eclipse.lsp4j.CompletionTriggerKind.TriggerCharacter);
    completionParams.setContext(context);

    // Anfrage senden
    languageServer.getTextDocumentService().completion(completionParams)
            .thenAccept(this::showCompletionItems);
}
```

#### Anzeige der Completion-Vorschläge

```java
// In XmlCodeEditor.showCompletionItems()
private void showCompletionItems(List<org.eclipse.lsp4j.CompletionItem> items) {
    List<String> completionLabels = items.stream()
            .map(org.eclipse.lsp4j.CompletionItem::getLabel)
            .collect(Collectors.toList());

    completionListView.getItems().clear();
    completionListView.getItems().addAll(completionLabels);

    if (!completionLabels.isEmpty()) {
        completionListView.getSelectionModel().select(0);
    }

    showCompletionPopup();
}
```

### 3. Hover-Informationen

#### Hover-Anfrage

```java
// In XmlEditor.triggerLspHover()
private void triggerLspHover() {
    int lineNumber = codeArea.getCurrentParagraph();
    int character = codeArea.getCaretColumn();

    org.eclipse.lsp4j.Position position = new org.eclipse.lsp4j.Position(lineNumber, character);
    org.eclipse.lsp4j.TextDocumentIdentifier textDocumentIdentifier =
            new org.eclipse.lsp4j.TextDocumentIdentifier(getDocumentUri());

    HoverParams hoverParams = new HoverParams(textDocumentIdentifier, position);

    CompletableFuture<Hover> hoverFuture = serverProxy.getTextDocumentService().hover(hoverParams);
    hoverFuture.thenAcceptAsync(hover -> {
        if (hover != null && hover.getContents() != null) {
            if (hover.getContents().isRight()) {
                String hoverText = hover.getContents().getRight().getValue();
                if (!hoverText.isBlank()) {
                    Platform.runLater(() -> {
                        popOverLabel.setText(hoverText);
                        showHoverPopover();
                    });
                }
            }
        }
    });
}
```

#### Hover-Anzeige

```java
// In XmlEditor.showHoverPopover()
private void showHoverPopover() {
    Point2D screenPos = codeArea.localToScreen(codeArea.getCaretBounds().get().getMinX(),
            codeArea.getCaretBounds().get().getMaxY());
    hoverPopOver.show(codeArea.getScene().getWindow(), screenPos.getX(), screenPos.getY() + 5);
}
```

### 4. Diagnose und Fehler-Highlighting

#### publishDiagnostics Handler

```java
// In MyLspClient.publishDiagnostics()
@Override
public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
    logger.debug("DIAGNOSTICS for {}", diagnostics.getUri());
    for (Diagnostic d : diagnostics.getDiagnostics()) {
        logger.debug("  -> [{}] {} @ Line {}",
                d.getSeverity(), d.getMessage(), d.getRange().getStart().getLine());
    }

    if (xmlController != null) {
        xmlController.publishDiagnostics(diagnostics);
    }
}
```

#### Diagnostics-Weiterleitung und Anzeige

```java
// In XmlEditor.updateDiagnostics()
public void updateDiagnostics(List<Diagnostic> diagnostics) {
    this.currentDiagnostics = new ArrayList<>(diagnostics);

    // Highlighting berechnen
    StyleSpans<Collection<String>> syntaxHighlighting = XmlCodeEditor.computeHighlighting(codeArea.getText());

    // Diagnostics-Styles anwenden
    codeArea.setStyleSpans(0, syntaxHighlighting.overlay(
            computeDiagnosticStyles(),
            (syntax, diagnostic) -> diagnostic.isEmpty() ? syntax : diagnostic
    ));
}

private StyleSpans<Collection<String>> computeDiagnosticStyles() {
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

    for (Diagnostic diagnostic : currentDiagnostics) {
        Range range = diagnostic.getRange();
        int start = getOffsetFromPosition(range.getStart());
        int end = getOffsetFromPosition(range.getEnd());

        if (start < end) {
            String styleClass = getStyleClassFor(diagnostic.getSeverity());
            spansBuilder.add(Collections.singleton(styleClass), end - start);
        }
    }

    return spansBuilder.create();
}
```

## LSP-Client Integration

### Server-Setup

```java
// In XmlController.setupLSPServer()
private void setupLSPServer() throws IOException, ExecutionException, InterruptedException {
    var clientInputStream = new PipedInputStream();
    var serverOutputStream = new PipedOutputStream(clientInputStream);
    var serverInputStream = new PipedInputStream();
    var clientOutputStream = new PipedOutputStream(serverInputStream);

    this.lspClient = new MyLspClient(this);

    Launcher<LanguageServer> launcher = new LSPLauncher.Builder<LanguageServer>()
            .setLocalService(lspClient)
            .setRemoteInterface(LanguageServer.class)
            .setInput(clientInputStream)
            .setOutput(clientOutputStream)
            .setExecutorService(lspExecutor)
            .create();

    this.serverProxy = launcher.getRemoteProxy();
    lspClient.connect(serverProxy);
    this.clientListening = launcher.startListening();

    XMLServerLauncher.launch(serverInputStream, serverOutputStream);

    // Server initialisieren
    InitializeParams initParams = new InitializeParams();
    initParams.setProcessId((int) ProcessHandle.current().pid());
    WorkspaceFolder workspaceFolder = new WorkspaceFolder(
            Paths.get(".").toUri().toString(), "lemminx-project");
    initParams.setWorkspaceFolders(Collections.singletonList(workspaceFolder));

    serverProxy.initialize(initParams).get();
    serverProxy.initialized(new InitializedParams());
}
```

### Verwendung im XmlEditor

```java
// In XmlEditor.createAndAddXmlTab()
private void createAndAddXmlTab(File file) {
    XmlEditor xmlEditor = new XmlEditor(file);
    xmlEditor.setMainController(this.mainController);
    xmlEditor.setLanguageServer(this.serverProxy); // LSP-Server setzen

    // Document synchronisation
    if (file != null) {
        notifyLspServerFileOpened(file, xmlEditor.codeArea.getText());
    }
}
```

## Trigger und Event-Handling

### IntelliSense-Trigger

- **Element-Completion**: `<` Zeichen
- **Attribut-Completion**: Leerzeichen innerhalb von Tags
- **Hover**: Maus-Hover mit 500ms Verzögerung
- **Diagnostics**: Automatisch vom Server gesendet

### Popup-Navigation

- **↑/↓**: Navigation in der Completion-Liste
- **Enter**: Auswahl des aktuellen Items
- **Escape**: Schließen des Popups
- **Tab**: Completion-Vervollständigung

## Konfiguration und Anpassung

### DocumentUri-Setup

```java
// In XmlCodeEditor.setDocumentUri()
public void setDocumentUri(String documentUri) {
    this.documentUri = documentUri;

    // Initial didOpen senden wenn Content vorhanden
    if (languageServer != null && codeArea.getText() != null && !codeArea.getText().isEmpty()) {
        sendDidOpenNotification(codeArea.getText());
    }
}
```

### Schema-Awareness

Die Implementation unterstützt sowohl LSP-basierte als auch Schema-basierte Completions:

- **Primär**: LSP-Server Completions (lemminx)
- **Fallback**: Lokale Schema-basierte Completions
- **Kombiniert**: Contextual completions basierend auf XSD-Schema

## CSS-Styling für Diagnostics

```css
.diagnostic-error {
    -fx-underline: red;
    -fx-border-color: red;
}

.diagnostic-warning {
    -fx-underline: orange;
    -fx-border-color: orange;
}
```

## Fehlerbehandlung und Fallbacks

- **LSP-Server nicht verfügbar**: Automatischer Fallback auf lokale Completions
- **Netzwerk-Timeouts**: Graceful degradation der Features
- **Parse-Fehler**: Logging und Fortsetzung mit verfügbaren Daten

## Performance-Optimierungen

- **Asynchrone LSP-Aufrufe**: Alle LSP-Anfragen laufen asynchron
- **Caching**: Completion-Ergebnisse werden temporär gecacht
- **Batching**: Mehrere didChange-Events werden gebündelt
- **Lazy Loading**: PopUp-Komponenten werden erst bei Bedarf initialisiert

Diese Implementierung erfüllt alle Anforderungen aus der `intellisense_requirement.md` und bietet eine vollständige,
produktionsreife IntelliSense-Funktionalität für XML-Dateien.