package org.fxt.freexmltoolkit.controls.unified.panels;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Embedded XSLT development panel for the XML Unified Editor.
 * Provides a split-pane layout: XSLT editor on the left, output on the right.
 * Uses the current XML file as input for transformations.
 */
public class XmlEmbeddedXsltPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlEmbeddedXsltPanel.class);

    private final XmlCodeEditorV2 xsltEditor;
    private final CodeArea resultArea;
    private final WebView htmlPreview;
    private final Label statusLabel;
    private final ComboBox<String> outputFormatCombo;
    private final ToggleButton liveTransformToggle;

    private final XsltTransformationEngine xsltEngine;
    private final ExecutorService executorService;

    private java.util.function.Supplier<String> xmlContentProvider;
    private Runnable onCloseRequested;

    public XmlEmbeddedXsltPanel() {
        setSpacing(4);
        setPadding(new Insets(4));
        setMinHeight(150);
        setPrefHeight(300);

        this.xsltEngine = new XsltTransformationEngine();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EmbeddedXslt-Worker");
            t.setDaemon(true);
            return t;
        });

        // Create XSLT editor
        this.xsltEditor = XmlCodeEditorV2Factory.createWithoutSchema();
        xsltEditor.setText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="xml" indent="yes"/>

                    <xsl:template match="/">
                        <xsl:copy-of select="."/>
                    </xsl:template>
                </xsl:stylesheet>
                """);

        // Create result area
        this.resultArea = new CodeArea();
        resultArea.setEditable(false);

        // Create HTML preview
        this.htmlPreview = new WebView();

        // Status and controls
        this.statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        this.outputFormatCombo = new ComboBox<>();
        outputFormatCombo.getItems().addAll("XML", "HTML", "Text");
        outputFormatCombo.setValue("XML");

        this.liveTransformToggle = new ToggleButton("Live");
        liveTransformToggle.setStyle("-fx-font-size: 11px;");

        // Header
        HBox header = createHeader();

        // Main split pane: XSLT editor | Output
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.5);

        // XSLT editor side
        VBox xsltSide = new VBox(4);
        Label xsltLabel = new Label("XSLT Stylesheet");
        xsltLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        HBox xsltToolbar = new HBox(4);
        xsltToolbar.setAlignment(Pos.CENTER_LEFT);
        Button loadXsltBtn = new Button("Load");
        loadXsltBtn.setStyle("-fx-font-size: 11px;");
        loadXsltBtn.setOnAction(e -> loadXsltFile());
        xsltToolbar.getChildren().addAll(xsltLabel, loadXsltBtn);

        VBox.setVgrow(xsltEditor, Priority.ALWAYS);
        xsltSide.getChildren().addAll(xsltToolbar, xsltEditor);

        // Output side
        TabPane outputTabs = new TabPane();
        outputTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab resultTab = new Tab("Result");
        resultTab.setContent(new VirtualizedScrollPane<>(resultArea));

        Tab previewTab = new Tab("Preview");
        previewTab.setContent(htmlPreview);

        outputTabs.getTabs().addAll(resultTab, previewTab);

        splitPane.getItems().addAll(xsltSide, outputTabs);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        getChildren().addAll(header, splitPane);

        // Live transform listener
        xsltEditor.getCodeArea().textProperty().addListener((obs, oldText, newText) -> {
            if (liveTransformToggle.isSelected()) {
                executeTransform();
            }
        });
    }

    private HBox createHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 4, 2, 4));

        FontIcon icon = new FontIcon("bi-arrow-repeat");
        icon.setIconSize(14);
        Label title = new Label("XSLT Development");
        title.setGraphic(icon);
        title.setStyle("-fx-font-weight: bold;");

        Button runBtn = new Button("Transform");
        runBtn.setStyle("-fx-font-size: 11px;");
        runBtn.setGraphic(createIcon("bi-play-fill", 12));
        runBtn.setOnAction(e -> executeTransform());

        Label formatLabel = new Label("Output:");
        formatLabel.setStyle("-fx-font-size: 11px;");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button();
        closeBtn.setGraphic(createIcon("bi-x-lg", 14));
        closeBtn.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.run();
        });
        closeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        header.getChildren().addAll(title, runBtn, liveTransformToggle,
                formatLabel, outputFormatCombo, statusLabel, spacer, closeBtn);

        return header;
    }

    /**
     * Executes the XSLT transformation.
     */
    public void executeTransform() {
        if (xmlContentProvider == null) {
            statusLabel.setText("No XML source");
            return;
        }

        String xmlContent = xmlContentProvider.get();
        String xsltContent = xsltEditor.getText();

        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            statusLabel.setText("Empty XML");
            return;
        }
        if (xsltContent == null || xsltContent.trim().isEmpty()) {
            statusLabel.setText("Empty XSLT");
            return;
        }

        statusLabel.setText("Transforming...");

        executorService.submit(() -> {
            try {
                XsltTransformationEngine.OutputFormat format = switch (outputFormatCombo.getValue()) {
                    case "HTML" -> XsltTransformationEngine.OutputFormat.HTML;
                    case "Text" -> XsltTransformationEngine.OutputFormat.TEXT;
                    default -> XsltTransformationEngine.OutputFormat.XML;
                };

                Map<String, Object> params = Map.of();
                var transformResult = xsltEngine.transform(xmlContent, xsltContent, params, format);
                String output = transformResult.getOutputContent();
                long elapsed = transformResult.getExecutionTime();

                Platform.runLater(() -> {
                    resultArea.replaceText(output != null ? output : "");
                    statusLabel.setText(String.format("Done (%dms)", elapsed));

                    // Update HTML preview
                    if ("HTML".equals(outputFormatCombo.getValue()) && output != null) {
                        htmlPreview.getEngine().loadContent(output);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.replaceText("Error: " + e.getMessage());
                    statusLabel.setText("Error");
                });
                logger.warn("XSLT transformation failed: {}", e.getMessage());
            }
        });
    }

    private void loadXsltFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load XSLT Stylesheet");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                xsltEditor.setText(content);
            } catch (Exception e) {
                logger.error("Failed to load XSLT: {}", e.getMessage());
            }
        }
    }

    /**
     * Sets the XML content provider (e.g., from the active XML tab).
     */
    public void setXmlContentProvider(java.util.function.Supplier<String> provider) {
        this.xmlContentProvider = provider;
    }

    public void setOnCloseRequested(Runnable handler) {
        this.onCloseRequested = handler;
    }

    /**
     * Cleans up resources.
     */
    public void dispose() {
        executorService.shutdownNow();
    }

    private FontIcon createIcon(String literal, int size) {
        FontIcon fi = new FontIcon(literal);
        fi.setIconSize(size);
        return fi;
    }
}
