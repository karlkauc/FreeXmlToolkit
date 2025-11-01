package org.fxt.freexmltoolkit.controls.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Comprehensive help dialog for the XSD Panel.
 * Provides documentation for all features, with special focus on XSD 1.1 assertions.
 */
public class XsdHelpDialog extends Dialog<Void> {

    private static final String TITLE_STYLE = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-fill: #2c3e50;";
    private static final String SUBTITLE_STYLE = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-fill: #34495e;";
    private static final String CODE_STYLE = "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
            "-fx-background-color: #f5f5f5; -fx-padding: 4px 8px; " +
            "-fx-border-color: #e0e0e0; -fx-border-radius: 3px; -fx-background-radius: 3px;";
    private static final String SECTION_STYLE = "-fx-background-color: white; " +
            "-fx-border-color: #e0e0e0; -fx-border-width: 1px; " +
            "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 15px;";

    public XsdHelpDialog() {
        setTitle("XSD Panel - User Guide");
        setHeaderText("Comprehensive Guide to XSD Schema Editing");
        setGraphic(new FontIcon("bi-question-circle"));

        // Create content
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(900, 650);

        // Add tabs
        tabPane.getTabs().addAll(
                createOverviewTab(),
                createBasicOperationsTab(),
                createAssertionsTab(),
                createXsd11FeaturesTab(),
                createKeyboardShortcutsTab()
        );

        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefSize(950, 700);
    }

    private Tab createOverviewTab() {
        Tab tab = new Tab("Overview");
        tab.setGraphic(new FontIcon("bi-info-circle"));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f9f9f9;");

        // Welcome section
        content.getChildren().add(createSection(
                "Welcome to the XSD Editor",
                "The XSD Panel provides a powerful visual editor for XML Schema (XSD) files. " +
                        "This tool supports both XSD 1.0 and XSD 1.1 standards, including advanced features " +
                        "like assertions, type alternatives, and open content models."
        ));

        // Key Features
        VBox featuresBox = createSection("Key Features", "");
        VBox features = new VBox(10);
        features.getChildren().addAll(
                createBulletPoint("Visual Schema Tree", "Navigate and edit your schema structure visually"),
                createBulletPoint("XSD 1.1 Support", "Use modern features like assertions and type alternatives"),
                createBulletPoint("Live Validation", "Instant feedback on schema validity"),
                createBulletPoint("Sample Data Generation", "Generate valid XML instances from your schema"),
                createBulletPoint("Documentation Generation", "Create comprehensive HTML/SVG documentation"),
                createBulletPoint("Context-Sensitive Help", "Inline help and autocomplete suggestions")
        );
        featuresBox.getChildren().add(features);
        content.getChildren().add(featuresBox);

        // Quick Start
        content.getChildren().add(createSection(
                "Quick Start",
                "1. Load or create an XSD file using File → Open or File → New\n" +
                        "2. Use the tree view to navigate your schema structure\n" +
                        "3. Right-click on elements to access editing operations\n" +
                        "4. Use the toolbar buttons for common operations\n" +
                        "5. Save your changes using File → Save or Ctrl+S"
        ));

        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createBasicOperationsTab() {
        Tab tab = new Tab("Basic Operations");
        tab.setGraphic(new FontIcon("bi-pencil-square"));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f9f9f9;");

        // Adding Elements
        VBox addSection = createSection("Adding Elements", "");
        VBox addSteps = new VBox(10);
        addSteps.getChildren().addAll(
                createBulletPoint("Add Child Element",
                        "Right-click on a complexType or element → 'Add Element'\n" +
                                "Specify name, type, and cardinality (minOccurs/maxOccurs)"),
                createBulletPoint("Add Attribute",
                        "Right-click on a complexType → 'Add Attribute'\n" +
                                "Define name, type, and usage (required/optional)"),
                createBulletPoint("Add Complex Type",
                        "Right-click on schema root → 'Add Complex Type'\n" +
                                "Create reusable type definitions"),
                createBulletPoint("Add Simple Type",
                        "Right-click on schema root → 'Add Simple Type'\n" +
                                "Define custom data types with restrictions")
        );
        addSection.getChildren().add(addSteps);
        content.getChildren().add(addSection);

        // Editing
        VBox editSection = createSection("Editing Elements", "");
        VBox editSteps = new VBox(10);
        editSteps.getChildren().addAll(
                createBulletPoint("Rename", "Right-click → 'Rename' or F2"),
                createBulletPoint("Change Type", "Right-click → 'Change Type'"),
                createBulletPoint("Edit Properties", "Right-click → 'Properties' to modify minOccurs, maxOccurs, etc."),
                createBulletPoint("Add Documentation", "Right-click → 'Add Documentation' to add xs:annotation")
        );
        editSection.getChildren().add(editSteps);
        content.getChildren().add(editSection);

        // Deleting
        content.getChildren().add(createSection(
                "Deleting Elements",
                "Right-click on any element → 'Delete' or press Delete key\n" +
                        "Use Undo (Ctrl+Z) to revert accidental deletions"
        ));

        // Restrictions and Enumerations
        VBox restrictSection = createSection("Adding Restrictions", "");
        VBox restrictSteps = new VBox(10);
        restrictSteps.getChildren().addAll(
                createBulletPoint("Enumerations",
                        "Right-click on element → 'Add Enumeration'\n" +
                                "Define allowed values (e.g., 'red', 'green', 'blue')"),
                createBulletPoint("Patterns",
                        "Add regex patterns for string validation\n" +
                                "Example: [A-Z]{2}[0-9]{4} for country codes"),
                createBulletPoint("Min/Max Length",
                        "Set string length constraints"),
                createBulletPoint("Min/Max Value",
                        "Set numeric range constraints")
        );
        restrictSection.getChildren().add(restrictSteps);
        content.getChildren().add(restrictSection);

        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createAssertionsTab() {
        Tab tab = new Tab("Assertions (XSD 1.1)");
        tab.setGraphic(new FontIcon("bi-check-circle"));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f9f9f9;");

        // Introduction
        content.getChildren().add(createSection(
                "What are Assertions?",
                "Assertions are an XSD 1.1 feature that allows you to define complex validation rules " +
                        "using XPath 2.0 expressions. They go beyond simple facets (like minLength or pattern) " +
                        "and enable cross-field validation, conditional constraints, and business logic validation."
        ));

        // Two types
        VBox typesSection = createSection("Two Types of Assertions", "");
        VBox types = new VBox(15);

        // ComplexType assertions
        VBox complexBox = new VBox(10);
        Text complexTitle = new Text("1. ComplexType Assertions");
        complexTitle.setStyle(SUBTITLE_STYLE);
        complexBox.getChildren().add(complexTitle);
        complexBox.getChildren().add(new Text(
                "Validate relationships between child elements within a complexType.\n" +
                        "The XPath context is the element itself, allowing access to all child elements."
        ));
        complexBox.getChildren().add(createCodeBlock(
                "<!-- Example: Validate that discount is less than price -->\n" +
                        "<xs:complexType name=\"ProductType\">\n" +
                        "  <xs:sequence>\n" +
                        "    <xs:element name=\"price\" type=\"xs:decimal\"/>\n" +
                        "    <xs:element name=\"discount\" type=\"xs:decimal\"/>\n" +
                        "  </xs:sequence>\n" +
                        "  <xs:assert test=\"price > discount\"/>\n" +
                        "</xs:complexType>"
        ));
        types.getChildren().add(complexBox);

        // SimpleType assertions
        VBox simpleBox = new VBox(10);
        Text simpleTitle = new Text("2. SimpleType Assertions (in restrictions)");
        simpleTitle.setStyle(SUBTITLE_STYLE);
        simpleBox.getChildren().add(simpleTitle);
        simpleBox.getChildren().add(new Text(
                "Validate the value itself using the $value variable.\n" +
                        "Placed within xs:restriction, after facets like minInclusive or pattern."
        ));
        simpleBox.getChildren().add(createCodeBlock(
                "<!-- Example: Validate even numbers -->\n" +
                        "<xs:simpleType name=\"EvenInteger\">\n" +
                        "  <xs:restriction base=\"xs:integer\">\n" +
                        "    <xs:minInclusive value=\"0\"/>\n" +
                        "    <xs:maxInclusive value=\"100\"/>\n" +
                        "    <xs:assert test=\"$value mod 2 = 0\"/>\n" +
                        "  </xs:restriction>\n" +
                        "</xs:simpleType>"
        ));
        types.getChildren().add(simpleBox);

        typesSection.getChildren().add(types);
        content.getChildren().add(typesSection);

        // How to add
        VBox howToSection = createSection("How to Add Assertions", "");
        VBox howTo = new VBox(10);
        howTo.getChildren().addAll(
                createBulletPoint("For ComplexTypes",
                        "Right-click on a complexType → 'Add Assertion (XSD 1.1)'\n" +
                                "Enter XPath 2.0 expression (e.g., 'price > discount')"),
                createBulletPoint("For SimpleTypes",
                        "Right-click on a simpleType → 'Add Assertion (XSD 1.1)'\n" +
                                "Enter XPath 2.0 expression using $value (e.g., '$value > 0 and $value < 100')")
        );
        howToSection.getChildren().add(howTo);
        content.getChildren().add(howToSection);

        // Common examples
        VBox examplesSection = createSection("Common Assertion Examples", "");
        VBox examples = new VBox(15);
        examples.getChildren().addAll(
                createExample("Date range validation",
                        "startDate < endDate"),
                createExample("Conditional requirement",
                        "if (status = 'active') then exists(endDate) else true()"),
                createExample("Sum validation",
                        "subtotal + tax = total"),
                createExample("String length range",
                        "string-length($value) >= 5 and string-length($value) <= 20"),
                createExample("Complex business rule",
                        "if (quantity > 100) then discount >= 10 else true()")
        );
        examplesSection.getChildren().add(examples);
        content.getChildren().add(examplesSection);

        // Important notes
        VBox notesSection = createSection("Important Notes", "");
        VBox notes = new VBox(10);
        notes.getChildren().addAll(
                createWarningPoint("Schema Version",
                        "Your schema must declare vc:minVersion=\"1.1\" to use assertions"),
                createWarningPoint("XPath 2.0",
                        "Assertions use XPath 2.0 syntax, which is more powerful than XPath 1.0"),
                createWarningPoint("Performance",
                        "Complex assertions may impact validation performance"),
                createWarningPoint("Validator Support",
                        "Ensure your XML validator supports XSD 1.1 (e.g., Saxon, Xerces 2.12+)")
        );
        notesSection.getChildren().add(notes);
        content.getChildren().add(notesSection);

        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createXsd11FeaturesTab() {
        Tab tab = new Tab("XSD 1.1 Features");
        tab.setGraphic(new FontIcon("bi-star"));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f9f9f9;");

        // Introduction
        content.getChildren().add(createSection(
                "XSD 1.1 Enhanced Features",
                "XSD 1.1 introduces several powerful features beyond XSD 1.0. " +
                        "This editor provides full support for these modern schema capabilities."
        ));

        // Assertions (already covered)
        VBox assertSection = createFeatureSection(
                "Assertions (xs:assert)",
                "XPath 2.0-based validation constraints",
                "See the 'Assertions' tab for detailed documentation"
        );
        content.getChildren().add(assertSection);

        // Type Alternatives
        VBox alternativesSection = createFeatureSection(
                "Type Alternatives (xs:alternative)",
                "Conditional type assignment based on element content",
                "Define different types based on conditions:\n\n" +
                        "<xs:element name=\"value\">\n" +
                        "  <xs:alternative test=\"@type='int'\" type=\"xs:integer\"/>\n" +
                        "  <xs:alternative test=\"@type='string'\" type=\"xs:string\"/>\n" +
                        "</xs:element>"
        );
        content.getChildren().add(alternativesSection);

        // Open Content
        VBox openContentSection = createFeatureSection(
                "Open Content (xs:openContent)",
                "Allow interspersed wildcard content in sequences",
                "Permits additional elements at specific positions while maintaining order"
        );
        content.getChildren().add(openContentSection);

        // Override
        VBox overrideSection = createFeatureSection(
                "Schema Override (xs:override)",
                "Modify imported schemas without changing the original",
                "More flexible than xs:redefine with better component resolution"
        );
        content.getChildren().add(overrideSection);

        // New built-in types
        VBox typesSection = createFeatureSection(
                "New Built-in Types",
                "Additional primitive data types",
                "• dateTimeStamp - date/time with required timezone\n" +
                        "• yearMonthDuration - duration in years and months\n" +
                        "• dayTimeDuration - duration in days, hours, minutes, seconds\n" +
                        "• precisionDecimal - arbitrary-precision decimals"
        );
        content.getChildren().add(typesSection);

        // Version Declaration
        VBox versionSection = createSection("Enabling XSD 1.1", "");
        versionSection.getChildren().add(createCodeBlock(
                "<!-- Add to your schema root element -->\n" +
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                        "           xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\"\n" +
                        "           vc:minVersion=\"1.1\"\n" +
                        "           elementFormDefault=\"qualified\">\n" +
                        "  <!-- Your schema content -->\n" +
                        "</xs:schema>"
        ));
        content.getChildren().add(versionSection);

        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    private Tab createKeyboardShortcutsTab() {
        Tab tab = new Tab("Keyboard Shortcuts");
        tab.setGraphic(new FontIcon("bi-keyboard"));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #f9f9f9;");

        // File operations
        VBox fileSection = createSection("File Operations", "");
        GridPane fileGrid = createShortcutGrid();
        fileGrid.add(new Label("Ctrl+N"), 0, 0);
        fileGrid.add(new Label("New Schema"), 1, 0);
        fileGrid.add(new Label("Ctrl+O"), 0, 1);
        fileGrid.add(new Label("Open Schema"), 1, 1);
        fileGrid.add(new Label("Ctrl+S"), 0, 2);
        fileGrid.add(new Label("Save Schema"), 1, 2);
        fileGrid.add(new Label("Ctrl+Shift+S"), 0, 3);
        fileGrid.add(new Label("Save As"), 1, 3);
        fileSection.getChildren().add(fileGrid);
        content.getChildren().add(fileSection);

        // Edit operations
        VBox editSection = createSection("Edit Operations", "");
        GridPane editGrid = createShortcutGrid();
        editGrid.add(new Label("Ctrl+Z"), 0, 0);
        editGrid.add(new Label("Undo"), 1, 0);
        editGrid.add(new Label("Ctrl+Y / Ctrl+Shift+Z"), 0, 1);
        editGrid.add(new Label("Redo"), 1, 1);
        editGrid.add(new Label("Ctrl+C"), 0, 2);
        editGrid.add(new Label("Copy"), 1, 2);
        editGrid.add(new Label("Ctrl+X"), 0, 3);
        editGrid.add(new Label("Cut"), 1, 3);
        editGrid.add(new Label("Ctrl+V"), 0, 4);
        editGrid.add(new Label("Paste"), 1, 4);
        editGrid.add(new Label("Delete"), 0, 5);
        editGrid.add(new Label("Delete selected element"), 1, 5);
        editGrid.add(new Label("F2"), 0, 6);
        editGrid.add(new Label("Rename"), 1, 6);
        editSection.getChildren().add(editGrid);
        content.getChildren().add(editSection);

        // Navigation
        VBox navSection = createSection("Navigation", "");
        GridPane navGrid = createShortcutGrid();
        navGrid.add(new Label("Ctrl+F"), 0, 0);
        navGrid.add(new Label("Find in schema"), 1, 0);
        navGrid.add(new Label("F3"), 0, 1);
        navGrid.add(new Label("Find next"), 1, 1);
        navGrid.add(new Label("↑ ↓ ← →"), 0, 2);
        navGrid.add(new Label("Navigate tree"), 1, 2);
        navSection.getChildren().add(navGrid);
        content.getChildren().add(navSection);

        // Help
        VBox helpSection = createSection("Help", "");
        GridPane helpGrid = createShortcutGrid();
        helpGrid.add(new Label("F1"), 0, 0);
        helpGrid.add(new Label("Show this help dialog"), 1, 0);
        helpSection.getChildren().add(helpGrid);
        content.getChildren().add(helpSection);

        scrollPane.setContent(content);
        tab.setContent(scrollPane);
        return tab;
    }

    // Helper methods

    private VBox createSection(String title, String content) {
        VBox section = new VBox(10);
        section.setStyle(SECTION_STYLE);

        Text titleText = new Text(title);
        titleText.setStyle(TITLE_STYLE);
        section.getChildren().add(titleText);

        if (content != null && !content.isEmpty()) {
            Text contentText = new Text(content);
            contentText.setWrappingWidth(800);
            section.getChildren().add(contentText);
        }

        return section;
    }

    private VBox createFeatureSection(String title, String subtitle, String content) {
        VBox section = createSection(title, "");

        Text subtitleText = new Text(subtitle);
        subtitleText.setStyle("-fx-font-style: italic; -fx-fill: #7f8c8d;");
        section.getChildren().add(subtitleText);

        if (content != null && !content.isEmpty()) {
            if (content.contains("<xs:")) {
                section.getChildren().add(createCodeBlock(content));
            } else {
                Text contentText = new Text(content);
                contentText.setWrappingWidth(800);
                section.getChildren().add(contentText);
            }
        }

        return section;
    }

    private HBox createBulletPoint(String title, String description) {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.TOP_LEFT);

        FontIcon bullet = new FontIcon("bi-circle-fill");
        bullet.setIconSize(8);
        bullet.setStyle("-fx-fill: #3498db;");

        VBox textBox = new VBox(5);
        Text titleText = new Text(title);
        titleText.setStyle("-fx-font-weight: bold;");
        textBox.getChildren().add(titleText);

        if (description != null && !description.isEmpty()) {
            Text descText = new Text(description);
            descText.setWrappingWidth(750);
            textBox.getChildren().add(descText);
        }

        hbox.getChildren().addAll(bullet, textBox);
        return hbox;
    }

    private HBox createWarningPoint(String title, String description) {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.TOP_LEFT);
        hbox.setStyle("-fx-background-color: #fff3cd; -fx-padding: 10px; " +
                "-fx-border-color: #ffc107; -fx-border-width: 1px; -fx-border-radius: 3px; " +
                "-fx-background-radius: 3px;");

        FontIcon icon = new FontIcon("bi-exclamation-triangle");
        icon.setIconSize(16);
        icon.setStyle("-fx-fill: #856404;");

        VBox textBox = new VBox(5);
        Text titleText = new Text(title);
        titleText.setStyle("-fx-font-weight: bold; -fx-fill: #856404;");
        textBox.getChildren().add(titleText);

        if (description != null && !description.isEmpty()) {
            Text descText = new Text(description);
            descText.setWrappingWidth(750);
            textBox.getChildren().add(descText);
        }

        hbox.getChildren().addAll(icon, textBox);
        return hbox;
    }

    private VBox createExample(String title, String code) {
        VBox example = new VBox(5);
        Text titleText = new Text("▸ " + title);
        titleText.setStyle("-fx-font-weight: bold;");
        example.getChildren().add(titleText);

        Label codeLabel = new Label(code);
        codeLabel.setStyle(CODE_STYLE);
        codeLabel.setWrapText(true);
        codeLabel.setMaxWidth(780);
        example.getChildren().add(codeLabel);

        return example;
    }

    private TextFlow createCodeBlock(String code) {
        TextFlow textFlow = new TextFlow();
        Text text = new Text(code);
        text.setFont(Font.font("Consolas", 12));
        textFlow.getChildren().add(text);
        textFlow.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15px; " +
                "-fx-border-color: #e0e0e0; -fx-border-width: 1px; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px;");
        textFlow.setMaxWidth(800);
        return textFlow;
    }

    private GridPane createShortcutGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(200);
        col1.setPrefWidth(200);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setMinWidth(400);
        col2.setPrefWidth(400);
        grid.getColumnConstraints().addAll(col1, col2);

        return grid;
    }
}
