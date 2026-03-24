package org.fxt.freexmltoolkit.controls.unified.xsd;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel for generating sample XML data from an XSD schema.
 */
public class XsdSampleDataPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdSampleDataPanel.class);

    private final CheckBox mandatoryOnlyCheck;
    private final Spinner<Integer> maxOccurrencesSpinner;
    private final CodeArea outputArea;
    private final Label statusLabel;

    private File sourceXsdFile;
    private final XmlService xmlService;
    private XsdDocumentationService xsdDocService;

    public XsdSampleDataPanel() {
        setSpacing(12);
        setPadding(new Insets(12));

        this.xmlService = new XmlServiceImpl();

        // Title
        Label titleLabel = new Label("Sample Data Generation");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Options
        Label optionsLabel = new Label("Options");
        optionsLabel.setStyle("-fx-font-weight: bold;");

        mandatoryOnlyCheck = new CheckBox("Mandatory elements only");
        mandatoryOnlyCheck.setSelected(false);

        HBox maxOccRow = new HBox(8);
        maxOccRow.setAlignment(Pos.CENTER_LEFT);
        Label maxOccLabel = new Label("Max occurrences for repeating elements:");
        maxOccurrencesSpinner = new Spinner<>();
        maxOccurrencesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 2));
        maxOccurrencesSpinner.setPrefWidth(80);
        maxOccRow.getChildren().addAll(maxOccLabel, maxOccurrencesSpinner);

        // Buttons
        Button generateBtn = new Button("Generate Sample XML");
        generateBtn.setStyle("-fx-font-size: 14px;");
        FontIcon genIcon = new FontIcon("bi-play-fill");
        genIcon.setIconSize(16);
        generateBtn.setGraphic(genIcon);
        generateBtn.setOnAction(e -> generateSampleData());

        Button validateBtn = new Button("Validate Generated XML");
        validateBtn.setOnAction(e -> validateGeneratedXml());

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        HBox actionRow = new HBox(8, generateBtn, validateBtn, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        // Output area
        outputArea = new CodeArea();
        outputArea.setEditable(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(outputArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, optionsLabel, mandatoryOnlyCheck,
                maxOccRow, actionRow, scrollPane);
    }

    /**
     * Sets the source XSD file for sample generation.
     */
    public void setSourceFile(File file) {
        this.sourceXsdFile = file;
    }

    /**
     * Generates sample XML data from the XSD schema.
     */
    public void generateSampleData() {
        if (sourceXsdFile == null || !sourceXsdFile.exists()) {
            statusLabel.setText("No XSD file loaded");
            return;
        }

        statusLabel.setText("Generating...");

        try {
            if (xsdDocService == null) {
                xsdDocService = new XsdDocumentationService();
            }
            xsdDocService.setXsdFilePath(sourceXsdFile.getAbsolutePath());
            String sampleXml = xsdDocService.generateSampleXml(
                    mandatoryOnlyCheck.isSelected(),
                    maxOccurrencesSpinner.getValue()
            );

            if (sampleXml != null && !sampleXml.isEmpty()) {
                // Pretty-print the output
                try {
                    sampleXml = XmlService.prettyFormat(sampleXml, 4);
                } catch (Exception ignored) {
                    // Use raw output if formatting fails
                }
                outputArea.replaceText(sampleXml);
                statusLabel.setText("Generated successfully");
            } else {
                outputArea.replaceText("<!-- No sample data generated -->");
                statusLabel.setText("No data generated");
            }
        } catch (Exception e) {
            outputArea.replaceText("<!-- Error: " + e.getMessage() + " -->");
            statusLabel.setText("Error: " + e.getMessage());
            logger.error("Failed to generate sample data: {}", e.getMessage());
        }
    }

    /**
     * Validates the generated XML against the XSD schema.
     */
    public void validateGeneratedXml() {
        String xml = outputArea.getText();
        if (xml == null || xml.trim().isEmpty()) {
            statusLabel.setText("No XML to validate");
            return;
        }

        try {
            var errors = xmlService.validateText(xml);
            if (errors == null || errors.isEmpty()) {
                statusLabel.setText("Validation: XML is valid");
            } else {
                statusLabel.setText("Validation: " + errors.size() + " error(s)");
            }
        } catch (Exception e) {
            statusLabel.setText("Validation error: " + e.getMessage());
        }
    }

    /**
     * Gets the generated XML content.
     */
    public String getGeneratedXml() {
        return outputArea.getText();
    }
}
