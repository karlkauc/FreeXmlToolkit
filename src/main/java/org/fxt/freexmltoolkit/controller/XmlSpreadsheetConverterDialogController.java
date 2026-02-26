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
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowData;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Controller for the XML&lt;-&gt;Spreadsheet Converter Dialog
 */
public class XmlSpreadsheetConverterDialogController implements Initializable {
    private static final Logger logger = LogManager.getLogger(XmlSpreadsheetConverterDialogController.class);

    // Services
    private final XmlSpreadsheetConverterService converterService = new XmlSpreadsheetConverterService();
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("SpreadsheetConverter-Thread");
        return t;
    });

    // Direction and Format Controls
    private ToggleGroup conversionDirectionGroup;
    @FXML
    private RadioButton xmlToSpreadsheetRadio;
    @FXML
    private RadioButton spreadsheetToXmlRadio;
    @FXML
    private ComboBox<String> formatCombo;

    // CSV Options
    @FXML
    private VBox csvOptionsBox;
    @FXML
    private ComboBox<String> delimiterCombo;
    @FXML
    private TextField customDelimiterField;
    @FXML
    private CheckBox includeBomCheckBox;
    @FXML
    private CheckBox alwaysQuoteCheckBox;

    // File Selection
    @FXML
    private Label sourceLabel;
    @FXML
    private TextField sourceFileField;
    @FXML
    private Button browseSourceButton;
    @FXML
    private CheckBox useCurrentXmlCheckBox;
    @FXML
    private Label targetLabel;
    @FXML
    private TextField targetFileField;
    @FXML
    private Button browseTargetButton;
    @FXML
    private Button autoTargetButton;

    // Conversion Options
    @FXML
    private CheckBox includeCommentsCheckBox;
    @FXML
    private CheckBox includeCDataCheckBox;
    @FXML
    private CheckBox includeNamespacesCheckBox;
    @FXML
    private CheckBox includeTypeColumnCheckBox;
    @FXML
    private CheckBox prettyPrintXmlCheckBox;
    @FXML
    private CheckBox validateXmlCheckBox;
    @FXML
    private CheckBox createBackupCheckBox;

    // Preview Controls
    @FXML
    private Button previewButton;
    @FXML
    private Button clearPreviewButton;
    @FXML
    private Label previewStatsLabel;
    @FXML
    private TableView<RowData> previewTable;
    @FXML
    private TableColumn<RowData, String> xpathColumn;
    @FXML
    private TableColumn<RowData, String> valueColumn;
    @FXML
    private TableColumn<RowData, String> typeColumn;
    @FXML
    private StackPane xmlPreviewPane;

    // Statistics
    @FXML
    private Label totalRowsLabel;
    @FXML
    private Label elementsCountLabel;
    @FXML
    private Label attributesCountLabel;
    @FXML
    private Label textNodesCountLabel;
    @FXML
    private Label commentsCountLabel;
    @FXML
    private Label cdataCountLabel;
    @FXML
    private TextArea processingInfoArea;

    // Status
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    // Internal state
    private String currentXmlContent;
    private File currentXmlFile;
    private CodeArea xmlPreviewCodeArea;
    private List<RowData> currentPreviewData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupEventHandlers();
        setupTableColumns();
        setupXmlPreviewPane();
        updateUIState();
        customizeButtonText();
    }

    private void setupUI() {
        // Create ToggleGroup and assign to RadioButtons
        conversionDirectionGroup = new ToggleGroup();
        xmlToSpreadsheetRadio.setToggleGroup(conversionDirectionGroup);
        spreadsheetToXmlRadio.setToggleGroup(conversionDirectionGroup);

        // Populate ComboBoxes
        formatCombo.getItems().addAll("Excel XLSX", "Excel XLS", "CSV");
        delimiterCombo.getItems().addAll("Comma (,)", "Semicolon (;)", "Tab", "Pipe (|)", "Custom");

        // Set default values
        formatCombo.getSelectionModel().select("Excel XLSX");
        delimiterCombo.getSelectionModel().select("Comma (,)");
        includeBomCheckBox.setSelected(false);
        alwaysQuoteCheckBox.setSelected(false);

        // Initialize conversion direction
        xmlToSpreadsheetRadio.setSelected(true);
    }

    private void setupEventHandlers() {
        // Format combo handler
        formatCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            boolean isCsv = "CSV".equals(newVal);
            csvOptionsBox.setVisible(isCsv);
            csvOptionsBox.setManaged(isCsv);
            updateUIState();
        });

        // Delimiter combo handler
        delimiterCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            boolean isCustom = "Custom".equals(newVal);
            customDelimiterField.setVisible(isCustom);
            customDelimiterField.setManaged(isCustom);
        });

        // Direction change handler
        conversionDirectionGroup.selectedToggleProperty().addListener((obs, old, newToggle) -> {
            updateUIState();
        });

        // Use current XML checkbox
        useCurrentXmlCheckBox.selectedProperty().addListener((obs, old, newVal) -> {
            sourceFileField.setDisable(newVal);
            browseSourceButton.setDisable(newVal);
        });

        // Auto-target button visibility
        autoTargetButton.disableProperty().bind(
                sourceFileField.textProperty().isEmpty().and(
                        useCurrentXmlCheckBox.selectedProperty().not()
                )
        );
    }

    private void setupTableColumns() {
        xpathColumn.setCellValueFactory(new PropertyValueFactory<>("xpath"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("nodeType"));

        // Enable text wrapping in value column
        valueColumn.setCellFactory(tc -> {
            TableCell<RowData, String> cell = new TableCell<>();
            Text text = new Text();
            cell.setGraphic(text);
            cell.setPrefHeight(Control.USE_COMPUTED_SIZE);
            text.wrappingWidthProperty().bind(valueColumn.widthProperty());
            text.textProperty().bind(cell.itemProperty());
            return cell;
        });

        // Limit preview to first 1000 rows for performance
        previewTable.setRowFactory(tv -> {
            TableRow<RowData> row = new TableRow<>();
            return row;
        });
    }

    private void setupXmlPreviewPane() {
        xmlPreviewCodeArea = new CodeArea();
        xmlPreviewCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(xmlPreviewCodeArea));
        xmlPreviewCodeArea.setEditable(false);
        xmlPreviewCodeArea.setWrapText(false);

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(xmlPreviewCodeArea);
        xmlPreviewPane.getChildren().add(scrollPane);
    }

    private void customizeButtonText() {
        // Find the DialogPane and customize the OK button text
        Platform.runLater(() -> {
            if (formatCombo.getScene() != null && formatCombo.getScene().getWindow() instanceof Stage stage) {
                if (stage.getScene().getRoot() instanceof DialogPane dialogPane) {
                    Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
                    if (okButton != null) {
                        okButton.setText("Convert");
                    }
                }
            }
        });
    }

    private void updateUIState() {
        boolean xmlToSpreadsheet = xmlToSpreadsheetRadio.isSelected();

        if (xmlToSpreadsheet) {
            sourceLabel.setText("XML Source:");
            targetLabel.setText("Target File:");
            useCurrentXmlCheckBox.setVisible(true);
        } else {
            sourceLabel.setText("Spreadsheet Source:");
            targetLabel.setText("XML Target:");
            useCurrentXmlCheckBox.setVisible(false);
            useCurrentXmlCheckBox.setSelected(false);
        }

        // Update file filters and extensions
        updateTargetFileExtension();
    }

    private void updateTargetFileExtension() {
        String sourceFile = sourceFileField.getText();
        if (!sourceFile.isEmpty() && !useCurrentXmlCheckBox.isSelected()) {
            autoGenerateTargetFile();
        }
    }

    @FXML
    private void browseSourceFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Source File");

        if (xmlToSpreadsheetRadio.isSelected()) {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
        } else {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
                    new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
        }

        File selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null) {
            sourceFileField.setText(selectedFile.getAbsolutePath());
            autoGenerateTargetFile();
        }
    }

    @FXML
    private void browseTargetFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Target File");

        if (xmlToSpreadsheetRadio.isSelected()) {
            String format = formatCombo.getValue();
            if ("Excel XLSX".equals(format)) {
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Excel XLSX", "*.xlsx")
                );
            } else if ("Excel XLS".equals(format)) {
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Excel XLS", "*.xls")
                );
            } else if ("CSV".equals(format)) {
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("CSV Files", "*.csv")
                );
            }
        } else {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XML Files", "*.xml")
            );
        }

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showSaveDialog(getStage());
        if (selectedFile != null) {
            targetFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void autoGenerateTargetFile() {
        String sourceFile = useCurrentXmlCheckBox.isSelected() ?
                (currentXmlFile != null ? currentXmlFile.getAbsolutePath() : "") :
                sourceFileField.getText();

        if (sourceFile.isEmpty()) {
            return;
        }

        File source = new File(sourceFile);
        String baseName = source.getName();
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }

        String extension;
        if (xmlToSpreadsheetRadio.isSelected()) {
            String format = formatCombo.getValue();
            switch (format) {
                case "Excel XLSX" -> extension = ".xlsx";
                case "Excel XLS" -> extension = ".xls";
                case "CSV" -> extension = ".csv";
                default -> extension = ".xlsx";
            }
        } else {
            extension = ".xml";
        }

        String targetPath = source.getParent() + File.separator + baseName + extension;
        targetFileField.setText(targetPath);
    }

    @FXML
    private void generatePreview() {
        statusLabel.setText("Generating preview...");
        statusLabel.setStyle("-fx-text-fill: orange;");
        progressBar.setVisible(true);
        previewButton.setDisable(true);

        Task<List<RowData>> previewTask = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                ConversionConfig config = createConversionConfig();

                if (xmlToSpreadsheetRadio.isSelected()) {
                    Document doc = getSourceDocument();
                    return converterService.extractRowsFromXml(doc, config);
                } else {
                    File sourceFile = new File(sourceFileField.getText());
                    String format = formatCombo.getValue();

                    if ("CSV".equals(format)) {
                        CsvHandler.CsvConfig csvConfig = createCsvConfig();
                        return new CsvHandler().readCsv(sourceFile, csvConfig, config);
                    } else {
                        Document doc = converterService.convertExcelToXml(sourceFile, config);
                        return converterService.extractRowsFromXml(doc, config);
                    }
                }
            }
        };

        previewTask.setOnSucceeded(e -> {
            List<RowData> data = previewTask.getValue();
            currentPreviewData = data;

            Platform.runLater(() -> {
                updatePreviewTable(data);
                updatePreviewXml(data);
                updateStatistics(data);

                statusLabel.setText("Preview generated successfully");
                statusLabel.setStyle("-fx-text-fill: green;");
                progressBar.setVisible(false);
                previewButton.setDisable(false);

                previewStatsLabel.setText(String.format("%d rows generated", data.size()));
            });
        });

        previewTask.setOnFailed(e -> {
            Throwable exception = previewTask.getException();
            Platform.runLater(() -> {
                statusLabel.setText("Preview generation failed");
                statusLabel.setStyle("-fx-text-fill: red;");
                progressBar.setVisible(false);
                previewButton.setDisable(false);

                showError("Preview Error", "Failed to generate preview: " + exception.getMessage());
            });
        });

        executorService.submit(previewTask);
    }

    @FXML
    private void clearPreview() {
        previewTable.getItems().clear();
        xmlPreviewCodeArea.clear();
        clearStatistics();
        currentPreviewData = null;
        previewStatsLabel.setText("Ready");
        processingInfoArea.clear();
    }

    private void updatePreviewTable(List<RowData> data) {
        // Limit to first 1000 rows for performance
        List<RowData> limitedData = data.stream().limit(1000).collect(Collectors.toList());
        previewTable.getItems().setAll(limitedData);

        if (data.size() > 1000) {
            processingInfoArea.appendText(String.format(
                    "Table preview limited to first 1000 rows (total: %d rows)\n", data.size()));
        }
    }

    private void updatePreviewXml(List<RowData> data) {
        if (!xmlToSpreadsheetRadio.isSelected()) {
            try {
                ConversionConfig config = createConversionConfig();
                Document doc = converterService.buildXmlFromRows(data, config);
                String xmlContent = converterService.documentToString(doc, config);
                xmlPreviewCodeArea.replaceText(xmlContent);
            } catch (Exception e) {
                xmlPreviewCodeArea.replaceText("Error generating XML preview: " + e.getMessage());
            }
        } else {
            xmlPreviewCodeArea.replaceText("XML preview not available for XML-to-Spreadsheet conversion");
        }
    }

    private void updateStatistics(List<RowData> data) {
        Map<String, Long> typeCounts = data.stream()
                .collect(Collectors.groupingBy(RowData::getNodeType, Collectors.counting()));

        totalRowsLabel.setText(String.valueOf(data.size()));
        elementsCountLabel.setText(String.valueOf(typeCounts.getOrDefault("element", 0L)));
        attributesCountLabel.setText(String.valueOf(typeCounts.getOrDefault("attribute", 0L)));
        textNodesCountLabel.setText(String.valueOf(typeCounts.getOrDefault("text", 0L)));
        commentsCountLabel.setText(String.valueOf(typeCounts.getOrDefault("comment", 0L)));
        cdataCountLabel.setText(String.valueOf(typeCounts.getOrDefault("cdata", 0L)));

        processingInfoArea.appendText(String.format("Statistics updated: %d total rows processed\n", data.size()));
        processingInfoArea.appendText("Breakdown by type:\n");
        typeCounts.forEach((type, count) ->
                processingInfoArea.appendText(String.format("  %s: %d\n", type, count)));
    }

    private void clearStatistics() {
        totalRowsLabel.setText("0");
        elementsCountLabel.setText("0");
        attributesCountLabel.setText("0");
        textNodesCountLabel.setText("0");
        commentsCountLabel.setText("0");
        cdataCountLabel.setText("0");
    }

    private Document getSourceDocument() throws Exception {
        String xmlContent;

        if (useCurrentXmlCheckBox.isSelected()) {
            xmlContent = currentXmlContent;
        } else {
            File sourceFile = new File(sourceFileField.getText());
            xmlContent = java.nio.file.Files.readString(sourceFile.toPath());
        }

        DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xmlContent)));
    }

    private ConversionConfig createConversionConfig() {
        ConversionConfig config = new ConversionConfig();
        config.setIncludeComments(includeCommentsCheckBox.isSelected());
        config.setIncludeCData(includeCDataCheckBox.isSelected());
        config.setIncludeNamespaces(includeNamespacesCheckBox.isSelected());
        config.setIncludeTypeColumn(includeTypeColumnCheckBox.isSelected());
        config.setPrettyPrintXml(prettyPrintXmlCheckBox.isSelected());
        return config;
    }

    private CsvHandler.CsvConfig createCsvConfig() {
        CsvHandler.CsvConfig config = new CsvHandler.CsvConfig();

        String delimiter = delimiterCombo.getValue();
        switch (delimiter) {
            case "Comma (,)" -> config.setDelimiter(',');
            case "Semicolon (;)" -> config.setDelimiter(';');
            case "Tab" -> config.setDelimiter('\t');
            case "Pipe (|)" -> config.setDelimiter('|');
            case "Custom" -> {
                String custom = customDelimiterField.getText();
                if (!custom.isEmpty()) {
                    config.setDelimiter(custom.charAt(0));
                }
            }
        }

        config.setIncludeBOM(includeBomCheckBox.isSelected());
        config.setAlwaysQuote(alwaysQuoteCheckBox.isSelected());

        return config;
    }

    private Stage getStage() {
        return (Stage) formatCombo.getScene().getWindow();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Public methods for external setup
    public void setSourceXml(String xmlContent) {
        this.currentXmlContent = xmlContent;
        useCurrentXmlCheckBox.setSelected(true);
    }

    public void setSourceFile(File xmlFile) {
        this.currentXmlFile = xmlFile;
        if (xmlFile != null) {
            sourceFileField.setText(xmlFile.getAbsolutePath());
            useCurrentXmlCheckBox.setSelected(false);
            autoGenerateTargetFile();
        }
    }

    public boolean isConversionRequested() {
        return currentPreviewData != null && !currentPreviewData.isEmpty();
    }

    /**
     * Selects XML to Spreadsheet conversion mode.
     */
    public void selectXmlToSpreadsheetMode() {
        if (xmlToSpreadsheetRadio != null) {
            xmlToSpreadsheetRadio.setSelected(true);
        }
    }

    /**
     * Selects Spreadsheet to XML conversion mode.
     */
    public void selectSpreadsheetToXmlMode() {
        if (spreadsheetToXmlRadio != null) {
            spreadsheetToXmlRadio.setSelected(true);
        }
    }

    public void performConversion() {
        if (currentPreviewData == null) {
            showError("No Preview", "Please generate a preview first.");
            return;
        }

        statusLabel.setText("Converting...");
        statusLabel.setStyle("-fx-text-fill: orange;");
        progressBar.setVisible(true);

        Task<Void> conversionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ConversionConfig config = createConversionConfig();
                File targetFile = new File(targetFileField.getText());

                if (xmlToSpreadsheetRadio.isSelected()) {
                    Document doc = getSourceDocument();
                    String format = formatCombo.getValue();

                    if ("CSV".equals(format)) {
                        CsvHandler.CsvConfig csvConfig = createCsvConfig();
                        converterService.convertXmlToCsv(doc, targetFile, csvConfig, config);
                    } else {
                        converterService.convertXmlToExcel(doc, targetFile, config);
                    }
                } else {
                    File sourceFile = new File(sourceFileField.getText());
                    Document doc;

                    if (sourceFile.getName().toLowerCase().endsWith(".csv")) {
                        CsvHandler.CsvConfig csvConfig = createCsvConfig();
                        doc = converterService.convertCsvToXml(sourceFile, csvConfig, config);
                    } else {
                        doc = converterService.convertExcelToXml(sourceFile, config);
                    }

                    String xmlContent = converterService.documentToString(doc, config);
                    java.nio.file.Files.writeString(targetFile.toPath(), xmlContent);
                }

                return null;
            }
        };

        conversionTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Conversion completed successfully");
                statusLabel.setStyle("-fx-text-fill: green;");
                progressBar.setVisible(false);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Conversion Complete");
                alert.setHeaderText(null);
                alert.setContentText("File converted successfully to:\n" + targetFileField.getText());
                alert.showAndWait();
            });
        });

        conversionTask.setOnFailed(e -> {
            Throwable exception = conversionTask.getException();
            Platform.runLater(() -> {
                statusLabel.setText("Conversion failed");
                statusLabel.setStyle("-fx-text-fill: red;");
                progressBar.setVisible(false);

                showError("Conversion Error", "Failed to convert file: " + exception.getMessage());
            });
        });

        executorService.submit(conversionTask);
    }
}