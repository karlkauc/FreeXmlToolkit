package org.fxt.freexmltoolkit.demo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlGridView;

/**
 * Simplified demo to test XmlGridView directly.
 */
public class SimpleXmlGridDemo extends Application {

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <catalog>
                <product id="1">
                    <name>Laptop</name>
                    <price>999.99</price>
                </product>
                <product id="2">
                    <name>Mouse</name>
                    <price>19.99</price>
                </product>
                <product id="3">
                    <name>Keyboard</name>
                    <price>49.99</price>
                </product>
            </catalog>
            """;

    @Override
    public void start(Stage primaryStage) {
        // Create context
        XmlEditorContext context = new XmlEditorContext();

        try {
            context.loadDocumentFromString(SAMPLE_XML);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create grid view
        XmlGridView gridView = new XmlGridView(context);

        // Toolbar
        ToolBar toolbar = new ToolBar();
        Button loadBtn = new Button("Load Products");
        loadBtn.setOnAction(e -> {
            if (context.getDocument() != null && context.getDocument().getChildCount() > 0) {
                XmlNode root = context.getDocument().getChildren().get(0);
                if (root instanceof XmlElement) {
                    XmlElement catalogElement = (XmlElement) root;
                    System.out.println("Loading products into grid...");
                    gridView.loadRepeatingElements(catalogElement, "product");
                    System.out.println("Grid loaded!");
                }
            }
        });
        toolbar.getItems().add(loadBtn);

        // Layout
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(gridView);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Simple Grid Demo");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Auto-load on startup
        loadBtn.fire();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
