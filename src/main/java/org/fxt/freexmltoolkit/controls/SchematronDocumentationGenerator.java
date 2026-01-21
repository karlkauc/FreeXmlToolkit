package org.fxt.freexmltoolkit.controls;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Documentation generator for Schematron files.
 * Creates comprehensive HTML documentation from Schematron schemas.
 */
public class SchematronDocumentationGenerator extends VBox {

    private static final Logger logger = LogManager.getLogger(SchematronDocumentationGenerator.class);

    // UI Components
    private TextField schematronFileField;
    private Button browseButton;
    private CheckBox includeXPathCheckBox;
    private CheckBox includeExamplesCheckBox;
    private CheckBox includeStatisticsCheckBox;
    private ComboBox<String> formatComboBox;
    private Button generateButton;
    private Button exportButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    private WebView documentationView;

    // Services
    private final ExecutorService executor;

    // State
    private File currentSchematronFile;
    private String generatedDocumentation;
    private DocumentationFormat selectedFormat = DocumentationFormat.HTML;

    /**
     * Creates a new SchematronDocumentationGenerator instance.
     * Initializes the executor service, UI components, layout, and event handlers
     * for generating comprehensive documentation from Schematron schemas.
     */
    public SchematronDocumentationGenerator() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("SchematronDocGen-Worker");
            return t;
        });

        this.setSpacing(10);
        this.setPadding(new Insets(10));

        initializeComponents();
        layoutComponents();
        setupEventHandlers();

        logger.debug("SchematronDocumentationGenerator initialized");
    }

    /**
     * Initialize all UI components
     */
    private void initializeComponents() {
        // File selection
        schematronFileField = new TextField();
        schematronFileField.setPromptText("Select Schematron file to document...");
        schematronFileField.setEditable(false);
        schematronFileField.setPrefWidth(400);

        browseButton = new Button("Browse...");
        browseButton.setPrefWidth(80);

        // Options
        includeXPathCheckBox = new CheckBox("Include XPath Details");
        includeXPathCheckBox.setSelected(true);

        includeExamplesCheckBox = new CheckBox("Include Code Examples");
        includeExamplesCheckBox.setSelected(true);

        includeStatisticsCheckBox = new CheckBox("Include Statistics");
        includeStatisticsCheckBox.setSelected(false);

        formatComboBox = new ComboBox<>();
        formatComboBox.getItems().addAll("HTML", "Markdown", "Plain Text");
        formatComboBox.setValue("HTML");

        // Generation controls
        generateButton = new Button("Generate Documentation");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setPrefWidth(180);
        generateButton.setDisable(true);

        exportButton = new Button("Export...");
        exportButton.getStyleClass().add("secondary-button");
        exportButton.setPrefWidth(100);
        exportButton.setDisable(true);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        // Documentation display
        documentationView = new WebView();
        documentationView.setPrefHeight(400);
    }

    /**
     * Layout all components in the UI
     */
    private void layoutComponents() {
        // Header
        Label headerLabel = new Label("Schematron Documentation Generator");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        // File section
        VBox fileSection = createSection("Source File", createFileControls());

        // Options section
        VBox optionsSection = createSection("Generation Options", createOptionsControls());

        // Generation section
        VBox generationSection = createSection("Generate Documentation", createGenerationControls());

        // Preview section
        VBox previewSection = createSection("Documentation Preview", createPreviewControls());

        // Add all sections
        this.getChildren().addAll(
                headerLabel,
                new Separator(),
                fileSection,
                optionsSection,
                generationSection,
                previewSection
        );
    }

    /**
     * Creates a titled section containing the specified content.
     *
     * @param title   the title to display at the top of the section
     * @param content the VBox containing the section content
     * @return a VBox containing the titled section with proper styling
     */
    private VBox createSection(String title, VBox content) {
        VBox section = new VBox(5);
        section.setPadding(new Insets(5));
        section.getStyleClass().add("doc-generator-section");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.getStyleClass().add("section-title");

        section.getChildren().addAll(titleLabel, content);

        return section;
    }

    /**
     * Create file selection controls
     */
    private VBox createFileControls() {
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        fileRow.getChildren().addAll(
                new Label("File:"),
                schematronFileField,
                browseButton
        );
        HBox.setHgrow(schematronFileField, Priority.ALWAYS);

        VBox controls = new VBox(5);
        controls.getChildren().addAll(fileRow);

        return controls;
    }

    /**
     * Create options controls
     */
    private VBox createOptionsControls() {
        HBox optionsRow1 = new HBox(20);
        optionsRow1.setAlignment(Pos.CENTER_LEFT);
        optionsRow1.getChildren().addAll(includeXPathCheckBox, includeExamplesCheckBox);

        HBox optionsRow2 = new HBox(20);
        optionsRow2.setAlignment(Pos.CENTER_LEFT);
        optionsRow2.getChildren().addAll(
                includeStatisticsCheckBox,
                new Label("Format:"),
                formatComboBox
        );

        VBox controls = new VBox(5);
        controls.getChildren().addAll(optionsRow1, optionsRow2);

        return controls;
    }

    /**
     * Create generation controls
     */
    private VBox createGenerationControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(generateButton, exportButton);

        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(statusLabel, progressBar);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        VBox controls = new VBox(5);
        controls.getChildren().addAll(buttonRow, statusRow);

        return controls;
    }

    /**
     * Create preview controls
     */
    private VBox createPreviewControls() {
        VBox controls = new VBox(5);
        controls.getChildren().addAll(documentationView);
        VBox.setVgrow(documentationView, Priority.ALWAYS);

        return controls;
    }

    /**
     * Set up event handlers for all interactive components
     */
    private void setupEventHandlers() {
        browseButton.setOnAction(e -> browseForSchematronFile());
        generateButton.setOnAction(e -> generateDocumentation());
        exportButton.setOnAction(e -> exportDocumentation());

        formatComboBox.setOnAction(e -> {
            String format = formatComboBox.getValue();
            selectedFormat = DocumentationFormat.valueOf(format.toUpperCase().replace(" ", "_"));
        });

        // Enable/disable generate button based on file selection
        schematronFileField.textProperty().addListener((obs, oldVal, newVal) -> {
            generateButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });
    }

    /**
     * Browse for Schematron file
     */
    private void browseForSchematronFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Schematron File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Schematron files", "*.sch", "*.schematron"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (file != null) {
            currentSchematronFile = file;
            schematronFileField.setText(file.getAbsolutePath());
            logger.debug("Selected Schematron file for documentation: {}", file.getName());
        }
    }

    /**
     * Generate documentation from the selected Schematron file
     */
    private void generateDocumentation() {
        if (currentSchematronFile == null || !currentSchematronFile.exists()) {
            showAlert("File Error", "Please select a valid Schematron file");
            return;
        }

        progressBar.setProgress(-1); // Indeterminate progress
        progressBar.setVisible(true);
        statusLabel.setText("Generating documentation...");
        generateButton.setDisable(true);

        // Generate documentation in background
        Task<String> documentationTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return generateDocumentationFromFile(currentSchematronFile);
            }
        };

        documentationTask.setOnSucceeded(e -> {
            generatedDocumentation = documentationTask.getValue();
            displayDocumentation(generatedDocumentation);

            statusLabel.setText("Documentation generated successfully");
            progressBar.setVisible(false);
            generateButton.setDisable(false);
            exportButton.setDisable(false);

            logger.info("Documentation generated for: {}", currentSchematronFile.getName());
        });

        documentationTask.setOnFailed(e -> {
            Throwable exception = documentationTask.getException();
            logger.error("Documentation generation failed", exception);

            statusLabel.setText("Error: " + exception.getMessage());
            progressBar.setVisible(false);
            generateButton.setDisable(false);

            showAlert("Generation Error", "Failed to generate documentation: " + exception.getMessage());
        });

        executor.execute(documentationTask);
    }

    /**
     * Generate documentation content from Schematron file
     */
    private String generateDocumentationFromFile(File schematronFile) throws Exception {
        // Parse the Schematron XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(schematronFile);

        // Extract schema information
        SchemaInfo schemaInfo = extractSchemaInfo(document);

        // Generate documentation based on selected format
        switch (selectedFormat) {
            case HTML -> {
                return generateHTMLDocumentation(schemaInfo, schematronFile);
            }
            case MARKDOWN -> {
                return generateMarkdownDocumentation(schemaInfo, schematronFile);
            }
            case PLAIN_TEXT -> {
                return generatePlainTextDocumentation(schemaInfo, schematronFile);
            }
            default -> throw new IllegalArgumentException("Unsupported format: " + selectedFormat);
        }
    }

    /**
     * Extract schema information from XML document
     */
    private SchemaInfo extractSchemaInfo(Document document) {
        Element root = document.getDocumentElement();
        SchemaInfo info = new SchemaInfo();

        // Basic schema info
        info.title = getElementText(root, "title");
        info.queryBinding = root.getAttribute("queryBinding");
        info.schemaVersion = root.getAttribute("schemaVersion");
        info.defaultPhase = root.getAttribute("defaultPhase");

        // Extract namespaces
        NodeList nsElements = root.getElementsByTagNameNS("*", "ns");
        for (int i = 0; i < nsElements.getLength(); i++) {
            Element nsElement = (Element) nsElements.item(i);
            String prefix = nsElement.getAttribute("prefix");
            String uri = nsElement.getAttribute("uri");
            if (!prefix.isEmpty() && !uri.isEmpty()) {
                info.namespaces.add(new NamespaceInfo(prefix, uri));
            }
        }

        // Extract patterns
        NodeList patterns = root.getElementsByTagNameNS("*", "pattern");
        for (int i = 0; i < patterns.getLength(); i++) {
            Element pattern = (Element) patterns.item(i);
            PatternInfo patternInfo = new PatternInfo();
            patternInfo.id = pattern.getAttribute("id");
            patternInfo.title = getElementText(pattern, "title");
            patternInfo.isAbstract = "true".equals(pattern.getAttribute("abstract"));

            // Extract rules within pattern
            NodeList rules = pattern.getElementsByTagNameNS("*", "rule");
            for (int j = 0; j < rules.getLength(); j++) {
                Element rule = (Element) rules.item(j);
                RuleInfo ruleInfo = new RuleInfo();
                ruleInfo.id = rule.getAttribute("id");
                ruleInfo.context = rule.getAttribute("context");
                ruleInfo.role = rule.getAttribute("role");

                // Extract assertions and reports
                NodeList assertions = rule.getElementsByTagNameNS("*", "assert");
                for (int k = 0; k < assertions.getLength(); k++) {
                    Element assertion = (Element) assertions.item(k);
                    AssertionInfo assertInfo = new AssertionInfo();
                    assertInfo.type = "assert";
                    assertInfo.test = assertion.getAttribute("test");
                    assertInfo.message = assertion.getTextContent().trim();
                    assertInfo.flag = assertion.getAttribute("flag");
                    assertInfo.role = assertion.getAttribute("role");
                    ruleInfo.assertions.add(assertInfo);
                }

                NodeList reports = rule.getElementsByTagNameNS("*", "report");
                for (int k = 0; k < reports.getLength(); k++) {
                    Element report = (Element) reports.item(k);
                    AssertionInfo reportInfo = new AssertionInfo();
                    reportInfo.type = "report";
                    reportInfo.test = report.getAttribute("test");
                    reportInfo.message = report.getTextContent().trim();
                    reportInfo.flag = report.getAttribute("flag");
                    reportInfo.role = report.getAttribute("role");
                    ruleInfo.assertions.add(reportInfo);
                }

                patternInfo.rules.add(ruleInfo);
            }

            info.patterns.add(patternInfo);
        }

        return info;
    }

    /**
     * Get text content of a child element
     */
    private String getElementText(Element parent, String childName) {
        NodeList children = parent.getElementsByTagNameNS("*", childName);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent().trim();
        }
        return "";
    }

    /**
     * Generate HTML documentation
     */
    private String generateHTMLDocumentation(SchemaInfo schema, File sourceFile) {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Schematron Documentation</title>\n");
        html.append("    <style>\n");
        html.append(getDocumentationCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        // Header section
        html.append("    <header>\n");
        html.append("        <h1>Schematron Documentation</h1>\n");
        if (!schema.title.isEmpty()) {
            html.append("        <h2>").append(escapeHtml(schema.title)).append("</h2>\n");
        }
        html.append("        <div class=\"metadata\">\n");
        html.append("            <p><strong>Source:</strong> ").append(escapeHtml(sourceFile.getName())).append("</p>\n");
        html.append("            <p><strong>Generated:</strong> ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        if (!schema.queryBinding.isEmpty()) {
            html.append("            <p><strong>Query Binding:</strong> ").append(escapeHtml(schema.queryBinding)).append("</p>\n");
        }
        html.append("        </div>\n");
        html.append("    </header>\n");

        // Statistics section (if enabled)
        if (includeStatisticsCheckBox.isSelected()) {
            html.append(generateStatisticsSection(schema));
        }

        // Namespaces section
        if (!schema.namespaces.isEmpty()) {
            html.append(generateNamespacesSection(schema));
        }

        // Patterns section
        html.append(generatePatternsSection(schema));

        // Footer
        html.append("    <footer>\n");
        html.append("        <p>Generated by FreeXmlToolkit Schematron Documentation Generator</p>\n");
        html.append("    </footer>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Generate statistics section for HTML
     */
    private String generateStatisticsSection(SchemaInfo schema) {
        StringBuilder html = new StringBuilder();

        int totalRules = schema.patterns.stream().mapToInt(p -> p.rules.size()).sum();
        int totalAssertions = schema.patterns.stream()
                .flatMap(p -> p.rules.stream())
                .mapToInt(r -> r.assertions.size()).sum();

        html.append("    <section class=\"statistics\">\n");
        html.append("        <h2>Statistics</h2>\n");
        html.append("        <div class=\"stats-grid\">\n");
        html.append("            <div class=\"stat-item\">\n");
        html.append("                <span class=\"stat-number\">").append(schema.patterns.size()).append("</span>\n");
        html.append("                <span class=\"stat-label\">Patterns</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-item\">\n");
        html.append("                <span class=\"stat-number\">").append(totalRules).append("</span>\n");
        html.append("                <span class=\"stat-label\">Rules</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-item\">\n");
        html.append("                <span class=\"stat-number\">").append(totalAssertions).append("</span>\n");
        html.append("                <span class=\"stat-label\">Assertions</span>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-item\">\n");
        html.append("                <span class=\"stat-number\">").append(schema.namespaces.size()).append("</span>\n");
        html.append("                <span class=\"stat-label\">Namespaces</span>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("    </section>\n");

        return html.toString();
    }

    /**
     * Generate namespaces section for HTML
     */
    private String generateNamespacesSection(SchemaInfo schema) {
        StringBuilder html = new StringBuilder();

        html.append("    <section class=\"namespaces\">\n");
        html.append("        <h2>Namespaces</h2>\n");
        html.append("        <table>\n");
        html.append("            <thead>\n");
        html.append("                <tr><th>Prefix</th><th>URI</th></tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");

        for (NamespaceInfo ns : schema.namespaces) {
            html.append("                <tr>\n");
            html.append("                    <td><code>").append(escapeHtml(ns.prefix)).append("</code></td>\n");
            html.append("                    <td><code>").append(escapeHtml(ns.uri)).append("</code></td>\n");
            html.append("                </tr>\n");
        }

        html.append("            </tbody>\n");
        html.append("        </table>\n");
        html.append("    </section>\n");

        return html.toString();
    }

    /**
     * Generate patterns section for HTML
     */
    private String generatePatternsSection(SchemaInfo schema) {
        StringBuilder html = new StringBuilder();

        html.append("    <section class=\"patterns\">\n");
        html.append("        <h2>Patterns</h2>\n");

        for (PatternInfo pattern : schema.patterns) {
            html.append("        <div class=\"pattern\">\n");
            html.append("            <h3>");
            if (!pattern.title.isEmpty()) {
                html.append(escapeHtml(pattern.title));
            } else {
                html.append("Pattern");
            }
            if (!pattern.id.isEmpty()) {
                html.append(" <span class=\"id\">(").append(escapeHtml(pattern.id)).append(")</span>");
            }
            if (pattern.isAbstract) {
                html.append(" <span class=\"abstract\">Abstract</span>");
            }
            html.append("</h3>\n");

            // Rules
            for (RuleInfo rule : pattern.rules) {
                html.append("            <div class=\"rule\">\n");
                html.append("                <h4>Rule");
                if (!rule.id.isEmpty()) {
                    html.append(" <span class=\"id\">(").append(escapeHtml(rule.id)).append(")</span>");
                }
                html.append("</h4>\n");
                html.append("                <p><strong>Context:</strong> <code>").append(escapeHtml(rule.context)).append("</code></p>\n");

                if (!rule.role.isEmpty()) {
                    html.append("                <p><strong>Role:</strong> ").append(escapeHtml(rule.role)).append("</p>\n");
                }

                // Assertions
                if (!rule.assertions.isEmpty()) {
                    html.append("                <div class=\"assertions\">\n");
                    for (AssertionInfo assertion : rule.assertions) {
                        String cssClass = assertion.type.equals("assert") ? "assertion" : "report";
                        html.append("                    <div class=\"").append(cssClass).append("\">\n");
                        html.append("                        <h5>").append(assertion.type.toUpperCase()).append("</h5>\n");

                        if (includeXPathCheckBox.isSelected()) {
                            html.append("                        <p><strong>Test:</strong> <code>").append(escapeHtml(assertion.test)).append("</code></p>\n");
                        }

                        html.append("                        <p><strong>Message:</strong> ").append(escapeHtml(assertion.message)).append("</p>\n");

                        if (!assertion.flag.isEmpty()) {
                            html.append("                        <p><strong>Flag:</strong> ").append(escapeHtml(assertion.flag)).append("</p>\n");
                        }

                        html.append("                    </div>\n");
                    }
                    html.append("                </div>\n");
                }

                html.append("            </div>\n");
            }

            html.append("        </div>\n");
        }

        html.append("    </section>\n");

        return html.toString();
    }

    /**
     * Generate Markdown documentation
     */
    private String generateMarkdownDocumentation(SchemaInfo schema, File sourceFile) {
        StringBuilder md = new StringBuilder();

        // Header
        md.append("# Schematron Documentation\n\n");
        if (!schema.title.isEmpty()) {
            md.append("## ").append(schema.title).append("\n\n");
        }

        md.append("**Source:** ").append(sourceFile.getName()).append("\n");
        md.append("**Generated:** ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // Statistics (if enabled)
        if (includeStatisticsCheckBox.isSelected()) {
            int totalRules = schema.patterns.stream().mapToInt(p -> p.rules.size()).sum();
            int totalAssertions = schema.patterns.stream()
                    .flatMap(p -> p.rules.stream())
                    .mapToInt(r -> r.assertions.size()).sum();

            md.append("## Statistics\n\n");
            md.append("- **Patterns:** ").append(schema.patterns.size()).append("\n");
            md.append("- **Rules:** ").append(totalRules).append("\n");
            md.append("- **Assertions:** ").append(totalAssertions).append("\n");
            md.append("- **Namespaces:** ").append(schema.namespaces.size()).append("\n\n");
        }

        // Namespaces
        if (!schema.namespaces.isEmpty()) {
            md.append("## Namespaces\n\n");
            md.append("| Prefix | URI |\n");
            md.append("|--------|-----|\n");
            for (NamespaceInfo ns : schema.namespaces) {
                md.append("| `").append(ns.prefix).append("` | `").append(ns.uri).append("` |\n");
            }
            md.append("\n");
        }

        // Patterns
        md.append("## Patterns\n\n");
        for (PatternInfo pattern : schema.patterns) {
            md.append("### ");
            if (!pattern.title.isEmpty()) {
                md.append(pattern.title);
            } else {
                md.append("Pattern");
            }
            if (!pattern.id.isEmpty()) {
                md.append(" (").append(pattern.id).append(")");
            }
            if (pattern.isAbstract) {
                md.append(" [Abstract]");
            }
            md.append("\n\n");

            // Rules
            for (RuleInfo rule : pattern.rules) {
                md.append("#### Rule");
                if (!rule.id.isEmpty()) {
                    md.append(" (").append(rule.id).append(")");
                }
                md.append("\n\n");
                md.append("**Context:** `").append(rule.context).append("`\n\n");

                // Assertions
                for (AssertionInfo assertion : rule.assertions) {
                    md.append("- **").append(assertion.type.toUpperCase()).append(":** ");
                    if (includeXPathCheckBox.isSelected()) {
                        md.append("`").append(assertion.test).append("` - ");
                    }
                    md.append(assertion.message).append("\n");
                }
                md.append("\n");
            }
        }

        return md.toString();
    }

    /**
     * Generate plain text documentation
     */
    private String generatePlainTextDocumentation(SchemaInfo schema, File sourceFile) {
        StringBuilder txt = new StringBuilder();

        txt.append("SCHEMATRON DOCUMENTATION\n");
        txt.append("=======================\n\n");

        if (!schema.title.isEmpty()) {
            txt.append("Title: ").append(schema.title).append("\n");
        }
        txt.append("Source: ").append(sourceFile.getName()).append("\n");
        txt.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // Patterns
        txt.append("PATTERNS\n");
        txt.append("--------\n\n");

        for (PatternInfo pattern : schema.patterns) {
            txt.append("Pattern: ");
            if (!pattern.title.isEmpty()) {
                txt.append(pattern.title);
            } else {
                txt.append("(Unnamed)");
            }
            if (!pattern.id.isEmpty()) {
                txt.append(" (ID: ").append(pattern.id).append(")");
            }
            txt.append("\n");

            for (RuleInfo rule : pattern.rules) {
                txt.append("  Rule: ").append(rule.context).append("\n");
                for (AssertionInfo assertion : rule.assertions) {
                    txt.append("    ").append(assertion.type.toUpperCase()).append(": ");
                    if (includeXPathCheckBox.isSelected()) {
                        txt.append(assertion.test).append(" - ");
                    }
                    txt.append(assertion.message).append("\n");
                }
            }
            txt.append("\n");
        }

        return txt.toString();
    }

    /**
     * Display documentation in the WebView
     */
    private void displayDocumentation(String documentation) {
        if (selectedFormat == DocumentationFormat.HTML) {
            documentationView.getEngine().loadContent(documentation);
        } else {
            // For non-HTML formats, wrap in HTML for display
            String htmlWrapper = "<html><body><pre>" + escapeHtml(documentation) + "</pre></body></html>";
            documentationView.getEngine().loadContent(htmlWrapper);
        }
    }

    /**
     * Export documentation to file
     */
    private void exportDocumentation() {
        if (generatedDocumentation == null) {
            showAlert("Export Error", "No documentation to export. Generate documentation first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Documentation");

        String extension;
        String description;

        switch (selectedFormat) {
            case HTML -> {
                extension = "*.html";
                description = "HTML files";
            }
            case MARKDOWN -> {
                extension = "*.md";
                description = "Markdown files";
            }
            case PLAIN_TEXT -> {
                extension = "*.txt";
                description = "Text files";
            }
            default -> {
                extension = "*.txt";
                description = "Text files";
            }
        }

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(description, extension),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), generatedDocumentation);
                statusLabel.setText("Documentation exported to " + file.getName());
                logger.info("Documentation exported to: {}", file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Export failed", e);
                showAlert("Export Error", "Failed to export documentation: " + e.getMessage());
            }
        }
    }

    /**
     * Sets the current Schematron file for documentation generation.
     * Updates the file field display to show the selected file path.
     *
     * @param schematronFile the Schematron file to document, or null to clear the selection
     */
    public void setSchematronFile(File schematronFile) {
        this.currentSchematronFile = schematronFile;
        if (schematronFile != null) {
            schematronFileField.setText(schematronFile.getAbsolutePath());
        } else {
            schematronFileField.clear();
        }
    }

    /**
     * Get CSS for HTML documentation
     */
    private String getDocumentationCSS() {
        return """
                body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
                header { border-bottom: 2px solid #007bff; padding-bottom: 20px; margin-bottom: 30px; }
                h1 { color: #007bff; margin: 0; }
                h2 { color: #0056b3; border-bottom: 1px solid #dee2e6; padding-bottom: 10px; }
                h3 { color: #495057; }
                .metadata { background: #f8f9fa; padding: 15px; border-radius: 5px; margin-top: 15px; }
                .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin: 20px 0; }
                .stat-item { text-align: center; background: #e9ecef; padding: 20px; border-radius: 5px; }
                .stat-number { display: block; font-size: 2em; font-weight: bold; color: #007bff; }
                .stat-label { font-size: 0.9em; color: #6c757d; }
                table { width: 100%; border-collapse: collapse; margin: 20px 0; }
                th, td { padding: 10px; text-align: left; border-bottom: 1px solid #dee2e6; }
                th { background: #f8f9fa; font-weight: bold; }
                code { background: #f8f9fa; padding: 2px 5px; border-radius: 3px; font-family: 'Consolas', monospace; }
                .pattern { margin: 30px 0; border: 1px solid #dee2e6; border-radius: 5px; padding: 20px; }
                .rule { margin: 20px 0; background: #f8f9fa; padding: 15px; border-radius: 5px; }
                .assertion { background: #d4edda; padding: 10px; margin: 10px 0; border-left: 4px solid #28a745; }
                .report { background: #fff3cd; padding: 10px; margin: 10px 0; border-left: 4px solid #ffc107; }
                .id { font-size: 0.9em; color: #6c757d; }
                .abstract { background: #17a2b8; color: white; padding: 2px 8px; border-radius: 3px; font-size: 0.8em; }
                footer { margin-top: 50px; text-align: center; color: #6c757d; font-size: 0.9em; }
                """;
    }

    /**
     * Escape HTML entities
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * Show an alert dialog
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Clean up resources
     */
    public void dispose() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // ========== Data Classes ==========

    /**
     * Enumeration of supported documentation output formats.
     */
    public enum DocumentationFormat {
        /** HTML format with CSS styling for web display */
        HTML,
        /** Markdown format for documentation systems */
        MARKDOWN,
        /** Plain text format for simple output */
        PLAIN_TEXT
    }

    /**
     * Schema information container
     */
    private static class SchemaInfo {
        String title = "";
        String queryBinding = "";
        String schemaVersion = "";
        String defaultPhase = "";
        List<NamespaceInfo> namespaces = new ArrayList<>();
        List<PatternInfo> patterns = new ArrayList<>();
    }

    /**
     * Namespace information
     */
    private static class NamespaceInfo {
        String prefix;
        String uri;

        NamespaceInfo(String prefix, String uri) {
            this.prefix = prefix;
            this.uri = uri;
        }
    }

    /**
     * Pattern information
     */
    private static class PatternInfo {
        String id = "";
        String title = "";
        boolean isAbstract = false;
        List<RuleInfo> rules = new ArrayList<>();
    }

    /**
     * Rule information
     */
    private static class RuleInfo {
        String id = "";
        String context = "";
        String role = "";
        List<AssertionInfo> assertions = new ArrayList<>();
    }

    /**
     * Assertion/Report information
     */
    private static class AssertionInfo {
        String type = "";
        String test = "";
        String message = "";
        String flag = "";
        String role = "";
    }
}