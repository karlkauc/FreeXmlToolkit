package org.fxt.freexmltoolkit.controls.unified.xsd;

import java.io.File;
import java.nio.charset.StandardCharsets;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel for flattening XSD schemas (merging includes/imports into single file).
 */
public class XsdFlattenPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdFlattenPanel.class);

    private final CheckBox flattenIncludesCheck;
    private final CheckBox flattenImportsCheck;
    private final CodeArea outputArea;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    private File sourceXsdFile;

    public XsdFlattenPanel() {
        setSpacing(12);
        setPadding(new Insets(12));

        // Title
        Label titleLabel = new Label("Schema Flatten");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Options
        Label optionsLabel = new Label("Options");
        optionsLabel.setStyle("-fx-font-weight: bold;");

        flattenIncludesCheck = new CheckBox("Flatten xs:include");
        flattenIncludesCheck.setSelected(true);

        flattenImportsCheck = new CheckBox("Flatten xs:import");
        flattenImportsCheck.setSelected(false);

        // Action
        Button flattenBtn = new Button("Flatten Schema");
        flattenBtn.setStyle("-fx-font-size: 14px;");
        FontIcon flatIcon = new FontIcon("bi-layers");
        flatIcon.setIconSize(16);
        flattenBtn.setGraphic(flatIcon);
        flattenBtn.setOnAction(e -> flattenSchema());

        Button saveBtn = new Button("Save Flattened Schema");
        saveBtn.setOnAction(e -> saveFlattenedSchema());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(24, 24);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");

        HBox actionRow = new HBox(8, flattenBtn, saveBtn, progressIndicator, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        // Output area
        outputArea = new CodeArea();
        outputArea.setEditable(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(outputArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, optionsLabel, flattenIncludesCheck,
                flattenImportsCheck, actionRow, scrollPane);
    }

    /**
     * Sets the source XSD file to flatten.
     */
    public void setSourceFile(File file) {
        this.sourceXsdFile = file;
    }

    /**
     * Flattens the schema.
     */
    public void flattenSchema() {
        if (sourceXsdFile == null || !sourceXsdFile.exists()) {
            statusLabel.setText("No XSD file loaded");
            return;
        }

        statusLabel.setText("Flattening...");
        progressIndicator.setVisible(true);

        Thread worker = new Thread(() -> {
            try {
                XsdParsingService parsingService = new XsdParsingServiceImpl();

                XsdParseOptions.IncludeMode includeMode = flattenIncludesCheck.isSelected()
                        ? XsdParseOptions.IncludeMode.FLATTEN
                        : XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE;

                XsdParseOptions options = XsdParseOptions.builder()
                        .includeMode(includeMode)
                        .resolveImports(flattenImportsCheck.isSelected())
                        .build();

                var parsed = parsingService.parse(sourceXsdFile.toPath(), options);
                XsdSchema schema = parsingService.toXsdModel(parsed);

                XsdSerializer serializer = new XsdSerializer();
                String flattened = serializer.serialize(schema);

                // Pretty-print
                try {
                    flattened = XmlService.prettyFormat(flattened, 4);
                } catch (Exception ignored) {
                    // Use raw if formatting fails
                }

                String result = flattened;
                Platform.runLater(() -> {
                    outputArea.replaceText(result);
                    statusLabel.setText("Flattened successfully");
                    progressIndicator.setVisible(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    outputArea.replaceText("<!-- Error: " + e.getMessage() + " -->");
                    statusLabel.setText("Error: " + e.getMessage());
                    progressIndicator.setVisible(false);
                });
                logger.error("Failed to flatten schema: {}", e.getMessage());
            }
        }, "Schema-Flatten-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Saves the flattened schema to file.
     */
    public void saveFlattenedSchema() {
        String content = outputArea.getText();
        if (content == null || content.trim().isEmpty()) {
            statusLabel.setText("Nothing to save");
            return;
        }

        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save Flattened Schema");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XSD Files", "*.xsd"));
        if (sourceXsdFile != null) {
            fc.setInitialDirectory(sourceXsdFile.getParentFile());
            String baseName = sourceXsdFile.getName().replaceFirst("\\.xsd$", "");
            fc.setInitialFileName(baseName + "_flattened.xsd");
        }

        File file = fc.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                statusLabel.setText("Saved: " + file.getName());
            } catch (Exception e) {
                statusLabel.setText("Save failed: " + e.getMessage());
            }
        }
    }
}
