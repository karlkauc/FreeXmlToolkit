package org.fxt.freexmltoolkit.controls.v2.editor;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;

/**
 * Demo application for XmlCodeEditorV2 showcasing:
 * - Line numbers
 * - Syntax highlighting
 * - Code folding
 * - Status line
 */
public class XmlCodeEditorV2Demo extends Application {

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Sample XML Document -->
            <bookstore>
                <book category="cooking">
                    <title lang="en">Everyday Italian</title>
                    <author>Giada De Laurentiis</author>
                    <year>2005</year>
                    <price>30.00</price>
                </book>
                <book category="children">
                    <title lang="en">Harry Potter</title>
                    <author>J K. Rowling</author>
                    <year>2005</year>
                    <price>29.99</price>
                </book>
                <book category="web">
                    <title lang="en">Learning XML</title>
                    <author>Erik T. Ray</author>
                    <year>2003</year>
                    <price>39.95</price>
                    <description><![CDATA[
                        A comprehensive guide to XML.
                        Multiple lines of content here.
                    ]]></description>
                </book>
            </bookstore>
            """;

    private static final String INVALID_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Sample XML with Error -->
            <bookstore>
                <book category="cooking">
                    <title lang="en">Everyday Italian
                    <author>Giada De Laurentiis</author>
                    <year>2005</year>
                    <price>30.00</price>
                </book>
            </bookstore>
            """;

    @Override
    public void start(Stage primaryStage) {
        // Create editor with functional schema provider
        XmlSchemaProvider schemaProvider = new XmlSchemaProvider() {
            private boolean hasSchema = false;

            @Override
            public boolean hasSchema() {
                return hasSchema;
            }

            @Override
            public org.fxt.freexmltoolkit.domain.XsdDocumentationData getXsdDocumentationData() {
                return null;
            }

            @Override
            public String getXsdFilePath() {
                return hasSchema ? "demo-schema.xsd" : null;
            }

            @Override
            public org.fxt.freexmltoolkit.domain.XsdExtendedElement findBestMatchingElement(String xpath) {
                return null;
            }

            public void setHasSchema(boolean hasSchema) {
                this.hasSchema = hasSchema;
            }
        };

        XmlCodeEditorV2 editor = new XmlCodeEditorV2(schemaProvider);

        // Set sample XML
        editor.setText(SAMPLE_XML);

        // Create toolbar
        HBox toolbar = createToolbar(editor);

        // Layout
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(editor);

        // Scene
        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("XmlCodeEditorV2 Demo - Line Numbers, Syntax Highlighting & Code Folding");
        primaryStage.show();
    }

    private HBox createToolbar(XmlCodeEditorV2 editor) {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #f0f0f0;");

        // Fold All button
        Button foldAllBtn = new Button("Fold All");
        foldAllBtn.setOnAction(e -> editor.foldAll());

        // Unfold All button
        Button unfoldAllBtn = new Button("Unfold All");
        unfoldAllBtn.setOnAction(e -> editor.unfoldAll());

        // Refresh Highlighting button
        Button refreshBtn = new Button("Refresh Highlighting");
        refreshBtn.setOnAction(e -> editor.refreshHighlighting());

        // Validate button
        Button validateBtn = new Button("Validate XML");
        validateBtn.setOnAction(e -> editor.validate());

        // Clear button
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> editor.setText(""));

        // Load Sample button
        Button loadSampleBtn = new Button("Load Sample");
        loadSampleBtn.setOnAction(e -> editor.setText(SAMPLE_XML));

        // Load Invalid XML button (to demonstrate validation)
        Button loadInvalidBtn = new Button("Load Invalid XML");
        loadInvalidBtn.setOnAction(e -> {
            editor.setText(INVALID_XML);
            editor.validate();
        });
        loadInvalidBtn.setStyle("-fx-background-color: #ffcccc;");

        toolbar.getChildren().addAll(
                foldAllBtn,
                unfoldAllBtn,
                refreshBtn,
                validateBtn,
                clearBtn,
                loadSampleBtn,
                loadInvalidBtn
        );

        return toolbar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
