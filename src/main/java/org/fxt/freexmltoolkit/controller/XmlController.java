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
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    Button openFile, saveFile, prettyPrint, newFile, runXpathQuery, minifyButton;

    @FXML
    StackPane stackPaneXPath, stackPaneXQuery;

    @FXML
    ComboBox<File> schemaList = new ComboBox<>();

    FileChooser fileChooser = new FileChooser();

    @FXML
    HBox test;




    @FXML
    Tab xPathTab, xQueryTab;

    @FXML
    TabPane xPathQueryPane, xmlFilesPane;

    @FXML
    TextArea textAreaTemp;

    @FXML
    XmlEditor emptyXmlEditor;


    // LSP Server functionality removed - using XSD-based implementation
    // Executor für asynchrone UI-Tasks wie Formatierung
    private final ExecutorService formattingExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true); // Damit die Anwendung beendet werden kann, auch wenn Tasks laufen
        return t;
    });

    // LSP document versioning removed

    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");

        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);
        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText))));

        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);
        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText))));

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
                    """ );
        }

        reloadXmlText();

        xmlFilesPane.setOnDragOver(this::handleFileOverEvent);
        xmlFilesPane.setOnDragExited(this::handleDragExitedEvent);
        xmlFilesPane.setOnDragDropped(this::handleFileDroppedEvent);

        // Add listener for continuous validation
    }

    private void createAndAddXmlTab(File file) {
        // Die Konstruktoren von XmlEditor müssen angepasst werden, um den MainController zu akzeptieren
        XmlEditor xmlEditor = new XmlEditor(file); // Behalten Sie Ihren Konstruktor bei
        xmlEditor.setMainController(this.mainController); // Übergeben Sie den Controller

        // CRITICAL: Ensure LSP server is available and properly set
        // LSP functionality replaced by XSD-based implementation
        logger.debug("✅ Using XSD-based implementation instead of LSP");
        
        xmlEditor.refresh();

        // Apply sidebar visibility setting from preferences
        if (mainController != null) {
            boolean sidebarVisible = mainController.isXmlEditorSidebarVisible();
            xmlEditor.setXmlEditorSidebarVisible(sidebarVisible);
            logger.debug("Applied sidebar visibility setting to new tab: {}", sidebarVisible);
        }

        // Füge einen Listener hinzu, der den Server über Textänderungen informiert.
        // LSP text change notification removed

        xmlFilesPane.getTabs().add(xmlEditor);
        xmlFilesPane.getSelectionModel().select(xmlEditor);

        if (file != null) {
            mainController.addFileToRecentFiles(file);
            // LSP file opened notification removed
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
        // LSP Server initialization removed - using XSD-based implementation
        logger.debug("Using XSD-based implementation instead of LSP Server");
    }

    // LSP server setup method removed - using XSD-based implementation

    // LSP diagnostics publishing removed - using XML validation instead

    // LSP editor URI lookup removed

    // LSP server file notification removed - using XSD-based implementation

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

                    // LSP file opened notification removed
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
                    // LSP file opened notification removed
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
        // Verwende das vom Service erkannte Schema.
        File schemaToUse = null;
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
        });

        minifyTask.setOnFailed(event -> {
            logger.error("Failed to minify XML", minifyTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not minify XML: " + minifyTask.getException().getMessage()).showAndWait();
            minifyButton.setDisable(false);
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

            createAndAddXmlTab(selectedFile); // Use the central method

            // Fokus auf den neuen Editor setzen, nachdem die UI-Änderungen verarbeitet wurden.
            Platform.runLater(() -> {
                XmlEditor xmlEditor = getCurrentXmlEditor();
                // 1. Zuerst den Fokus auf den Tab-Container legen.
                xmlFilesPane.requestFocus();

                // 2. Dann den Fokus an die CodeArea im Inneren übergeben.
                if (xmlEditor.getXmlCodeEditor().getCodeArea() != null) {
                    xmlEditor.getXmlCodeEditor().getCodeArea().requestFocus();
                    xmlEditor.getXmlCodeEditor().moveUp(); // Setzt den Cursor an den Anfang.
                }
            });
        } else {
            logger.debug("No file selected");
        }
    }

    /**
     * Shuts down executor services.
     */
    public void shutdown() {
        if (!formattingExecutor.isShutdown()) {
            formattingExecutor.shutdownNow();
            logger.debug("Formatting-Executor-Service heruntergefahren.");
        }
        logger.info("XmlController shutdown completed.");
    }

    // LSP didChange notification removed - using XSD-based implementation

    // LSP folding ranges request removed

    @FXML
    private void test() {
        // 1. Hole den aktuellen Editor.
        XmlEditor currentEditor = getCurrentXmlEditor();

        // 2. Wenn kein Editor aktiv ist, erstelle einen neuen.
        if (currentEditor == null) {
            logger.info("Test button clicked, but no active editor found. Creating a new one.");
            createAndAddXmlTab(null);
            currentEditor = getCurrentXmlEditor();
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

        // Cursor und Ansicht an den Anfang setzen, nachdem die UI-Änderungen verarbeitet wurden.
        XmlEditor finalCurrentEditor = currentEditor;
        Platform.runLater(() -> {
            if (finalCurrentEditor.getXmlCodeEditor() != null) {
                finalCurrentEditor.getXmlCodeEditor().moveUp();

                // Force syntax highlighting refresh
                finalCurrentEditor.getXmlCodeEditor().refreshSyntaxHighlighting();

                // Force folding regions refresh
                finalCurrentEditor.getXmlCodeEditor().refreshFoldingRegions();
            }
        });
        // 7. Führe Folgeaktionen aus, die vom geladenen Inhalt abhängen.

        // LSP file opened notification removed

        logger.debug("Test file loading complete.");
    }

    /**
     * Sets the visibility of the XML Editor Sidebar for all tabs.
     *
     * @param visible true to show the sidebar, false to hide it completely
     */
    public void setXmlEditorSidebarVisible(boolean visible) {
        logger.debug("Setting XML Editor Sidebar visibility to: {}", visible);

        // Apply to all existing tabs
        if (xmlFilesPane != null) {
            for (Tab tab : xmlFilesPane.getTabs()) {
                if (tab instanceof XmlEditor xmlEditor) {
                    xmlEditor.setXmlEditorSidebarVisible(visible);
                }
            }
        }
    }
}
