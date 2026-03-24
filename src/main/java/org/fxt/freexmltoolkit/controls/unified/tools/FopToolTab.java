package org.fxt.freexmltoolkit.controls.unified.tools;

import java.io.File;
import java.util.HashMap;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.unified.AbstractToolTab;
import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.FOPService;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Tool tab for PDF generation from XML + XSL-FO using Apache FOP.
 */
public class FopToolTab extends AbstractToolTab {

    private static final Logger logger = LogManager.getLogger(FopToolTab.class);

    private final TextField xmlFileField;
    private final TextField xslFileField;
    private final TextField pdfOutputField;
    private final TextField authorField;
    private final TextField titleField;
    private final TextField keywordsField;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    private File xmlFile;
    private File xslFile;

    public FopToolTab() {
        super("tool:fop", "PDF Generation (FOP)", "bi-file-earmark-richtext", "#dc3545");

        // Main layout
        VBox mainBox = new VBox(16);
        mainBox.setPadding(new Insets(16));

        // Title
        Label titleLabel = new Label("PDF Generation with Apache FOP");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px;");

        // Input files
        Label inputLabel = new Label("Input & Output Files");
        inputLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(8);

        xmlFileField = new TextField();
        xmlFileField.setPromptText("Select XML source file...");
        xmlFileField.setEditable(false);
        GridPane.setHgrow(xmlFileField, Priority.ALWAYS);
        Button browseXml = new Button("Browse");
        browseXml.setOnAction(e -> browseXmlFile());
        inputGrid.add(new Label("XML Source:"), 0, 0);
        inputGrid.add(new HBox(4, xmlFileField, browseXml), 1, 0);
        GridPane.setHgrow(inputGrid.getChildren().get(inputGrid.getChildren().size() - 1), Priority.ALWAYS);

        xslFileField = new TextField();
        xslFileField.setPromptText("Select XSL-FO stylesheet...");
        xslFileField.setEditable(false);
        Button browseXsl = new Button("Browse");
        browseXsl.setOnAction(e -> browseXslFile());
        inputGrid.add(new Label("XSL-FO Stylesheet:"), 0, 1);
        inputGrid.add(new HBox(4, xslFileField, browseXsl), 1, 1);

        pdfOutputField = new TextField();
        pdfOutputField.setPromptText("Output PDF file path...");
        Button browsePdf = new Button("Browse");
        browsePdf.setOnAction(e -> browsePdfOutput());
        inputGrid.add(new Label("Output PDF:"), 0, 2);
        inputGrid.add(new HBox(4, pdfOutputField, browsePdf), 1, 2);

        // PDF Metadata
        Label metaLabel = new Label("PDF Metadata (Optional)");
        metaLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(10);
        metaGrid.setVgap(8);

        authorField = new TextField(System.getProperty("user.name"));
        titleField = new TextField();
        titleField.setPromptText("Document title");
        keywordsField = new TextField();
        keywordsField.setPromptText("Keywords, comma-separated");

        GridPane.setHgrow(authorField, Priority.ALWAYS);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(keywordsField, Priority.ALWAYS);

        metaGrid.add(new Label("Author:"), 0, 0);
        metaGrid.add(authorField, 1, 0);
        metaGrid.add(new Label("Title:"), 0, 1);
        metaGrid.add(titleField, 1, 1);
        metaGrid.add(new Label("Keywords:"), 0, 2);
        metaGrid.add(keywordsField, 1, 2);

        // Generate button
        Button generateBtn = new Button("Generate PDF");
        generateBtn.setStyle("-fx-font-size: 16px; -fx-padding: 8 24;");
        FontIcon genIcon = new FontIcon("bi-file-earmark-richtext");
        genIcon.setIconSize(20);
        generateBtn.setGraphic(genIcon);
        generateBtn.setOnAction(e -> generatePdf());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(32, 32);

        statusLabel = new Label("Ready - Select input files to begin");
        statusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 13px;");

        HBox actionRow = new HBox(12, generateBtn, progressIndicator, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        mainBox.getChildren().addAll(titleLabel, inputLabel, inputGrid,
                metaLabel, metaGrid, actionRow);

        ScrollPane scrollPane = new ScrollPane(mainBox);
        scrollPane.setFitToWidth(true);
        setContent(scrollPane);
    }

    private void browseXmlFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select XML Source File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File file = fc.showOpenDialog(getContent().getScene().getWindow());
        if (file != null) {
            xmlFile = file;
            xmlFileField.setText(file.getAbsolutePath());
        }
    }

    private void browseXslFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select XSL-FO Stylesheet");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSL Files", "*.xsl", "*.xslt", "*.fo"));
        File file = fc.showOpenDialog(getContent().getScene().getWindow());
        if (file != null) {
            xslFile = file;
            xslFileField.setText(file.getAbsolutePath());
        }
    }

    private void browsePdfOutput() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Output PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fc.showSaveDialog(getContent().getScene().getWindow());
        if (file != null) {
            pdfOutputField.setText(file.getAbsolutePath());
        }
    }

    private void generatePdf() {
        if (xmlFile == null || !xmlFile.exists()) {
            showAlert("Please select a valid XML source file.");
            return;
        }
        if (xslFile == null || !xslFile.exists()) {
            showAlert("Please select a valid XSL-FO stylesheet.");
            return;
        }
        String pdfPath = pdfOutputField.getText();
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            showAlert("Please specify an output PDF file path.");
            return;
        }

        File pdfFile = new File(pdfPath);
        statusLabel.setText("Generating PDF...");
        progressIndicator.setVisible(true);

        Thread worker = new Thread(() -> {
            try {
                FOPService fopService = new FOPService();
                PDFSettings settings = new PDFSettings(
                        new HashMap<>(),
                        "",
                        authorField.getText(),
                        "Created with FreeXMLToolkit",
                        java.time.LocalDate.now().toString(),
                        titleField.getText(),
                        keywordsField.getText()
                );

                fopService.createPdfFile(xmlFile, xslFile, pdfFile, settings);

                Platform.runLater(() -> {
                    statusLabel.setText("PDF generated: " + pdfFile.getName());
                    progressIndicator.setVisible(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                });
                logger.error("PDF generation failed: {}", e.getMessage());
            }
        }, "FOP-Generate-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("FOP - PDF Generation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
