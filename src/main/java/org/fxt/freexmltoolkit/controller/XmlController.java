/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lemminx.XMLServerLauncher;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.MyLspClient;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

public class XmlController {
    private final static Logger logger = LogManager.getLogger(XmlController.class);

    private final static int XML_INDENT = 4;

    CodeArea codeAreaXpath = new CodeArea();
    CodeArea codeAreaXQuery = new CodeArea();

    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    private MainController mainController;

    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, validateSchema, runXpathQuery, minifyButton;

    @FXML
    StackPane stackPaneXPath, stackPaneXQuery;

    @FXML
    ComboBox<File> schemaList = new ComboBox<>();

    FileChooser fileChooser = new FileChooser();

    @FXML
    HBox test;

    @FXML
    Label schemaValidText;

    @FXML
    Tab xPathTab, xQueryTab;

    @FXML
    TabPane xPathQueryPane, xmlFilesPane;

    @FXML
    TextArea textAreaTemp;

    @FXML
    XmlEditor emptyXmlEditor;

    @FXML
    TextField searchField;

    @FXML
    private ProgressIndicator operationProgressBar;

    // --- LSP Server Felder ---
    private LanguageServer serverProxy;
    private MyLspClient lspClient;
    private final ExecutorService lspExecutor = Executors.newSingleThreadExecutor();
    private Future<?> clientListening;
    // NEU: Map zur Speicherung von Diagnosen, hierher verschoben.
    private final Map<String, List<Diagnostic>> diagnosticsMap = new ConcurrentHashMap<>();
    // Executor für asynchrone UI-Tasks wie Formatierung
    private final ExecutorService formattingExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true); // Damit die Anwendung beendet werden kann, auch wenn Tasks laufen
        return t;
    });

    private final Map<String, Integer> documentVersions = new ConcurrentHashMap<>();


    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");
        schemaValidText.setText("");

        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);
        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText))));

        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);
        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText))));

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            XmlEditor currentEditor = getCurrentXmlEditor();
            if (currentEditor != null) {
                // Ruft die neue Suchmethode im aktiven Editor auf
                currentEditor.searchAndHighlight(newVal);
            }
        });

        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                XmlEditor editor = getCurrentXmlEditor();
                if (editor == null) return;

                // Hervorhebung entfernen
                editor.clearHighlight();

                // Leert das Suchfeld
                searchField.clear();

                // Optional: Fokus zurück auf den Editor setzen für nahtloses Weiterarbeiten
                CodeArea currentCodeArea = getCurrentCodeArea();
                if (currentCodeArea != null) {
                    currentCodeArea.requestFocus();
                }
            }
        });


        var t = System.getenv("debug");
        if (t != null) {
            logger.debug("set visible false");
            test.setVisible(true);

            codeAreaXpath.replaceText(0, 0, "/FundsXML4/ControlData");
            codeAreaXQuery.replaceText(0, 0, """
                    for $i in /FundsXML4/Funds/Fund
                                      	where number($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text()) -\s
                                      			sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency]) > 10
                                          return
                                              string-join(
                                                  (
                                                      $i/Names/OfficialName,
                                                      $i/Currency,
                                                      format-number(number($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text()), "###,##0.00"),
                                                      format-number(sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency]), "###,##0.00"),
                                                      format-number(
                                      			number($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text()) -\s
                                      			sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency])
                                      		, "###,##0.00")
                                                  ), ' | '
                                              )
                    """);
        }

        reloadXmlText();

        xmlFilesPane.setOnDragOver(this::handleFileOverEvent);
        xmlFilesPane.setOnDragExited(this::handleDragExitedEvent);
        xmlFilesPane.setOnDragDropped(this::handleFileDroppedEvent);
    }

    private void createAndAddXmlTab(File file) {
        // Die Konstruktoren von XmlEditor müssen angepasst werden, um den MainController zu akzeptieren
        XmlEditor xmlEditor = new XmlEditor(file); // Behalten Sie Ihren Konstruktor bei
        xmlEditor.setMainController(this.mainController); // Übergeben Sie den Controller

        xmlEditor.setLanguageServer(this.serverProxy);
        xmlEditor.refresh();

        // Füge einen Listener hinzu, der den Server über Textänderungen informiert.
        // Dies ist entscheidend für aktuelle Diagnosen und Falt-Bereiche.
        xmlEditor.getXmlCodeEditor().getCodeArea().textProperty().addListener((obs, oldVal, newVal) -> {
            notifyLspServerFileChanged(xmlEditor);
        });

        xmlEditor.setOnSearchRequested(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });

        xmlFilesPane.getTabs().add(xmlEditor);
        xmlFilesPane.getSelectionModel().select(xmlEditor);

        if (file != null) {
            mainController.addFileToRecentFiles(file);
            notifyLspServerFileOpened(file, xmlEditor.codeArea.getText());
        }
    }

    @FXML
    void handleFileOverEvent(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!xmlFilesPane.getStyleClass().contains("xmlPaneFileDragDrop-active")) {
                xmlFilesPane.getStyleClass().add("xmlPaneFileDragDrop-active");
            }
        } else {
            event.consume();
        }
    }

    @FXML
    void handleDragExitedEvent(DragEvent event) {
        xmlFilesPane.getStyleClass().clear();
        xmlFilesPane.getStyleClass().add("tab-pane");
    }

    @FXML
    void handleFileDroppedEvent(DragEvent event) {
        Dragboard db = event.getDragboard();

        for (File f : db.getFiles()) {
            logger.debug("add File: '{}': {}", f.getName(), f.getAbsolutePath());
            createAndAddXmlTab(f);
        }
    }

    /**
     * Die Methode ist jetzt public und wurde refaktorisiert, um die
     * zentrale `createAndAddXmlTab`-Methode zu verwenden. Das vermeidet Code-Duplizierung.
     *
     * @param f Die zu ladende Datei.
     */
    public void loadFile(File f) {
        logger.debug("Loading file {} via createAndAddXmlTab", f.getAbsolutePath());
        createAndAddXmlTab(f);
    }

    @FXML
    private void newFilePressed() {
        logger.debug("New File Pressed");
        // Verwendung der zentralen Methode zum Erstellen von Tabs
        createAndAddXmlTab(null);
    }

    private XmlEditor getCurrentXmlEditor() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        return (XmlEditor) active;
    }

    // Die Methoden für die Schriftgröße können entfernt werden, da die Steuerung
    // jetzt direkt im XmlCodeEditor über Tastenkürzel und Mausrad erfolgt.
    // Falls du sie für Toolbar-Buttons behalten möchtest, kannst du sie so implementieren:
    @FXML
    private void increaseFontSize() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().increaseFontSize();
        }
    }

    @FXML
    private void decreaseFontSize() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().decreaseFontSize();
        }
    }

    // Die `getCurrentCodeArea()` Methode muss angepasst werden, um die neue Struktur zu finden.
    private CodeArea getCurrentCodeArea() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null && editor.getXmlCodeEditor() != null) {
            // Greift über den XmlCodeEditor auf die CodeArea zu.
            return editor.getXmlCodeEditor().getCodeArea();
        }
        return null;
    }

    @FXML
    private void runXpathQueryPressed() {
        var currentCodeArea = getCurrentCodeArea();

        if (currentCodeArea != null && currentCodeArea.getText() != null) {
            String xml = currentCodeArea.getText();
            Tab selectedItem = xPathQueryPane.getSelectionModel().getSelectedItem();
            String query = ((CodeArea) ((VirtualizedScrollPane<?>) ((StackPane) selectedItem.getContent()).getChildren().getFirst()).getContent()).getText();
            String result = "";

            logger.debug("QUERY: {}", query);

            switch (selectedItem.getId()) {
                case "xQueryTab" ->
                        result = String.join(System.lineSeparator(), getCurrentXmlEditor().getXmlService().getXQueryResult(query));
                case "xPathTab" -> result = getCurrentXmlEditor().getXmlService().getXmlFromXpath(xml, query);
            }

            if (result != null && !result.isEmpty()) {
                logger.debug(result);
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, result);
            }
        }
    }

    public void setParentController(MainController parentController) {
        logger.debug("XML Controller - set parent controller");
        this.mainController = parentController;
        try {
            setupLSPServer();
        } catch (Exception e) {
            logger.error("Failed to start LSP Server from XmlController", e);
        }
    }

    private void setupLSPServer() throws IOException, ExecutionException, InterruptedException {
        var clientInputStream = new PipedInputStream();
        var serverOutputStream = new PipedOutputStream(clientInputStream);
        var serverInputStream = new PipedInputStream();
        var clientOutputStream = new PipedOutputStream(serverInputStream);

        // GEÄNDERT: Übergibt 'this' (den XmlController) an den Client.
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
        logger.debug("🚀 Server und Client gestartet und verbunden (Owner: XmlController).");

        InitializeParams initParams = new InitializeParams();
        initParams.setProcessId((int) ProcessHandle.current().pid());
        WorkspaceFolder workspaceFolder = new WorkspaceFolder(Paths.get(".").toUri().toString(), "lemminx-project");
        initParams.setWorkspaceFolders(Collections.singletonList(workspaceFolder));

        logger.debug("🤝 Sende 'initialize' Anfrage...");
        serverProxy.initialize(initParams).get();
        serverProxy.initialized(new InitializedParams());
        logger.debug("...Initialisierung abgeschlossen.");
    }

    /**
     * Wird vom MyLspClient aufgerufen. Speichert die Diagnosen
     * und stößt die UI-Aktualisierung an.
     */
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        logger.debug("bin im publishDiagnostics");
        String uri = diagnostics.getUri();
        diagnosticsMap.put(uri, diagnostics.getDiagnostics());

        Platform.runLater(() -> {
            findEditorByUri(uri).ifPresent(editor ->
                    editor.updateDiagnostics(diagnostics.getDiagnostics())
            );
        });
    }

    /**
     * Hilfsmethode, um den geöffneten XmlEditor für einen URI zu finden.
     */
    private Optional<XmlEditor> findEditorByUri(String uri) {
        for (Tab tab : xmlFilesPane.getTabs()) {
            if (tab instanceof XmlEditor editor) {
                if (editor.getXmlFile() != null && editor.getXmlFile().toURI().toString().equals(uri)) {
                    return Optional.of(editor);
                }
            }
        }
        return Optional.empty();
    }

    private void notifyLspServerFileOpened(File file, String content) {
        if (this.serverProxy == null) {
            logger.warn("LSP Server not available. Cannot send 'didOpen' notification.");
            return;
        }

        try {
            // 1. Erstelle ein TextDocumentItem mit allen notwendigen Informationen
            TextDocumentItem textDocumentItem = new TextDocumentItem(
                    file.toURI().toString(), // LSP verwendet URIs zur Identifikation
                    "xml",                   // Die Sprach-ID
                    1,                       // Version 1, da es das erste Öffnen ist
                    content                  // Der vollständige Inhalt der Datei
            );

            // 2. Verpacke das Item in die 'didOpen'-Parameter
            DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);

            // 3. Sende die Benachrichtigung an den Text-Service des Servers
            // Dies ist eine asynchrone Benachrichtigung, wir warten nicht auf eine Antwort.
            this.serverProxy.getTextDocumentService().didOpen(params);

            // Initialisiere die Version für dieses Dokument
            documentVersions.put(file.toURI().toString(), 1);

            requestFoldingRanges(getCurrentXmlEditor());

            logger.info("✅ Sent 'didOpen' notification for {} to LSP server.", file.getName());

        } catch (Exception e) {
            logger.error("Failed to send 'didOpen' notification to LSP server.", e);
        }
    }

    public void displayFileContent(File file) {
        if (file != null && file.exists()) {
            try {
                // Lese den Inhalt der Datei
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

                if (mainController != null) {
                    mainController.addFileToRecentFiles(file);
                }

                // Setze den Inhalt in die CodeArea
                var area = getCurrentCodeArea();
                if (area != null) {
                    area.replaceText(content);
                    logger.debug("File {} displayed.", file.getName());

                    // Benachrichtige den LSP-Server, dass die Datei geöffnet wurde.
                    notifyLspServerFileOpened(file, content);
                }

            } catch (IOException e) {
                logger.error("Could not read file {}", file.getAbsolutePath(), e);
            }
        }
    }

    @FXML
    public void reloadXmlText() {
        try {
            XmlEditor xmlEditor = getCurrentXmlEditor();
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().exists()) {
                xmlEditor.refresh();

                if (xmlEditor.getXmlFile() != null) {
                    textAreaTemp.setText(xmlEditor.getXmlFile().getName());
                    // Hole die CodeArea sicher. Der direkte Aufruf von .getText() auf das Ergebnis von
                    // getCurrentCodeArea() ist unsicher, da die Methode null zurückgeben kann.
                    CodeArea currentCodeArea = getCurrentCodeArea();
                    if (currentCodeArea != null) {
                        notifyLspServerFileOpened(xmlEditor.getXmlFile(), currentCodeArea.getText());
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Wird vom "Save File"-Button in der FXML-Datei aufgerufen.
     * Diese Methode orchestriert den gesamten Speicherprozess: Validierung, Benutzerbestätigung und das eigentliche Speichern.
     */
    @FXML
    private void saveFile() {
        XmlEditor currentEditor = getCurrentXmlEditor();
        CodeArea currentCodeArea = getCurrentCodeArea();

        // 1. Sicherstellen, dass ein Editor aktiv ist
        if (currentEditor == null || currentCodeArea == null) {
            logger.warn("Speicheraktion ausgelöst, aber kein aktiver Editor gefunden.");
            return;
        }

        // 2. Inhalt validieren. Der XmlService prüft auf Wohlgeformtheit
        //    und, falls ein Schema vorhanden ist, auf Schemavalidität.
        String contentToValidate = currentCodeArea.getText();
        XmlService service = currentEditor.getXmlService();

        // Schema-Datei für die Validierung bestimmen.
        // Priorität 1: Die explizite Auswahl des Benutzers in der ComboBox.
        File schemaToUse = schemaList.getValue();

        // Priorität 2: Wenn nichts ausgewählt ist, das vom Service erkannte Schema verwenden.
        // Dies ist nützlich, wenn ein Schema automatisch aus der XML-Datei geladen wurde.
        if (schemaToUse == null) {
            schemaToUse = service.getCurrentXsdFile();
        }

        // 2. Inhalt validieren.
        // Die Methode `validateText(String, File)` prüft auf Wohlgeformtheit, wenn das Schema null ist,
        // und zusätzlich auf Schemavalidität, wenn ein Schema angegeben ist.
        List<SAXParseException> errors = service.validateText(contentToValidate, schemaToUse);

        // 3. Wenn Fehler gefunden wurden, den Benutzer um Bestätigung bitten.
        if (errors != null && !errors.isEmpty()) {
            Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationDialog.setTitle("Validierungsfehler");
            confirmationDialog.setHeaderText(errors.size() + " Validierungsfehler gefunden.");
            confirmationDialog.setContentText("Das XML ist nicht wohlgeformt oder nicht schemakonform.\n\nWirklich speichern?");

            Optional<ButtonType> result = confirmationDialog.showAndWait();
            // Aktion abbrechen, wenn der Benutzer nicht "OK" klickt.
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        // 4. Mit dem Speichern fortfahren.
        saveTextToFile(currentEditor);
    }

    /**
     * Eine Hilfsmethode, die die eigentliche Datei-I/O-Logik kapselt.
     * Sie unterscheidet zwischen dem Speichern einer neuen und einer bestehenden Datei.
     *
     * @param editor Der XmlEditor, dessen Inhalt gespeichert werden soll.
     */
    private void saveTextToFile(XmlEditor editor) {
        File targetFile = editor.getXmlFile();
        String content = editor.getXmlCodeEditor().getCodeArea().getText();

        // Fall 1: Es ist ein neues, ungespeichertes Dokument. "Speichern unter"-Dialog anzeigen.
        if (targetFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("XML-Datei speichern");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML-Dateien (*.xml)", "*.xml"));

            // Das zuletzt verwendete Verzeichnis für eine bessere Benutzererfahrung voreinstellen.
            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }

            File selectedFile = fileChooser.showSaveDialog(xmlFilesPane.getScene().getWindow());

            if (selectedFile == null) {
                return; // Benutzer hat den Dialog abgebrochen.
            }

            // Den Editor mit den Informationen der neuen Datei aktualisieren.
            targetFile = selectedFile;
            editor.setXmlFile(targetFile);
            editor.getXmlService().setCurrentXmlFile(targetFile);
            mainController.addFileToRecentFiles(targetFile);

            // Das neue Verzeichnis für das nächste Mal speichern.
            if (targetFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(targetFile.getParentFile().getAbsolutePath());
            }
        }

        // Fall 2: Datei schreiben (entweder die bestehende oder die neu ausgewählte).
        try {
            Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
            logger.info("Datei erfolgreich gespeichert: {}", targetFile.getAbsolutePath());
            schemaValidText.setText("Datei '" + targetFile.getName() + "' gespeichert (" + targetFile.length() + " Bytes).");
        } catch (IOException e) {
            logger.error("Fehler beim Schreiben der Datei: {}", targetFile.getAbsolutePath(), e);
            new Alert(Alert.AlertType.ERROR, "Konnte Datei nicht speichern:\n" + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void minifyXmlText() {
        final CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea == null) return;
        final String xml = currentCodeArea.getText();
        if (xml == null || xml.isBlank()) return;

        // Die Operation wird in einen Hintergrund-Thread ausgelagert, um die UI nicht zu blockieren.
        minifyButton.setDisable(true);
        operationProgressBar.setVisible(true);

        Task<String> minifyTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Ruft die neue, deutlich schnellere StAX-basierte Methode auf.
                return XmlService.convertXmlToOneLineFast(xml);
            }
        };

        minifyTask.setOnSucceeded(event -> {
            String minifiedString = minifyTask.getValue();
            if (minifiedString != null && !minifiedString.isEmpty()) {
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, minifiedString);
            }
            minifyButton.setDisable(false);
            operationProgressBar.setVisible(false);
        });

        minifyTask.setOnFailed(event -> {
            logger.error("Failed to minify XML", minifyTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not minify XML: " + minifyTask.getException().getMessage()).showAndWait();
            minifyButton.setDisable(false);
            operationProgressBar.setVisible(false);
        });

        formattingExecutor.submit(minifyTask);
    }

    @FXML
    private void prettifyingXmlText() {
        final CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea == null) return;
        final String text = currentCodeArea.getText();
        if (text == null || text.isBlank()) return;

        prettyPrint.setDisable(true);

        Task<String> formatTask = new Task<>() {
            @Override
            protected String call() {
                return XmlService.prettyFormat(text, XML_INDENT);
            }
        };

        formatTask.setOnSucceeded(event -> {
            String prettyString = formatTask.getValue();
            if (prettyString != null && !prettyString.isEmpty()) {
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, prettyString);
            }
            prettyPrint.setDisable(false);
        });

        formatTask.setOnFailed(event -> {
            logger.error("Failed to pretty-print XML", formatTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not format XML: " + formatTask.getException().getMessage()).showAndWait();
            prettyPrint.setDisable(false);
        });

        formattingExecutor.submit(formatTask);
    }

    @FXML
    private void validateSchema() {
        logger.debug("Validate Schema");
        XmlEditor currentEditor = getCurrentXmlEditor();
        if (currentEditor == null) {
            return; // No active editor, nothing to do.
        }
        currentEditor.getXmlService().loadSchemaFromXMLFile(); // schaut, ob er schema selber finden kann
        String xsdLocation = currentEditor.getXmlService().getRemoteXsdLocation();
        logger.debug("XSD Location: {}", xsdLocation);

        if (xsdLocation != null) {
            currentEditor.getXmlService().loadSchemaFromXMLFile();
            logger.debug("Schema loaded: {}", xsdLocation);

            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null && currentCodeArea.getText().length() > 1) {
                var errors = currentEditor.getXmlService().validateText(currentCodeArea.getText());
                if (errors != null && !errors.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);

                    alert.getDialogPane().setMaxHeight(Region.USE_PREF_SIZE);

                    alert.setTitle(errors.size() + " validation Errors");
                    StringBuilder temp = new StringBuilder();
                    for (SAXParseException error : errors) {
                        logger.debug("Append Error: {}", error.getMessage());
                        temp.append(error.getMessage()).append(System.lineSeparator());
                    }

                    schemaValidText.setText(LocalDateTime.now() + "... Schema not valid!");

                    alert.setContentText(temp.toString());
                    alert.showAndWait();
                } else {
                    schemaValidText.setText(LocalDateTime.now() + "... Schema Valid!");
                }
            }
        }
    }

    @FXML
    private void moveUp() {
        logger.debug("Moving caret and scrollbar to the beginning.");
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().moveUp();
        }
    }

    @FXML
    private void moveDown() {
        logger.debug("Moving caret and scrollbar to the end.");
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().moveDown();
        }
    }

    @FXML
    private void openFile() {
        // Lese das zuletzt geöffnete Verzeichnis aus dem PropertiesService
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        // Stelle sicher, dass nur der XML-Filter aktiv ist
        fileChooser.getExtensionFilters().clear();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());

            // Speichere das neue Verzeichnis im PropertiesService
            if (selectedFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(selectedFile.getParentFile().getAbsolutePath());
            }

            XmlEditor xmlEditor = new XmlEditor(selectedFile);

            // NEU: Füge einen Listener hinzu, der den Server über Textänderungen informiert.
            // Dies ist entscheidend für aktuelle Diagnosen und Falt-Bereiche.
            xmlEditor.getXmlCodeEditor().getCodeArea().textProperty().addListener((obs, oldVal, newVal) -> {
                notifyLspServerFileChanged(xmlEditor);
            });


            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);

            xmlEditor.refresh();
            // Die Anforderung für Falt-Bereiche darf erst erfolgen,
            // nachdem der Server über das Öffnen der Datei informiert wurde.
            // Die Methode notifyLspServerFileOpened übernimmt diese Aufgabe.
            CodeArea codeArea = xmlEditor.getXmlCodeEditor().getCodeArea();
            if (codeArea != null) {
                notifyLspServerFileOpened(selectedFile, codeArea.getText());
            }
            // Fokus auf den neuen Editor setzen, nachdem die UI-Änderungen verarbeitet wurden.
            Platform.runLater(() -> {
                // 1. Zuerst den Fokus auf den Tab-Container legen.
                xmlFilesPane.requestFocus();

                // 2. Dann den Fokus an die CodeArea im Inneren übergeben.
                if (xmlEditor.getXmlCodeEditor().getCodeArea() != null) {
                    xmlEditor.getXmlCodeEditor().getCodeArea().requestFocus();
                    xmlEditor.getXmlCodeEditor().moveUp(); // Setzt den Cursor an den Anfang.
                }
            });

            xmlEditor.getXmlService().setCurrentXmlFile(selectedFile);
            xmlEditor.setOnSearchRequested(() -> {
                searchField.requestFocus();
                searchField.selectAll();
            });

            if (xmlEditor.getXmlService().loadSchemaFromXMLFile()) {
                schemaList.getItems().add(xmlEditor.getXmlService().getCurrentXsdFile());
            }

            validateSchema();
            mainController.addFileToRecentFiles(selectedFile);
        } else {
            logger.debug("No file selected");
        }
    }

    /**
     * Fährt den Language Server und die zugehörigen Dienste sauber herunter.
     * Diese Methode sollte beim Schließen der Anwendung aufgerufen werden.
     */
    public void shutdown() {
        if (serverProxy == null) {
            logger.info("LSP Server wurde nie gestartet, kein Shutdown notwendig.");
            return;
        }

        logger.info("Beginne mit dem Herunterfahren des LSP-Servers...");
        try {
            // Schritt 1: Sende die 'shutdown'-Anfrage und warte auf die Antwort.
            // Dies signalisiert dem Server, sich vorzubereiten, aber noch nicht zu beenden.
            serverProxy.shutdown().get(5, TimeUnit.SECONDS);
            logger.debug("LSP-Server 'shutdown'-Anfrage erfolgreich gesendet und bestätigt.");

            // Schritt 2: Sende die 'exit'-Benachrichtigung.
            // Dies weist den Server an, sich jetzt zu beenden. Dies ist eine Benachrichtigung, keine Anfrage.
            serverProxy.exit();
            logger.debug("LSP-Server 'exit'-Benachrichtigung gesendet.");

        } catch (Exception e) {
            logger.error("Fehler beim ordnungsgemäßen Herunterfahren des LSP-Servers.", e);
        } finally {
            // Schritt 3: Beende die clientseitigen Ressourcen, unabhängig vom Server-Status.
            if (clientListening != null && !clientListening.isDone()) {
                clientListening.cancel(true); // Stoppt den Listener-Thread.
                logger.debug("LSP-Client-Listener gestoppt.");
            }
            if (!lspExecutor.isShutdown()) {
                lspExecutor.shutdownNow(); // Beendet den Executor-Service.
                logger.debug("LSP-Executor-Service heruntergefahren.");
            }
            if (!formattingExecutor.isShutdown()) {
                formattingExecutor.shutdownNow();
                logger.debug("Formatting-Executor-Service heruntergefahren.");
            }
            logger.info("LSP-Server-Shutdown-Prozess abgeschlossen.");
        }
    }

    @FXML
    private void print() {
        logger.debug("Print button clicked.");
        XmlEditor currentEditor = getCurrentXmlEditor();
        if (currentEditor != null) {
            String documentContent = currentEditor.getDocumentAsString();
            if (documentContent != null) {
                System.out.println("--- Current Document Content ---");
                System.out.println(documentContent);
                System.out.println("------------------------------");
                logger.info("Document content printed to console.");
            } else {
                logger.warn("Current document content is null or could not be serialized.");
            }
        } else {
            logger.warn("No active XML editor found.");
        }
    }

    /**
     * NEU: Sendet eine 'didChange' Benachrichtigung an den LSP-Server, wenn sich der
     * Text in einem Editor ändert. Aktualisiert auch die Falt-Bereiche.
     *
     * @param editor Der Editor, dessen Inhalt sich geändert hat.
     */
    private void notifyLspServerFileChanged(XmlEditor editor) {
        if (serverProxy == null || editor == null || editor.getXmlFile() == null) {
            return;
        }
        File file = editor.getXmlFile();
        String uri = file.toURI().toString();
        String content = editor.getXmlCodeEditor().getCodeArea().getText();

        // Version für dieses Dokument erhöhen
        int version = documentVersions.compute(uri, (k, v) -> (v == null) ? 1 : v + 1);

        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, version);
        // Wir senden den kompletten neuen Text. Das ist einfacher als inkrementelle Änderungen.
        TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(content);
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams(identifier, Collections.singletonList(changeEvent));

        serverProxy.getTextDocumentService().didChange(params);
        logger.debug("Sent 'didChange' (v{}) notification for {}", version, file.getName());

        // Nach einer Änderung müssen die Falt-Bereiche neu angefordert werden.
        requestFoldingRanges(editor);
    }

    /**
     * Fordert die Falt-Bereiche vom LSP-Server für den angegebenen Editor an
     * und aktualisiert die UI, wenn die Antwort eintrifft.
     *
     * @param editor Der XmlEditor, für den die Falt-Bereiche angefordert werden.
     */
    private void requestFoldingRanges(XmlEditor editor) {
        // Die Methode wurde robuster und lesbarer gemacht.
        if (serverProxy == null || editor == null || editor.getXmlFile() == null || editor.getXmlCodeEditor() == null) {
            return; // Nichts tun, wenn Server oder Datei nicht bereit sind
        }

        FoldingRangeRequestParams params = new FoldingRangeRequestParams(new TextDocumentIdentifier(editor.getXmlFile().toURI().toString()));
        logger.debug("Requesting folding ranges for: {}", editor.getXmlFile().getName());

        serverProxy.getTextDocumentService().foldingRange(params).thenAccept(foldingRanges -> {
            // KORREKTUR: Prüfen, ob die Antwort vom Server null ist, bevor sie verwendet wird.
            // Dies verhindert die NullPointerException aus dem Stacktrace.
            final List<FoldingRange> finalRanges = (foldingRanges != null) ? foldingRanges : Collections.emptyList();

            logger.debug("Received {} folding ranges.", finalRanges.size());
            // Die UI muss auf dem JavaFX Application Thread aktualisiert werden
            Platform.runLater(() -> {
                // Es ist gute Praxis, hier nochmals zu prüfen, ob der Editor noch existiert,
                // da der Benutzer den Tab in der Zwischenzeit geschlossen haben könnte.
                if (editor.getXmlCodeEditor() != null) {
                    editor.getXmlCodeEditor().updateFoldingRanges(finalRanges);
                    logger.debug("Folding ranges updated for {}.", editor.getXmlFile().getName());
                }
            });
        }).exceptionally(e -> {
            logger.error("Failed to get folding ranges for {}", editor.getXmlFile().getName(), e);
            return null;
        });
    }

    @FXML
    private void test() {
        // 1. Hole den aktuellen Editor.
        XmlEditor currentEditor = getCurrentXmlEditor();

        // 2. Wenn kein Editor aktiv ist, erstelle einen neuen.
        if (currentEditor == null) {
            logger.info("Test button clicked, but no active editor found. Creating a new one.");
            currentEditor = new XmlEditor(); // Erstellt einen neuen, leeren Editor

            // Notwendige Abhängigkeiten und Listener für den neuen Editor setzen
            currentEditor.setMainController(this.mainController);
            currentEditor.setLanguageServer(this.serverProxy);
            currentEditor.setOnSearchRequested(() -> {
                searchField.requestFocus();
                searchField.selectAll();
            });

            // Listener für Textänderungen auch hier hinzufügen, damit Falt-Bereiche
            // und Diagnosen bei der Bearbeitung aktualisiert werden.
            XmlEditor finalCurrentEditor = currentEditor;
            currentEditor.getXmlCodeEditor().getCodeArea().textProperty().addListener((obs, oldVal, newVal) -> {
                notifyLspServerFileChanged(finalCurrentEditor);
            });


            // Den neuen Editor zur UI hinzufügen und ihn zum aktiven Tab machen
            xmlFilesPane.getTabs().add(currentEditor);
            xmlFilesPane.getSelectionModel().select(currentEditor);
        }

        // 3. Definiere die Pfade zu den Testdateien.
        Path xmlExampleFile = Paths.get("release/examples/xml/FundsXML_422_Bond_Fund.xml");
        Path xsdExampleFile = Paths.get("release/examples/xsd/FundsXML4.xsd");

        // 4. Prüfe, ob die Testdatei existiert, um Fehler zu vermeiden.
        if (!Files.exists(xmlExampleFile)) {
            logger.error("Test file not found at path: {}", xmlExampleFile.toAbsolutePath());
            new Alert(Alert.AlertType.ERROR, "Test file not found: " + xmlExampleFile).showAndWait();
            return;
        }

        // 5. Konfiguriere den Editor und seinen Service mit den neuen Dateien.
        logger.debug("Loading test file '{}' into the current editor.", xmlExampleFile.getFileName());
        currentEditor.setXmlFile(xmlExampleFile.toFile());

        XmlService service = currentEditor.getXmlService();
        service.setCurrentXmlFile(xmlExampleFile.toFile());
        service.setCurrentXsdFile(xsdExampleFile.toFile());

        // 6. Lade den Inhalt der Datei in die UI.
        currentEditor.refresh();

        // 7. Führe Folgeaktionen aus, die vom geladenen Inhalt abhängen.
        validateSchema();

        CodeArea codeArea = getCurrentCodeArea();
        if (codeArea != null) {
            // Benachrichtige den LSP-Server, dass die Datei jetzt "geöffnet" ist.
            notifyLspServerFileOpened(currentEditor.getXmlFile(), codeArea.getText());
        } else {
            logger.warn("Could not get CodeArea after refresh to notify LSP server.");
        }

        logger.debug("Test file loading complete.");
    }
}
