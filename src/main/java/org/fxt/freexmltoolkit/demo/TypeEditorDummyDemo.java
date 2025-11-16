package org.fxt.freexmltoolkit.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Demo application to showcase the Type Editor Dummy UI.
 *
 * This is a standalone JavaFX application that demonstrates the Type Editor
 * UI structure with dummy data before the actual implementation.
 *
 * HOW TO RUN:
 * Run this class as a Java Application (main method).
 *
 * @since 2.0
 */
public class TypeEditorDummyDemo extends Application {

    private TypeEditorTabManager typeEditorManager;
    private TabPane mainTabPane;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top: Info banner
        VBox infoBanner = createInfoBanner();
        root.setTop(infoBanner);

        // Center: Tab pane
        mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        root.setCenter(mainTabPane);

        // Bottom: Control panel
        HBox controls = createControlPanel();
        root.setBottom(controls);

        // Create test schema
        XsdSchema testSchema = new XsdSchema();
        testSchema.setTargetNamespace("http://demo.example.com");

        // Initialize manager
        typeEditorManager = new TypeEditorTabManager(mainTabPane, testSchema);

        // Create scene
        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Type Editor - Dummy UI Demo");
        primaryStage.show();

        // Show welcome message
        showWelcomeTab();
    }

    /**
     * Creates the info banner at the top.
     */
    private VBox createInfoBanner() {
        VBox banner = new VBox(5);
        banner.setStyle("-fx-background-color: #d1ecf1; -fx-padding: 10;");

        Label title = new Label("🎨 XSD Type Editor - DUMMY UI DEMO (Phase 0)");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0c5460;");

        Label info = new Label(
                "This is a visualization of the Type Editor UI structure. " +
                "All buttons and interactions are placeholders. " +
                "Use the buttons below to open different editor tabs."
        );
        info.setStyle("-fx-text-fill: #0c5460;");
        info.setWrapText(true);

        banner.getChildren().addAll(title, info);
        return banner;
    }

    /**
     * Creates the control panel with buttons to open different tabs.
     */
    private HBox createControlPanel() {
        HBox controls = new HBox(15);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6;");

        Label label = new Label("Open Tab:");
        label.setStyle("-fx-font-weight: bold;");

        // Button: ComplexType Editor
        Button openComplexTypeBtn = new Button("📦 ComplexType: AddressType");
        openComplexTypeBtn.setTooltip(new Tooltip("Opens a ComplexType editor tab (like FundsXML AddressType)"));
        openComplexTypeBtn.setOnAction(e -> openComplexTypeExample());

        // Button: Another ComplexType
        Button openComplexType2Btn = new Button("📦 ComplexType: AmountType");
        openComplexType2Btn.setTooltip(new Tooltip("Opens another ComplexType editor tab"));
        openComplexType2Btn.setOnAction(e -> openComplexType2Example());

        // Button: SimpleType Editor
        Button openSimpleTypeBtn = new Button("📄 SimpleType: ISINType");
        openSimpleTypeBtn.setTooltip(new Tooltip("Opens a SimpleType editor tab (like FundsXML ISINType)"));
        openSimpleTypeBtn.setOnAction(e -> openSimpleTypeExample());

        // Button: Another SimpleType
        Button openSimpleType2Btn = new Button("📄 SimpleType: EmailAddressType");
        openSimpleType2Btn.setTooltip(new Tooltip("Opens another SimpleType editor tab"));
        openSimpleType2Btn.setOnAction(e -> openSimpleType2Example());

        // Button: SimpleTypes List
        Button openListBtn = new Button("📋 SimpleTypes List");
        openListBtn.setTooltip(new Tooltip("Opens the SimpleTypes overview tab"));
        openListBtn.setOnAction(e -> typeEditorManager.openSimpleTypesListTab());

        controls.getChildren().addAll(
                label,
                openComplexTypeBtn,
                openComplexType2Btn,
                openSimpleTypeBtn,
                openSimpleType2Btn,
                openListBtn
        );

        return controls;
    }

    /**
     * Shows a welcome tab with instructions.
     */
    private void showWelcomeTab() {
        VBox welcome = new VBox(20);
        welcome.setPadding(new Insets(50));
        welcome.setStyle("-fx-alignment: center;");

        Label title = new Label("Welcome to Type Editor Dummy UI");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Phase 0 - UI Structure Visualization");
        subtitle.setStyle("-fx-font-size: 16px; -fx-text-fill: #6c757d;");

        Label instructions = new Label(
                "This demo shows the Type Editor UI structure with dummy data.\n\n" +
                "Use the buttons at the bottom to open different editor tabs:\n" +
                "• ComplexType Editors - Show graphic editor for complex types\n" +
                "• SimpleType Editors - Show tabbed editor for simple types\n" +
                "• SimpleTypes List - Show overview of all simple types\n\n" +
                "All interactions are disabled (this is just a visual preview)."
        );
        instructions.setWrapText(true);
        instructions.setMaxWidth(600);
        instructions.setStyle("-fx-font-size: 14px; -fx-text-fill: #495057;");

        Label note = new Label(
                "ℹ️ Note: This is DUMMY UI only. Real implementation starts in Phase 1."
        );
        note.setStyle("-fx-font-size: 12px; -fx-text-fill: #856404; " +
                     "-fx-background-color: #fff3cd; -fx-padding: 10; -fx-border-radius: 5;");

        welcome.getChildren().addAll(title, subtitle, instructions, note);

        javafx.scene.control.Tab welcomeTab = new javafx.scene.control.Tab("Welcome");
        welcomeTab.setContent(welcome);
        welcomeTab.setClosable(false);
        mainTabPane.getTabs().add(welcomeTab);
    }

    /**
     * Opens a ComplexType example (AddressType from FundsXML).
     */
    private void openComplexTypeExample() {
        XsdComplexType addressType = new XsdComplexType("AddressType");
        // Note: In real implementation, this would have children (sequence, elements, etc.)
        typeEditorManager.openComplexTypeTab(addressType);
    }

    /**
     * Opens another ComplexType example (AmountType).
     */
    private void openComplexType2Example() {
        XsdComplexType amountType = new XsdComplexType("AmountType");
        typeEditorManager.openComplexTypeTab(amountType);
    }

    /**
     * Opens a SimpleType example (ISINType from FundsXML).
     */
    private void openSimpleTypeExample() {
        XsdSimpleType isinType = new XsdSimpleType("ISINType");
        isinType.setBase("xs:string");
        typeEditorManager.openSimpleTypeTab(isinType);
    }

    /**
     * Opens another SimpleType example (EmailAddressType).
     */
    private void openSimpleType2Example() {
        XsdSimpleType emailType = new XsdSimpleType("EmailAddressType");
        emailType.setBase("xs:string");
        typeEditorManager.openSimpleTypeTab(emailType);
    }

    /**
     * Main method to launch the demo.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
