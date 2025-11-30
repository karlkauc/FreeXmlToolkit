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
    private final TextField findField = new TextField();
    private final TextField replaceField = new TextField();
    private final CheckBox matchCaseCheck = new CheckBox("Match Case");
    private final CheckBox wholeWordCheck = new CheckBox("Whole Word");
    private final CheckBox regexCheck = new CheckBox("Regex");

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

        // Prevent dialog from closing except for Close button
        setupResultConverter();
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
        ButtonType findPrevButtonType = new ButtonType("Find Previous", ButtonBar.ButtonData.OTHER);
        ButtonType findButtonType = new ButtonType("Find Next", ButtonBar.ButtonData.OTHER);
        ButtonType replaceButtonType = new ButtonType("Replace", ButtonBar.ButtonData.OTHER);
        ButtonType replaceAllButtonType = new ButtonType("Replace All", ButtonBar.ButtonData.OTHER);
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        getDialogPane().getButtonTypes().addAll(findPrevButtonType, findButtonType, replaceButtonType, replaceAllButtonType, closeButtonType);

        // Configure Find Previous button - use addEventFilter to prevent dialog from closing
        final Button findPrevButton = (Button) getDialogPane().lookupButton(findPrevButtonType);
        findPrevButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            findPrevious();
            e.consume(); // Consume event BEFORE dialog processes it
        });
        findPrevButton.setDisable(true);
        findPrevButton.getStyleClass().addAll("btn-primary");
        addButtonIcon(findPrevButton, "/img/previous.png");
        findField.textProperty().addListener((obs, old, text) -> findPrevButton.setDisable(text.isEmpty()));

        // Configure Find Next button - use addEventFilter to prevent dialog from closing
        final Button findButton = (Button) getDialogPane().lookupButton(findButtonType);
        findButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            findNext();
            e.consume(); // Consume event BEFORE dialog processes it
        });
        findButton.setDisable(true);
        findButton.getStyleClass().addAll("btn-primary");
        addButtonIcon(findButton, "/img/next.png");
        findField.textProperty().addListener((obs, old, text) -> findButton.setDisable(text.isEmpty()));

        // Configure Replace button - use addEventFilter to prevent dialog from closing
        final Button replaceButton = (Button) getDialogPane().lookupButton(replaceButtonType);
        replaceButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            replace();
            e.consume(); // Consume event BEFORE dialog processes it
        });
        replaceButton.getStyleClass().addAll("btn-info");
        addButtonIcon(replaceButton, "/img/icons8-aktualisieren-48.png");

        // Configure Replace All button - use addEventFilter to prevent dialog from closing
        final Button replaceAllButton = (Button) getDialogPane().lookupButton(replaceAllButtonType);
        replaceAllButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            replaceAll();
            e.consume(); // Consume event BEFORE dialog processes it
        });
        replaceAllButton.getStyleClass().addAll("btn-warning");
        addButtonIcon(replaceAllButton, "/img/icons8-aktualisieren-48.png");

        // Configure Close button - this one SHOULD close the dialog
        final Button closeButton = (Button) getDialogPane().lookupButton(closeButtonType);
        // Don't add event filter here - let the default close behavior work
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

        // Add keyboard shortcuts for F3 (Find Next) and Shift+F3 (Find Previous)
        getDialogPane().setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case F3 -> {
                    if (event.isShiftDown()) {
                        findPrevious();
                    } else {
                        findNext();
                    }
                    event.consume();
                }
                case ENTER -> {
                    if (event.getSource() == findField) {
                        findNext();
                        event.consume();
                    }
                }
                case ESCAPE -> {
                    close();
                    event.consume();
                }
            }
        });
    }

    /**
     * Sets up behavior to prevent dialog from closing except for Close button.
     */
    private void setupResultConverter() {
        // Prevent dialog from closing by consuming close requests
        setOnCloseRequest(event -> {
            // Allow closing only if explicitly called via close() method
            // This will be triggered by the Close button action
        });
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

        boolean found = false;
        if (matcher.find(fromIndex)) {
            selectAndScrollToMatch(matcher.start(), matcher.end());
            lastFoundIndex = matcher.start();
            found = true;
        } else { // Wrap search
            lastFoundIndex = -1;
            if (matcher.find(0)) {
                selectAndScrollToMatch(matcher.start(), matcher.end());
                lastFoundIndex = matcher.start();
                found = true;
            }
        }

        // Provide visual feedback
        updateSearchStatus(found, searchText);
    }

    private void findPrevious() {
        String searchText = findField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        String content = codeArea.getText();
        int fromIndex = codeArea.getSelection().getStart() - 1;
        if (fromIndex < 0) {
            fromIndex = content.length(); // Start from end for wrap-around
        }

        Pattern pattern = createPattern(searchText);

        // For searching backwards, we need to find all matches up to the current position
        boolean found = false;
        int lastMatchStart = -1;
        int lastMatchEnd = -1;

        Matcher matcher = pattern.matcher(content);

        // Find the last match before the current position
        while (matcher.find() && matcher.start() < fromIndex) {
            lastMatchStart = matcher.start();
            lastMatchEnd = matcher.end();
            found = true;
        }

        // If no match found before current position, wrap to end and find last match
        if (!found) {
            matcher.reset();
            while (matcher.find()) {
                lastMatchStart = matcher.start();
                lastMatchEnd = matcher.end();
                found = true;
            }
        }

        if (found) {
            selectAndScrollToMatch(lastMatchStart, lastMatchEnd);
            lastFoundIndex = lastMatchStart;
        }

        // Provide visual feedback
        updateSearchStatus(found, searchText);
    }

    /**
     * Selects the matched text and scrolls to make it visible
     */
    private void selectAndScrollToMatch(int start, int end) {
        // Select the found text - this sets anchor at start and caret at end
        codeArea.selectRange(start, end);

        // NOTE: Do NOT call moveTo() here! It would clear the selection.
        // selectRange already positions the caret at 'end', which is what we want
        // for "Find Next" to continue from the end of the current match.

        // Scroll to make the selection visible
        codeArea.requestFollowCaret();

        // Temporarily give focus to the code area to ensure highlighting
        codeArea.requestFocus();

        // Return focus to the dialog after a short delay to keep it open and accessible
        javafx.application.Platform.runLater(() -> {
            if (isShowing()) {
                findField.requestFocus();
            }
        });
    }

    /**
     * Updates the search status to provide feedback to the user
     */
    private void updateSearchStatus(boolean found, String searchText) {
        // This could be enhanced with a status label in the UI
        if (found) {
            // Visual confirmation could be added here
            setTitle("Find and Replace - Found: \"" + searchText + "\"");
        } else {
            setTitle("Find and Replace - Not found: \"" + searchText + "\"");
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