package org.fxt.freexmltoolkit.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlHybridView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlTextView;

import java.io.File;
import java.nio.file.Files;

/**
 * Demo application for XML Editor V2 graphical views:
 * - Text View (syntax highlighting)
 * - Hybrid View (Tree + Grid in XMLSpy style)
 * - Tree shows hierarchical XML structure
 * - Grid automatically appears for repeating elements (e.g., multiple &lt;product&gt; nodes)
 */
public class XmlEditorViewsDemo extends Application {

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Sample Products Catalog -->
            <catalog>
                <product id="1">
                    <name>Laptop</name>
                    <price>999.99</price>
                    <category>Electronics</category>
                    <inStock>true</inStock>
                </product>
                <product id="2">
                    <name>Mouse</name>
                    <price>19.99</price>
                    <category>Electronics</category>
                    <inStock>true</inStock>
                </product>
                <product id="3">
                    <name>Keyboard</name>
                    <price>49.99</price>
                    <category>Electronics</category>
                    <inStock>false</inStock>
                </product>
                <product id="4">
                    <name>Monitor</name>
                    <price>299.99</price>
                    <category>Electronics</category>
                    <inStock>true</inStock>
                </product>
            </catalog>
            """;

    private XmlEditorContext context;
    private XmlTextView textView;
    private XmlHybridView hybridView;

    @Override
    public void start(Stage primaryStage) {
        // Create editor context
        context = new XmlEditorContext();

        // Load sample XML
        try {
            context.loadDocumentFromString(SAMPLE_XML);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create views
        textView = new XmlTextView(context);
        hybridView = new XmlHybridView(context);

        // Create tab pane with different views
        TabPane tabPane = new TabPane();
        tabPane.setSide(Side.TOP);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Text tab
        Tab textTab = new Tab("Text");
        textTab.setContent(textView);

        // Hybrid Tree/Grid tab
        Tab hybridTab = new Tab("Tree/Grid");
        hybridTab.setContent(hybridView);

        tabPane.getTabs().addAll(textTab, hybridTab);

        // Create toolbar
        ToolBar toolbar = createToolbar();

        // Main layout
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("XML Editor V2 - Views Demo");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Select hybrid tab by default
        tabPane.getSelectionModel().select(hybridTab);

        // Note: Grid view should appear automatically when catalog node is selected
        // The automatic detection is handled by XmlHybridView.onDocumentChanged()
    }

    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();

        Button newBtn = new Button("New");
        newBtn.setOnAction(e -> {
            try {
                context.loadDocumentFromString(SAMPLE_XML);
            } catch (Exception ex) {
                showError("Error creating new document", ex.getMessage());
            }
        });

        Button openBtn = new Button("Open...");
        openBtn.setOnAction(e -> openFile());

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> saveFile());

        Separator sep1 = new Separator(Orientation.VERTICAL);

        Button undoBtn = new Button("Undo");
        undoBtn.setOnAction(e -> context.undo());
        undoBtn.disableProperty().setValue(!context.canUndo());
        context.addPropertyChangeListener("canUndo", evt ->
                undoBtn.setDisable(!context.canUndo()));

        Button redoBtn = new Button("Redo");
        redoBtn.setOnAction(e -> context.redo());
        redoBtn.disableProperty().setValue(!context.canRedo());
        context.addPropertyChangeListener("canRedo", evt ->
                redoBtn.setDisable(!context.canRedo()));

        Separator sep2 = new Separator(Orientation.VERTICAL);

        Button formatBtn = new Button("Format XML");
        formatBtn.setOnAction(e -> textView.formatXml());

        Button refreshBtn = new Button("Refresh All");
        refreshBtn.setOnAction(e -> hybridView.refresh());

        toolbar.getItems().addAll(
                newBtn, openBtn, saveBtn, sep1,
                undoBtn, redoBtn, sep2,
                formatBtn, refreshBtn
        );

        return toolbar;
    }

    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open XML File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                context.loadDocument(file.getAbsolutePath());
            } catch (Exception e) {
                showError("Error opening file", e.getMessage());
            }
        }
    }

    private void saveFile() {
        String filePath = context.getFilePath();
        if (filePath == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XML File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XML Files", "*.xml")
            );

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                filePath = file.getAbsolutePath();
            }
        }

        if (filePath != null) {
            try {
                context.saveAs(filePath);
                showInfo("File saved successfully", "File: " + filePath);
            } catch (Exception e) {
                showError("Error saving file", e.getMessage());
            }
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
