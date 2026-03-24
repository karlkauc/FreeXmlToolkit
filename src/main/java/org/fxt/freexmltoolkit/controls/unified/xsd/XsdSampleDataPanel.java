package org.fxt.freexmltoolkit.controls.unified.xsd;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel for generating sample XML data from an XSD schema.
 * Feature parity with the standalone XSD Editor's Sample Data tab.
 */
public class XsdSampleDataPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdSampleDataPanel.class);

    private final CheckBox mandatoryOnlyCheck;
    private final Spinner<Integer> maxOccurrencesSpinner;
    private final TextField outputPathField;
    private final XmlCodeEditorV2 outputEditor;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;
    private final Button generateButton;
    private final Button validateButton;
    private final Button exportErrorsButton;
    private final TableView<ValidationErrorRow> validationTable;

    private File sourceXsdFile;
    private XsdDocumentationService xsdDocService;
    private List<XsdDocumentationService.ValidationError> currentErrors;

    public XsdSampleDataPanel() {
        setSpacing(12);
        setPadding(new Insets(12));

        // Title
        Label titleLabel = new Label("Sample Data Generation");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Options
        Label optionsLabel = new Label("Settings");
        optionsLabel.setStyle("-fx-font-weight: bold;");

        mandatoryOnlyCheck = new CheckBox("Mandatory elements only");
        mandatoryOnlyCheck.setSelected(false);

        HBox maxOccRow = new HBox(8);
        maxOccRow.setAlignment(Pos.CENTER_LEFT);
        maxOccurrencesSpinner = new Spinner<>();
        maxOccurrencesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 2));
        maxOccurrencesSpinner.setPrefWidth(80);
        maxOccRow.getChildren().addAll(new Label("Max occurrences for repeating elements:"), maxOccurrencesSpinner);

        // Output path
        HBox outputPathRow = new HBox(8);
        outputPathRow.setAlignment(Pos.CENTER_LEFT);
        outputPathField = new TextField();
        outputPathField.setPromptText("Output XML file path (optional)...");
        HBox.setHgrow(outputPathField, Priority.ALWAYS);
        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> browseOutputPath());
        outputPathRow.getChildren().addAll(new Label("Output File:"), outputPathField, browseBtn);

        // Action buttons
        generateButton = new Button("Generate Sample XML");
        generateButton.setStyle("-fx-font-size: 14px;");
        FontIcon genIcon = new FontIcon("bi-play-fill");
        genIcon.setIconSize(16);
        generateButton.setGraphic(genIcon);
        generateButton.setOnAction(e -> generateSampleData());

        validateButton = new Button("Validate Generated XML");
        validateButton.setDisable(true);
        validateButton.setOnAction(e -> validateGeneratedXml());

        exportErrorsButton = new Button("Export Errors");
        exportErrorsButton.setVisible(false);
        exportErrorsButton.setManaged(false);
        exportErrorsButton.setOnAction(e -> exportValidationErrors());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(24, 24);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        HBox actionRow = new HBox(8, generateButton, validateButton, exportErrorsButton, progressIndicator, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        // Output editor with full XML syntax highlighting and line numbers
        outputEditor = XmlCodeEditorV2Factory.createWithoutSchema();

        // Validation results table
        validationTable = createValidationTable();
        validationTable.setVisible(false);
        validationTable.setManaged(false);
        validationTable.setPrefHeight(150);

        VBox resultBox = new VBox(4, outputEditor, validationTable);
        VBox.setVgrow(outputEditor, Priority.ALWAYS);
        VBox.setVgrow(resultBox, Priority.ALWAYS);

        getChildren().addAll(titleLabel, optionsLabel, mandatoryOnlyCheck,
                maxOccRow, outputPathRow, actionRow, resultBox);
    }

    @SuppressWarnings("unchecked")
    private TableView<ValidationErrorRow> createValidationTable() {
        TableView<ValidationErrorRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No validation errors"));

        TableColumn<ValidationErrorRow, Number> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(cd -> cd.getValue().lineProperty());
        lineCol.setPrefWidth(60);

        TableColumn<ValidationErrorRow, Number> colCol = new TableColumn<>("Column");
        colCol.setCellValueFactory(cd -> cd.getValue().columnProperty());
        colCol.setPrefWidth(70);

        TableColumn<ValidationErrorRow, String> severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(cd -> cd.getValue().severityProperty());
        severityCol.setPrefWidth(80);

        TableColumn<ValidationErrorRow, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(cd -> cd.getValue().messageProperty());

        table.getColumns().addAll(lineCol, colCol, severityCol, messageCol);
        return table;
    }

    public void setSourceFile(File file) {
        this.sourceXsdFile = file;
        generateButton.setDisable(file == null);
    }

    public void generateSampleData() {
        if (sourceXsdFile == null || !sourceXsdFile.exists()) {
            statusLabel.setText("No XSD file loaded");
            return;
        }

        progressIndicator.setVisible(true);
        statusLabel.setText("Generating...");
        generateButton.setDisable(true);

        Thread worker = new Thread(() -> {
            try {
                if (xsdDocService == null) {
                    xsdDocService = new XsdDocumentationService();
                }
                xsdDocService.setXsdFilePath(sourceXsdFile.getAbsolutePath());
                String sampleXml = xsdDocService.generateSampleXml(
                        mandatoryOnlyCheck.isSelected(),
                        maxOccurrencesSpinner.getValue());

                if (sampleXml != null) {
                    try {
                        sampleXml = XmlService.prettyFormat(sampleXml, 4);
                    } catch (Exception ignored) {}
                }

                // Save to file if output path specified
                String outputPath = outputPathField.getText();
                if (outputPath != null && !outputPath.trim().isEmpty() && sampleXml != null) {
                    java.nio.file.Files.writeString(new File(outputPath).toPath(), sampleXml, StandardCharsets.UTF_8);
                }

                String result = sampleXml;
                Platform.runLater(() -> {
                    outputEditor.setText(result != null ? result : "<!-- No sample data generated -->");
                    statusLabel.setText("Generated successfully");
                    progressIndicator.setVisible(false);
                    generateButton.setDisable(false);
                    validateButton.setDisable(false);
                    validationTable.setVisible(false);
                    validationTable.setManaged(false);
                    exportErrorsButton.setVisible(false);
                    exportErrorsButton.setManaged(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    outputEditor.setText("<!-- Error: " + e.getMessage() + " -->");
                    statusLabel.setText("Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    generateButton.setDisable(false);
                });
                logger.error("Failed to generate sample data: {}", e.getMessage());
            }
        }, "SampleData-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public void validateGeneratedXml() {
        String xml = outputEditor.getText();
        if (xml == null || xml.trim().isEmpty()) {
            statusLabel.setText("No XML to validate");
            return;
        }

        progressIndicator.setVisible(true);
        statusLabel.setText("Validating...");

        Thread worker = new Thread(() -> {
            try {
                if (xsdDocService == null) {
                    xsdDocService = new XsdDocumentationService();
                }
                xsdDocService.setXsdFilePath(sourceXsdFile.getAbsolutePath());
                var result = xsdDocService.validateXmlAgainstSchema(xml);

                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    validationTable.getItems().clear();

                    if (result.isValid()) {
                        statusLabel.setText("Validation: XML is valid");
                        validationTable.setVisible(false);
                        validationTable.setManaged(false);
                        exportErrorsButton.setVisible(false);
                        exportErrorsButton.setManaged(false);
                    } else {
                        currentErrors = result.errors();
                        statusLabel.setText("Validation: " + result.errors().size() + " error(s)");

                        for (var error : result.errors()) {
                            validationTable.getItems().add(new ValidationErrorRow(
                                    error.lineNumber(), error.columnNumber(), error.severity(), error.message()));
                        }
                        validationTable.setVisible(true);
                        validationTable.setManaged(true);
                        exportErrorsButton.setVisible(true);
                        exportErrorsButton.setManaged(true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Validation error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        }, "SampleData-Validate-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void exportValidationErrors() {
        if (currentErrors == null || currentErrors.isEmpty()) {
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Validation Errors");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fc.setInitialFileName("validation_errors.csv");
        File file = fc.showSaveDialog(getScene().getWindow());

        if (file != null) {
            try {
                StringBuilder sb = new StringBuilder("Line,Column,Severity,Message\n");
                for (var error : currentErrors) {
                    sb.append(error.lineNumber()).append(",")
                            .append(error.columnNumber()).append(",")
                            .append(csvEscape(error.severity())).append(",")
                            .append(csvEscape(error.message())).append("\n");
                }
                java.nio.file.Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
                statusLabel.setText("Exported to: " + file.getName());
            } catch (Exception e) {
                statusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    private void browseOutputPath() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Generated XML");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        if (sourceXsdFile != null) {
            fc.setInitialDirectory(sourceXsdFile.getParentFile());
            fc.setInitialFileName(sourceXsdFile.getName().replaceFirst("\\.xsd$", "_sample.xml"));
        }
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file != null) {
            outputPathField.setText(file.getAbsolutePath());
        }
    }

    private String csvEscape(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public static class ValidationErrorRow {
        private final SimpleIntegerProperty line;
        private final SimpleIntegerProperty column;
        private final SimpleStringProperty severity;
        private final SimpleStringProperty message;

        public ValidationErrorRow(int line, int column, String severity, String message) {
            this.line = new SimpleIntegerProperty(line);
            this.column = new SimpleIntegerProperty(column);
            this.severity = new SimpleStringProperty(severity);
            this.message = new SimpleStringProperty(message);
        }

        public SimpleIntegerProperty lineProperty() { return line; }
        public SimpleIntegerProperty columnProperty() { return column; }
        public SimpleStringProperty severityProperty() { return severity; }
        public SimpleStringProperty messageProperty() { return message; }
    }
}
