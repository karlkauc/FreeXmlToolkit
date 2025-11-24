package org.fxt.freexmltoolkit.demo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;

import java.io.File;

/**
 * Demo for the new Canvas-based XML view with embedded tables.
 * Uses the FundsXML_428.xml test file to demonstrate large XML handling.
 */
public class XmlCanvasViewDemo extends Application {

    // Path to the test XML file
    private static final String XML_FILE_PATH = "src/test/resources/FundsXML_428.xml";

    private XmlEditorContext context;
    private XmlCanvasView canvasView;

    @Override
    public void start(Stage primaryStage) {
        // Create editor context
        context = new XmlEditorContext();

        // Load XML from file
        try {
            File xmlFile = new File(XML_FILE_PATH);
            if (!xmlFile.exists()) {
                System.err.println("ERROR: XML file not found: " + xmlFile.getAbsolutePath());
                System.exit(1);
            }
            System.out.println("Loading XML from: " + xmlFile.getAbsolutePath());
            context.loadDocument(XML_FILE_PATH);
            System.out.println("XML loaded successfully!");
        } catch (Exception e) {
            System.err.println("ERROR loading XML: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Create canvas view
        canvasView = new XmlCanvasView(context);

        // Create toolbar
        ToolBar toolbar = new ToolBar();

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> canvasView.refresh());

        Button expandAllBtn = new Button("Expand All");
        expandAllBtn.setOnAction(e -> canvasView.expandAll());

        Button collapseAllBtn = new Button("Collapse All");
        collapseAllBtn.setOnAction(e -> canvasView.collapseAll());

        toolbar.getItems().addAll(refreshBtn, expandAllBtn, collapseAllBtn);

        // Main layout
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(canvasView);

        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setTitle("XML Canvas View Demo - FundsXML (2.1MB, XMLSpy Style)");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
