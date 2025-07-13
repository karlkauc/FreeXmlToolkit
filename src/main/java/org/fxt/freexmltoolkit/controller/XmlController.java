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
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.MyLspClient;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
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

    CodeArea codeAreaXpath = new CodeArea();
    CodeArea codeAreaXQuery = new CodeArea();

    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    private MainController mainController;

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, validateSchema, runXpathQuery;

    @FXML
    StackPane stackPaneXPath, stackPaneXQuery;

    @FXML
    ComboBox<File> schemaList = new ComboBox<>();

    String lastOpenDir;
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

    // --- LSP Server Felder ---
    private LanguageServer serverProxy;
    private MyLspClient lspClient;
    private final ExecutorService lspExecutor = Executors.newSingleThreadExecutor();
    private Future<?> clientListening;
    // NEU: Map zur Speicherung von Diagnosen, hierher verschoben.
    private final Map<String, List<Diagnostic>> diagnosticsMap = new ConcurrentHashMap<>();


    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");
        schemaValidText.setText("");

        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);
        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, XmlEditor.computeHighlighting(newText))));

        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);
        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, XmlEditor.computeHighlighting(newText))));

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            XmlEditor currentEditor = getCurrentXmlEditor();
            if (currentEditor != null) {
                // Ruft die neue Suchmethode im aktiven Editor auf
                currentEditor.searchAndHighlight(newVal);
            }
        });

        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                XmlEditor currentEditor = getCurrentXmlEditor();
                if (currentEditor != null) {
                    // Entfernt die Hervorhebung im Editor
                    currentEditor.clearHighlight();
                }
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
        xmlEditor.setMainController(this.mainController); // NEU: Übergeben Sie den Controller

        xmlEditor.setLanguageServer(this.serverProxy);
        xmlEditor.refresh();

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
            XmlEditor xmlEditor = new XmlEditor(f);
            xmlEditor.setXmlFile(f);
            xmlEditor.refresh();

            // NEU: Setzt die Aktion für die Suchanfrage
            xmlEditor.setOnSearchRequested(() -> {
                searchField.requestFocus();
                searchField.selectAll();
            });

            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);
        }
    }

    protected void loadFile(File f) {
        logger.debug("Loading file {}", f.getAbsolutePath());

        XmlEditor xmlEditor = new XmlEditor(f);
        xmlEditor.refresh();

        xmlEditor.setOnSearchRequested(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });

        xmlFilesPane.getTabs().add(xmlEditor);
        xmlFilesPane.getSelectionModel().select(xmlEditor);
    }

    @FXML
    private void newFilePressed() {
        logger.debug("New File Pressed");

        XmlEditor x = new XmlEditor();

        x.setOnSearchRequested(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });

        xmlFilesPane.getTabs().add(x);
        xmlFilesPane.getSelectionModel().select(x);
    }

    private XmlEditor getCurrentXmlEditor() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        return (XmlEditor) active;
    }

    private CodeArea getCurrentCodeArea() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        TabPane tp = (TabPane) active.getContent();
        Tab tabText = tp.getTabs().getFirst();
        StackPane sp = (StackPane) tabText.getContent();
        VirtualizedScrollPane<?> vsp = (VirtualizedScrollPane<?>) sp.getChildren().getFirst();

        return (CodeArea) vsp.getContent();
    }

    @FXML
    private void increaseFontSize() {
        getCurrentXmlEditor().increaseFontSize();
    }

    @FXML
    private void decreaseFontSize() {
        getCurrentXmlEditor().decreaseFontSize();
    }


    @FXML
    private void runXpathQueryPressed() {
        var currentCodeArea = getCurrentCodeArea();
        String xml = currentCodeArea.getText();

        if (xml != null) {
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
     * NEU: Wird vom MyLspClient aufgerufen. Speichert die Diagnosen
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
     * NEU: Hilfsmethode, um den geöffneten XmlEditor für einen URI zu finden.
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

                // ... restlicher Code der Methode (falls vorhanden) ...

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

                    logger.debug("TEXT DOCUMENT");
                    TextDocumentItem textDocument = new TextDocumentItem(xmlEditor.getXmlFile().toURI().toString(), "xml", 1, xmlEditor.getText());
                    this.serverProxy.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument));

                    notifyLspServerFileOpened(xmlEditor.getXmlFile(), getCurrentCodeArea().getText());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @FXML
    private boolean saveFile() {
        var errors = getCurrentXmlEditor().getXmlService().validateText(getCurrentCodeArea().getText());

        if (errors == null || errors.isEmpty()) {
            return saveTextToFile();
        } else {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Text not schema Valid");
            a.setHeaderText(errors.size() + " Errors found.");
            a.setContentText("Save anyway?");

            var result = a.showAndWait();
            if (result.isPresent()) {
                var buttonType = result.get();
                if (buttonType == ButtonType.OK) {
                    return saveTextToFile();
                }
            }
        }
        return false;
    }

    private boolean saveTextToFile() {
        try {
            var currentXmlEditor = getCurrentXmlEditor();
            File xmlFile = currentXmlEditor.getXmlFile();

            if (xmlFile == null) {
                if (lastOpenDir == null) {
                    lastOpenDir = Path.of(".").toString();
                    logger.debug("New last open Dir: {}", lastOpenDir);
                }

                fileChooser.setInitialDirectory(new File(lastOpenDir));
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
                File selectedFile = fileChooser.showSaveDialog(null);

                if (selectedFile != null) {
                    getCurrentXmlEditor().getXmlService().setCurrentXmlFile(selectedFile);
                    currentXmlEditor.setXmlFile(selectedFile);
                    currentXmlEditor.setText(selectedFile.getName());

                    if (!selectedFile.exists()) {
                        Files.writeString(selectedFile.toPath(), getCurrentCodeArea().getText(), Charset.defaultCharset());
                        this.schemaValidText.setText("Saved " + selectedFile.length() + " Bytes");
                    }
                }

            } else {
                byte[] strToBytes = getCurrentCodeArea().getText().getBytes();
                Files.write(xmlFile.toPath(), strToBytes);
            }

            Path path = Paths.get(getCurrentXmlEditor().getXmlService().getCurrentXmlFile().getPath());
            byte[] strToBytes = getCurrentCodeArea().getText().getBytes();
            Files.write(path, strToBytes);

            logger.debug("File saved!");
            getCurrentXmlEditor().getXmlService().setCurrentXmlFile(path.toFile());
            schemaValidText.setText("File '" + path + "' saved (" + path.toFile().length() + " bytes)");

            return true;
        } catch (Exception e) {
            logger.error("Exception in writing File: {}", e.getMessage());
            // logger.error("File: {}", this.xmlService.getCurrentXmlFile().getAbsolutePath());
        }
        return false;
    }

    @FXML
    private void minifyXmlText() {
        var currentCodeArea = getCurrentCodeArea();
        String xml = currentCodeArea.getText();

        try {
            final String minifiedString = XmlService.convertXmlToOneLine(xml);
            if (!minifiedString.isEmpty()) {
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, minifiedString);
            }
        } catch (TransformerException transformerException) {
            logger.error(transformerException.getMessage());
        }
    }

    @FXML
    private void prettifyingXmlText() {
        var currentCodeArea = getCurrentCodeArea();
        String text = currentCodeArea.getText();
        currentCodeArea.clear();

        final String prettyString = XmlService.prettyFormat(text, 4);
        if (!prettyString.isEmpty()) {
            currentCodeArea.replaceText(0, 0, prettyString);
        }
    }

    @FXML
    private void validateSchema() {
        logger.debug("Validate Schema");
        getCurrentXmlEditor().getXmlService().loadSchemaFromXMLFile(); // schaut, ob er schema selber finden kann
        String xsdLocation = getCurrentXmlEditor().getXmlService().getRemoteXsdLocation();
        logger.debug("XSD Location: {}", xsdLocation);

        if (xsdLocation != null) {
            getCurrentXmlEditor().getXmlService().loadSchemaFromXMLFile();
            logger.debug("Schema loaded: {}", xsdLocation);

            if (getCurrentCodeArea().getText().length() > 1) {
                var errors = getCurrentXmlEditor().getXmlService().validateText(getCurrentCodeArea().getText());
                if (errors != null && !errors.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);

                    // alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
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
        var area = getCurrentCodeArea();
        if (area != null) {
            // Setzt den Cursor an die Position 0 (Anfang des Dokuments)
            area.moveTo(0);
            // Erzwingt, dass der Scrollbalken ganz nach oben geht, indem die erste Zeile oben angezeigt wird
            area.showParagraphAtTop(0);
            // Stellt sicher, dass der Bereich den Fokus erhält, damit der Cursor sichtbar ist
            area.requestFocus();
        }
    }

    @FXML
    private void moveDown() {
        logger.debug("Moving caret and scrollbar to the end.");
        var area = getCurrentCodeArea();
        if (area != null && area.getText() != null && !area.getParagraphs().isEmpty()) {
            // Setzt den Cursor an das Ende des Textes
            area.moveTo(area.getLength());
            // Erzwingt, dass der Scrollbalken ganz nach unten geht, indem die letzte Zeile unten angezeigt wird
            area.showParagraphAtBottom(area.getParagraphs().size() - 1);
            // Stellt sicher, dass der Bereich den Fokus erhält, damit der Cursor sichtbar ist
            area.requestFocus();
        }
    }

    @FXML
    private void openFile() {
        logger.debug("Last open Dir: {}", lastOpenDir);
        if (lastOpenDir == null) {
            lastOpenDir = Path.of(".").toString();
            logger.debug("New last open Dir: {}", lastOpenDir);
        }

        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            this.lastOpenDir = selectedFile.getParent();

            XmlEditor xmlEditor = new XmlEditor(selectedFile);
            xmlEditor.refresh();

            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);

            getCurrentXmlEditor().getXmlService().setCurrentXmlFile(selectedFile);
            if (getCurrentXmlEditor().getXmlService().loadSchemaFromXMLFile()) {
                schemaList.getItems().add(getCurrentXmlEditor().getXmlService().getCurrentXsdFile());
            }

            validateSchema();
            mainController.addFileToRecentFiles(selectedFile);
        } else {
            logger.debug("No file selected");
        }
    }


    @FXML
    private void print() {
        logger.debug("BIN DRINNEN");
        logger.debug(emptyXmlEditor.getDocument().getNodeValue());
    }
    @FXML
    private void test() {
        Path xmlExampleFile = Paths.get("release/examples/xml/FundsXML_422_Bond_Fund.xml");

        getCurrentXmlEditor().getXmlService().setCurrentXmlFile(xmlExampleFile.toFile());
        getCurrentXmlEditor().getXmlService().setCurrentXsdFile(Paths.get("release/examples/xsd/FundsXML4.xsd").toFile());

        try {
            emptyXmlEditor = getCurrentXmlEditor();
            emptyXmlEditor.setXmlFile(xmlExampleFile.toFile());
            emptyXmlEditor.refresh();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        getCurrentXmlEditor().getXmlService().setCurrentXmlFile(xmlExampleFile.toFile());
        if (getCurrentXmlEditor().getXmlService().loadSchemaFromXMLFile()) {
            schemaList.getItems().add(getCurrentXmlEditor().getXmlService().getCurrentXsdFile());
        }

        validateSchema();
        notifyLspServerFileOpened(getCurrentXmlEditor().getXmlFile(), getCurrentCodeArea().getText());
    }
}
