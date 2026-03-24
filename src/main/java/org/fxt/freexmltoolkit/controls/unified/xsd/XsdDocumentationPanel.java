package org.fxt.freexmltoolkit.controls.unified.xsd;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel for XSD documentation generation (HTML, Word, PDF).
 * Simplified version for embedding in XsdUnifiedTab.
 */
public class XsdDocumentationPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationPanel.class);

    private final TextField sourceFileField;
    private final TextField outputPathField;
    private final ComboBox<String> formatCombo;
    private final CheckBox svgOverviewCheck;
    private final CheckBox markdownCheck;
    private final CheckBox includeSourceCheck;
    private final ProgressIndicator progressIndicator;
    private final Label statusLabel;
    private final Button generateButton;

    private File sourceFile;
    private java.util.function.BiConsumer<File, String> onGenerateRequested;

    public XsdDocumentationPanel() {
        setSpacing(12);
        setPadding(new Insets(12));

        // Source file
        Label titleLabel = new Label("XSD Documentation Generator");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        sourceFileField = new TextField();
        sourceFileField.setEditable(false);
        sourceFileField.setPromptText("XSD source file...");
        GridPane.setHgrow(sourceFileField, Priority.ALWAYS);
        grid.add(new Label("Source XSD:"), 0, 0);
        grid.add(sourceFileField, 1, 0);

        outputPathField = new TextField();
        outputPathField.setPromptText("Output path...");
        GridPane.setHgrow(outputPathField, Priority.ALWAYS);
        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> browseOutputPath());
        HBox outputRow = new HBox(4, outputPathField, browseBtn);
        HBox.setHgrow(outputPathField, Priority.ALWAYS);
        grid.add(new Label("Output:"), 0, 1);
        grid.add(outputRow, 1, 1);

        formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll("HTML", "Word (DOCX)", "PDF");
        formatCombo.setValue("HTML");
        grid.add(new Label("Format:"), 0, 2);
        grid.add(formatCombo, 1, 2);

        // Options
        Label optionsLabel = new Label("Options");
        optionsLabel.setStyle("-fx-font-weight: bold;");

        svgOverviewCheck = new CheckBox("Generate SVG overview page");
        svgOverviewCheck.setSelected(true);
        markdownCheck = new CheckBox("Use Markdown renderer");
        markdownCheck.setSelected(true);
        includeSourceCheck = new CheckBox("Include source code in output");
        includeSourceCheck.setSelected(false);

        // Generate button and status
        generateButton = new Button("Generate Documentation");
        generateButton.setStyle("-fx-font-size: 14px;");
        FontIcon genIcon = new FontIcon("bi-file-text");
        genIcon.setIconSize(16);
        generateButton.setGraphic(genIcon);
        generateButton.setOnAction(e -> generate());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(24, 24);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        HBox actionRow = new HBox(12, generateButton, progressIndicator, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(titleLabel, grid, optionsLabel, svgOverviewCheck,
                markdownCheck, includeSourceCheck, actionRow);
    }

    /**
     * Sets the source XSD file.
     */
    public void setSourceFile(File file) {
        this.sourceFile = file;
        if (file != null) {
            sourceFileField.setText(file.getAbsolutePath());
            // Auto-generate output path
            String baseName = file.getName().replaceFirst("\\.xsd$", "");
            File outputDir = new File(file.getParentFile(), baseName + "_doc");
            outputPathField.setText(outputDir.getAbsolutePath());
        }
    }

    /**
     * Sets the callback for documentation generation.
     * Parameters: sourceFile, outputFormat
     */
    public void setOnGenerateRequested(java.util.function.BiConsumer<File, String> handler) {
        this.onGenerateRequested = handler;
    }

    private void generate() {
        if (sourceFile == null || !sourceFile.exists()) {
            statusLabel.setText("No XSD file loaded");
            return;
        }

        String outputPath = outputPathField.getText();
        if (outputPath == null || outputPath.trim().isEmpty()) {
            statusLabel.setText("Please specify an output path");
            return;
        }

        statusLabel.setText("Generating...");
        progressIndicator.setVisible(true);
        generateButton.setDisable(true);

        if (onGenerateRequested != null) {
            onGenerateRequested.accept(sourceFile, formatCombo.getValue());
        }

        // Reset UI after a delay (the actual work is done via callback)
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> {
            progressIndicator.setVisible(false);
            generateButton.setDisable(false);
        });
        pause.play();
    }

    private void browseOutputPath() {
        String format = formatCombo.getValue();
        if ("HTML".equals(format)) {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Directory");
            File dir = dc.showDialog(getScene().getWindow());
            if (dir != null) {
                outputPathField.setText(dir.getAbsolutePath());
            }
        } else {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Output File");
            String ext = "Word (DOCX)".equals(format) ? "*.docx" : "*.pdf";
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(format, ext));
            File file = fc.showSaveDialog(getScene().getWindow());
            if (file != null) {
                outputPathField.setText(file.getAbsolutePath());
            }
        }
    }

    /**
     * Updates the status label.
     */
    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    /**
     * Gets the output path.
     */
    public String getOutputPath() {
        return outputPathField.getText();
    }

    /**
     * Gets the selected format.
     */
    public String getSelectedFormat() {
        return formatCombo.getValue();
    }

    /**
     * Gets whether SVG overview is enabled.
     */
    public boolean isSvgOverviewEnabled() {
        return svgOverviewCheck.isSelected();
    }

    /**
     * Gets whether Markdown rendering is enabled.
     */
    public boolean isMarkdownEnabled() {
        return markdownCheck.isSelected();
    }

    /**
     * Gets whether source code inclusion is enabled.
     */
    public boolean isIncludeSourceEnabled() {
        return includeSourceCheck.isSelected();
    }
}
