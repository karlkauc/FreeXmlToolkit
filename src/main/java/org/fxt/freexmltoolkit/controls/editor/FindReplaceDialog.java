/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

package org.fxt.freexmltoolkit.controls.editor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindReplaceDialog extends Dialog<Void> {

    private final CodeArea codeArea;
    private TextField findField = new TextField();
    private TextField replaceField = new TextField();
    private CheckBox matchCaseCheck = new CheckBox("Match Case");
    private CheckBox wholeWordCheck = new CheckBox("Whole Word");
    private CheckBox regexCheck = new CheckBox("Regex");

    private int lastFoundIndex = -1;

    public FindReplaceDialog(CodeArea codeArea) {
        this.codeArea = codeArea;
        setTitle("Find and Replace");
        setHeaderText("Search and replace text in the XML document");
        
        // Apply modern styling
        initializeModernUI();
        
        // Create custom button types with modern styling
        setupButtons();
        
        // Focus management
        setupFocusManagement();
    }
    
    /**
     * Initializes the modern UI with consistent styling.
     */
    private void initializeModernUI() {
        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(20));
        mainContainer.getStyleClass().add("popup-container");
        
        // Header section
        VBox headerSection = createHeaderSection();
        
        // Search fields section
        GridPane fieldsGrid = createFieldsSection();
        
        // Options section
        VBox optionsSection = createOptionsSection();
        
        mainContainer.getChildren().addAll(headerSection, fieldsGrid, optionsSection);
        getDialogPane().setContent(mainContainer);
        
        // Apply CSS styling
        applyCssStyles();
    }
    
    /**
     * Creates the header section with title and description.
     */
    private VBox createHeaderSection() {
        VBox header = new VBox(5);
        header.getStyleClass().add("popup-header");
        
        Label titleLabel = new Label("Find and Replace");
        titleLabel.getStyleClass().add("popup-title");
        
        Label subtitleLabel = new Label("Search and replace text with advanced options");
        subtitleLabel.getStyleClass().add("popup-subtitle");
        
        header.getChildren().addAll(titleLabel, subtitleLabel);
        return header;
    }
    
    /**
     * Creates the input fields section.
     */
    private GridPane createFieldsSection() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 10, 0));
        
        // Find field with icon
        Label findLabel = new Label("Find:");
        findLabel.getStyleClass().add("form-label");
        
        findField.setPromptText("Enter text to find...");
        findField.setMinWidth(350);
        findField.setPrefWidth(350);
        findField.getStyleClass().add("text-field");
        
        // Replace field with icon
        Label replaceLabel = new Label("Replace:");
        replaceLabel.getStyleClass().add("form-label");
        
        replaceField.setPromptText("Enter replacement text...");
        replaceField.setMinWidth(350);
        replaceField.setPrefWidth(350);
        replaceField.getStyleClass().add("text-field");
        
        grid.add(findLabel, 0, 0);
        grid.add(findField, 1, 0);
        grid.add(replaceLabel, 0, 1);
        grid.add(replaceField, 1, 1);
        
        return grid;
    }
    
    /**
     * Creates the options section with checkboxes.
     */
    private VBox createOptionsSection() {
        VBox optionsContainer = new VBox(10);
        
        Label optionsLabel = new Label("Search Options:");
        optionsLabel.getStyleClass().add("subsection-label");
        
        // Style checkboxes
        matchCaseCheck.setText("Match Case");
        matchCaseCheck.getStyleClass().add("check-box");
        
        wholeWordCheck.setText("Whole Word");
        wholeWordCheck.getStyleClass().add("check-box");
        
        regexCheck.setText("Regular Expression");
        regexCheck.getStyleClass().add("check-box");
        
        HBox optionsBox = new HBox(20, matchCaseCheck, wholeWordCheck, regexCheck);
        optionsBox.setAlignment(Pos.CENTER_LEFT);
        
        optionsContainer.getChildren().addAll(optionsLabel, optionsBox);
        return optionsContainer;
    }
    
    /**
     * Applies CSS styles and loads the stylesheet.
     */
    private void applyCssStyles() {
        // Load the popup CSS stylesheet
        String cssPath = "/css/popups.css";
        var cssResource = getClass().getResource(cssPath);
        if (cssResource != null) {
            getDialogPane().getStylesheets().add(cssResource.toExternalForm());
        }
        
        // Set dialog pane styling
        getDialogPane().getStyleClass().add("popup-container");
    }
    
    /**
     * Sets up the dialog buttons with icons and modern styling.
     */
    private void setupButtons() {
        ButtonType findButtonType = new ButtonType("Find Next", ButtonBar.ButtonData.OK_DONE);
        ButtonType replaceButtonType = new ButtonType("Replace", ButtonBar.ButtonData.OK_DONE);
        ButtonType replaceAllButtonType = new ButtonType("Replace All", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        getDialogPane().getButtonTypes().addAll(findButtonType, replaceButtonType, replaceAllButtonType, closeButtonType);

        // Configure Find Next button
        final Button findButton = (Button) getDialogPane().lookupButton(findButtonType);
        findButton.setOnAction(e -> findNext());
        findButton.setDisable(true);
        findButton.getStyleClass().addAll("btn-primary");
        addButtonIcon(findButton, "/img/next.png");
        findField.textProperty().addListener((obs, old, text) -> findButton.setDisable(text.isEmpty()));

        // Configure Replace button
        final Button replaceButton = (Button) getDialogPane().lookupButton(replaceButtonType);
        replaceButton.setOnAction(e -> replace());
        replaceButton.getStyleClass().addAll("btn-info");
        addButtonIcon(replaceButton, "/img/icons8-aktualisieren-48.png");

        // Configure Replace All button
        final Button replaceAllButton = (Button) getDialogPane().lookupButton(replaceAllButtonType);
        replaceAllButton.setOnAction(e -> replaceAll());
        replaceAllButton.getStyleClass().addAll("btn-warning");
        addButtonIcon(replaceAllButton, "/img/icons8-aktualisieren-48.png");
        
        // Configure Close button
        final Button closeButton = (Button) getDialogPane().lookupButton(closeButtonType);
        closeButton.getStyleClass().addAll("btn-secondary");
        addButtonIcon(closeButton, "/img/icons8-stornieren-48.png");
    }
    
    /**
     * Adds an icon to a button.
     */
    private void addButtonIcon(Button button, String iconPath) {
        try {
            var iconResource = getClass().getResource(iconPath);
            if (iconResource != null) {
                Image icon = new Image(iconResource.toExternalForm());
                ImageView iconView = new ImageView(icon);
                iconView.setFitWidth(16);
                iconView.setFitHeight(16);
                iconView.setPreserveRatio(true);
                button.setGraphic(iconView);
            }
        } catch (Exception e) {
            // Icon loading failed, button will display without icon
        }
    }
    
    /**
     * Sets up focus management and keyboard shortcuts.
     */
    private void setupFocusManagement() {
        // Auto-focus on find field when dialog opens
        setOnShowing(e -> javafx.application.Platform.runLater(() -> findField.requestFocus()));
        
        // Reset search index when find text changes
        findField.textProperty().addListener((obs, old, text) -> lastFoundIndex = -1);
        
        // Keyboard shortcuts
        findField.setOnAction(e -> findNext());
        replaceField.setOnAction(e -> replace());
    }

    private void findNext() {
        String searchText = findField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        String content = codeArea.getText();
        int fromIndex = codeArea.getSelection().getEnd();

        Pattern pattern = createPattern(searchText);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find(fromIndex)) {
            codeArea.selectRange(matcher.start(), matcher.end());
            lastFoundIndex = matcher.start();
        } else { // Wrap search
            lastFoundIndex = -1;
            if (matcher.find(0)) {
                codeArea.selectRange(matcher.start(), matcher.end());
                lastFoundIndex = matcher.start();
            }
        }
    }

    private void replace() {
        String selectedText = codeArea.getSelectedText();
        String searchText = findField.getText();
        Pattern pattern = createPattern(searchText);
        Matcher matcher = pattern.matcher(selectedText);

        if (matcher.matches()) {
            codeArea.replaceSelection(replaceField.getText());
        }
        findNext();
    }

    private void replaceAll() {
        String searchText = findField.getText();
        String replaceText = replaceField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        Pattern pattern = createPattern(searchText);
        Matcher matcher = pattern.matcher(codeArea.getText());
        String newContent = matcher.replaceAll(replaceText);
        codeArea.replaceText(newContent);
    }

    private Pattern createPattern(String searchText) {
        StringBuilder regex = new StringBuilder();
        if (wholeWordCheck.isSelected()) {
            regex.append("\\b");
        }
        regex.append(regexCheck.isSelected() ? searchText : Pattern.quote(searchText));
        if (wholeWordCheck.isSelected()) {
            regex.append("\\b");
        }

        int flags = 0;
        if (!matchCaseCheck.isSelected()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }

        return Pattern.compile(regex.toString(), flags);
    }
}