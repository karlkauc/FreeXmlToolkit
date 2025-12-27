/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.unified;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * Properties pane for Schematron editor in the MultiFunctionalSidePane.
 * <p>
 * Provides:
 * <ul>
 *   <li>Quick Help for Schematron syntax</li>
 *   <li>Document structure view (patterns, rules)</li>
 *   <li>Common templates for assertions</li>
 *   <li>XPath tester for testing expressions</li>
 * </ul>
 *
 * @since 2.0
 */
public class SchematronPropertiesPane extends VBox {

    private static final Logger logger = LogManager.getLogger(SchematronPropertiesPane.class);

    // Structure view
    private final TreeView<String> structureTree;
    private final TreeItem<String> rootItem;

    // XPath tester
    private final TextField xpathInput;
    private final TextArea xpathResult;

    // Callbacks
    private Consumer<String> onInsertTemplate;
    private Consumer<String> onXPathTest;

    /**
     * Creates a new Schematron properties pane.
     */
    public SchematronPropertiesPane() {
        super(12);
        setPadding(new Insets(12));
        getStyleClass().add("schematron-properties-pane");

        // Quick Help section
        TitledPane quickHelpPane = createQuickHelpSection();

        // Structure section
        rootItem = new TreeItem<>("Schematron Document");
        rootItem.setExpanded(true);
        structureTree = new TreeView<>(rootItem);
        structureTree.setPrefHeight(150);
        TitledPane structurePane = createStructureSection();

        // Templates section
        TitledPane templatesPane = createTemplatesSection();

        // XPath Tester section
        xpathInput = new TextField();
        xpathResult = new TextArea();
        TitledPane xpathPane = createXPathTesterSection();

        getChildren().addAll(quickHelpPane, structurePane, templatesPane, xpathPane);

        logger.debug("SchematronPropertiesPane created");
    }

    /**
     * Creates the Quick Help section.
     */
    private TitledPane createQuickHelpSection() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(8));

        Label intro = new Label("""
            Schematron is a rule-based validation language for XML.

            Key elements:
            - pattern: Groups related rules
            - rule: Defines context (XPath)
            - assert: Must be true
            - report: Fires when true
            """);
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 11px;");

        content.getChildren().add(intro);

        TitledPane pane = new TitledPane("Quick Help", content);
        FontIcon icon = new FontIcon("bi-question-circle");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(false);

        return pane;
    }

    /**
     * Creates the Structure section with tree view.
     */
    private TitledPane createStructureSection() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(8));

        // Refresh button
        HBox toolbar = new HBox(4);
        Button refreshBtn = new Button("Refresh");
        FontIcon refreshIcon = new FontIcon("bi-arrow-clockwise");
        refreshIcon.setIconSize(12);
        refreshBtn.setGraphic(refreshIcon);
        refreshBtn.setOnAction(e -> {
            if (onXPathTest != null) {
                onXPathTest.accept("__REFRESH_STRUCTURE__");
            }
        });
        toolbar.getChildren().add(refreshBtn);

        content.getChildren().addAll(toolbar, structureTree);
        VBox.setVgrow(structureTree, Priority.ALWAYS);

        TitledPane pane = new TitledPane("Document Structure", content);
        FontIcon icon = new FontIcon("bi-list-nested");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    /**
     * Creates the Templates section.
     */
    private TitledPane createTemplatesSection() {
        VBox content = new VBox(6);
        content.setPadding(new Insets(8));

        // Template buttons
        content.getChildren().addAll(
                createTemplateButton("Assert", "bi-check-circle", ASSERT_TEMPLATE),
                createTemplateButton("Report", "bi-info-circle", REPORT_TEMPLATE),
                createTemplateButton("Rule", "bi-code-square", RULE_TEMPLATE),
                createTemplateButton("Pattern", "bi-grid-3x3", PATTERN_TEMPLATE),
                createTemplateButton("Variable", "bi-braces", VARIABLE_TEMPLATE)
        );

        TitledPane pane = new TitledPane("Templates", content);
        FontIcon icon = new FontIcon("bi-file-earmark-plus");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    /**
     * Creates a template insertion button.
     */
    private Button createTemplateButton(String text, String iconLiteral, String template) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(14);
        btn.setGraphic(icon);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setOnAction(e -> {
            if (onInsertTemplate != null) {
                onInsertTemplate.accept(template);
            }
        });
        return btn;
    }

    /**
     * Creates the XPath Tester section.
     */
    private TitledPane createXPathTesterSection() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(8));

        // Input row
        HBox inputRow = new HBox(4);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        xpathInput.setPromptText("Enter XPath expression...");
        HBox.setHgrow(xpathInput, Priority.ALWAYS);

        Button testBtn = new Button();
        FontIcon playIcon = new FontIcon("bi-play-fill");
        playIcon.setIconSize(14);
        testBtn.setGraphic(playIcon);
        testBtn.setTooltip(new Tooltip("Test XPath"));
        testBtn.setOnAction(e -> testXPath());

        inputRow.getChildren().addAll(xpathInput, testBtn);

        // Result area
        xpathResult.setPromptText("XPath result will appear here...");
        xpathResult.setEditable(false);
        xpathResult.setPrefRowCount(4);
        xpathResult.setWrapText(true);
        xpathResult.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");

        content.getChildren().addAll(inputRow, xpathResult);
        VBox.setVgrow(xpathResult, Priority.ALWAYS);

        TitledPane pane = new TitledPane("XPath Tester", content);
        FontIcon icon = new FontIcon("bi-search");
        icon.setIconSize(14);
        pane.setGraphic(icon);
        pane.setCollapsible(true);
        pane.setExpanded(false);

        return pane;
    }

    /**
     * Tests the XPath expression.
     */
    private void testXPath() {
        String xpath = xpathInput.getText();
        if (xpath == null || xpath.trim().isEmpty()) {
            xpathResult.setText("Please enter an XPath expression");
            return;
        }

        if (onXPathTest != null) {
            onXPathTest.accept(xpath);
        }
    }

    // ==================== Public API ====================

    /**
     * Sets the callback for template insertion.
     */
    public void setOnInsertTemplate(Consumer<String> callback) {
        this.onInsertTemplate = callback;
    }

    /**
     * Sets the callback for XPath testing.
     */
    public void setOnXPathTest(Consumer<String> callback) {
        this.onXPathTest = callback;
    }

    /**
     * Updates the XPath result.
     */
    public void setXPathResult(String result) {
        xpathResult.setText(result);
    }

    /**
     * Clears the structure tree.
     */
    public void clearStructure() {
        rootItem.getChildren().clear();
    }

    /**
     * Adds a pattern to the structure tree.
     */
    public void addPattern(String patternId, String title) {
        TreeItem<String> patternItem = new TreeItem<>("Pattern: " + (title != null ? title : patternId));
        FontIcon icon = new FontIcon("bi-grid-3x3");
        icon.setIconSize(14);
        patternItem.setGraphic(icon);
        patternItem.setExpanded(true);
        rootItem.getChildren().add(patternItem);
    }

    /**
     * Adds a rule to the last pattern in the structure tree.
     */
    public void addRule(String context) {
        if (!rootItem.getChildren().isEmpty()) {
            TreeItem<String> lastPattern = rootItem.getChildren().get(rootItem.getChildren().size() - 1);
            TreeItem<String> ruleItem = new TreeItem<>("Rule: " + context);
            FontIcon icon = new FontIcon("bi-code-square");
            icon.setIconSize(14);
            ruleItem.setGraphic(icon);
            lastPattern.getChildren().add(ruleItem);
        }
    }

    /**
     * Gets the structure tree.
     */
    public TreeView<String> getStructureTree() {
        return structureTree;
    }

    // ==================== Templates ====================

    private static final String ASSERT_TEMPLATE = """
            <sch:assert test="expression">
                Assertion message when test fails.
            </sch:assert>""";

    private static final String REPORT_TEMPLATE = """
            <sch:report test="expression">
                Report message when test succeeds.
            </sch:report>""";

    private static final String RULE_TEMPLATE = """
            <sch:rule context="XPath-context">
                <sch:assert test="expression">
                    Assertion message.
                </sch:assert>
            </sch:rule>""";

    private static final String PATTERN_TEMPLATE = """
            <sch:pattern id="pattern-id">
                <sch:title>Pattern Title</sch:title>

                <sch:rule context="XPath-context">
                    <sch:assert test="expression">
                        Assertion message.
                    </sch:assert>
                </sch:rule>
            </sch:pattern>""";

    private static final String VARIABLE_TEMPLATE = """
            <sch:let name="variable-name" value="XPath-expression"/>""";
}
