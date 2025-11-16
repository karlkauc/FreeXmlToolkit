package org.fxt.freexmltoolkit.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Integration test for Type Editor in main application.
 * This demo shows how the Type Editor integrates with the main XSD Controller.
 */
public class TypeEditorIntegrationTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create TabPane for type editor
        TabPane typeEditorTabPane = new TabPane();
        typeEditorTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Create a test schema
        XsdSchema testSchema = new XsdSchema();
        testSchema.setTargetNamespace("http://test.example.com");

        // Create TypeEditorTabManager
        TypeEditorTabManager manager = new TypeEditorTabManager(typeEditorTabPane, testSchema);

        // Create test buttons
        Button btnComplexType1 = new Button("Open ComplexType: AddressType");
        btnComplexType1.setOnAction(e -> {
            XsdComplexType type = new XsdComplexType("AddressType");
            manager.openComplexTypeTab(type);
        });

        Button btnComplexType2 = new Button("Open ComplexType: AmountType");
        btnComplexType2.setOnAction(e -> {
            XsdComplexType type = new XsdComplexType("AmountType");
            manager.openComplexTypeTab(type);
        });

        Button btnSimpleType1 = new Button("Open SimpleType: ISINType");
        btnSimpleType1.setOnAction(e -> {
            XsdSimpleType type = new XsdSimpleType("ISINType");
            manager.openSimpleTypeTab(type);
        });

        Button btnSimpleType2 = new Button("Open SimpleType: EmailAddressType");
        btnSimpleType2.setOnAction(e -> {
            XsdSimpleType type = new XsdSimpleType("EmailAddressType");
            manager.openSimpleTypeTab(type);
        });

        Button btnSimpleTypesList = new Button("Open SimpleTypes List");
        btnSimpleTypesList.setOnAction(e -> manager.openSimpleTypesListTab());

        Button btnSaveAll = new Button("Save All Tabs");
        btnSaveAll.setOnAction(e -> {
            boolean success = manager.saveAllTabs();
            System.out.println("Save all: " + (success ? "SUCCESS" : "FAILED"));
        });

        Button btnCloseAll = new Button("Close All Type Tabs");
        btnCloseAll.setOnAction(e -> {
            boolean success = manager.closeAllTypeTabs();
            System.out.println("Close all: " + (success ? "SUCCESS" : "CANCELLED"));
        });

        // Info label
        Label info = new Label("Type Editor Integration Test - Click buttons to open type editors");
        info.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label info2 = new Label("Features: Duplicate prevention, Dirty tracking, Unsaved changes dialog");
        info2.setStyle("-fx-text-fill: gray;");

        // Layout
        VBox controls = new VBox(10,
                info,
                info2,
                new Label("ComplexTypes:"),
                btnComplexType1,
                btnComplexType2,
                new Label("SimpleTypes:"),
                btnSimpleType1,
                btnSimpleType2,
                new Label("List:"),
                btnSimpleTypesList,
                new Label("Actions:"),
                btnSaveAll,
                btnCloseAll
        );
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-background-color: #f5f5f5;");

        VBox root = new VBox(controls, typeEditorTabPane);
        VBox.setVgrow(typeEditorTabPane, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Type Editor Integration Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
