package org.fxt.freexmltoolkit.controls.unified;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A search bar component for the Unified Editor.
 * Provides find and replace functionality for text editors.
 */
public class UnifiedSearchBar extends HBox {

    private static final Logger logger = LogManager.getLogger(UnifiedSearchBar.class);

    private final TextField searchField;
    private final TextField replaceField;
    private final Label statusLabel;
    private final HBox replaceBox;
    private final Button toggleReplaceButton;

    private XmlCodeEditorV2 currentEditor;
    private CodeArea currentCodeArea;
    private Runnable onCloseCallback;

    private boolean replaceMode = false;

    public UnifiedSearchBar() {
        setSpacing(8);
        setPadding(new Insets(6, 10, 6, 10));
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("search-bar");
        setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border; -fx-border-width: 0 0 1 0;");

        // Status label (initialize early as it's used in searchField listener)
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: -fx-mid-text-color;");
        statusLabel.setMinWidth(80);

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setPrefWidth(250);
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    findPrevious();
                } else {
                    findNext();
                }
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hide();
            }
        });
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                updateSearchCount(newVal);
            } else {
                statusLabel.setText("");
            }
        });

        // Find Previous button
        Button findPrevButton = new Button();
        FontIcon prevIcon = new FontIcon("bi-chevron-up");
        prevIcon.setIconSize(14);
        findPrevButton.setGraphic(prevIcon);
        findPrevButton.setTooltip(new Tooltip("Find Previous (Shift+Enter)"));
        findPrevButton.setOnAction(e -> findPrevious());

        // Find Next button
        Button findNextButton = new Button();
        FontIcon nextIcon = new FontIcon("bi-chevron-down");
        nextIcon.setIconSize(14);
        findNextButton.setGraphic(nextIcon);
        findNextButton.setTooltip(new Tooltip("Find Next (Enter)"));
        findNextButton.setOnAction(e -> findNext());

        // Toggle Replace button
        toggleReplaceButton = new Button();
        FontIcon replaceIcon = new FontIcon("bi-arrow-left-right");
        replaceIcon.setIconSize(14);
        toggleReplaceButton.setGraphic(replaceIcon);
        toggleReplaceButton.setTooltip(new Tooltip("Toggle Replace (Ctrl+H)"));
        toggleReplaceButton.setOnAction(e -> toggleReplaceMode());

        // Close button
        Button closeButton = new Button();
        FontIcon closeIcon = new FontIcon("bi-x");
        closeIcon.setIconSize(14);
        closeButton.setGraphic(closeIcon);
        closeButton.setTooltip(new Tooltip("Close (Escape)"));
        closeButton.setOnAction(e -> hide());

        // Replace field and buttons (hidden by default)
        replaceField = new TextField();
        replaceField.setPromptText("Replace with...");
        replaceField.setPrefWidth(200);
        replaceField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                replaceNext();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hide();
            }
        });

        Button replaceButton = new Button("Replace");
        replaceButton.setOnAction(e -> replaceNext());
        replaceButton.setTooltip(new Tooltip("Replace current match"));

        Button replaceAllButton = new Button("All");
        replaceAllButton.setOnAction(e -> replaceAll());
        replaceAllButton.setTooltip(new Tooltip("Replace all matches"));

        replaceBox = new HBox(6, replaceField, replaceButton, replaceAllButton);
        replaceBox.setAlignment(Pos.CENTER_LEFT);
        replaceBox.setVisible(false);
        replaceBox.setManaged(false);

        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(
                searchField,
                findPrevButton,
                findNextButton,
                toggleReplaceButton,
                replaceBox,
                spacer,
                statusLabel,
                closeButton
        );

        // Initially hidden
        setVisible(false);
        setManaged(false);
    }

    /**
     * Sets the current editor to search in.
     */
    public void setCurrentEditor(XmlCodeEditorV2 editor) {
        this.currentEditor = editor;
        this.currentCodeArea = editor != null ? editor.getCodeArea() : null;
    }

    /**
     * Sets the current CodeArea to search in (for editors without XmlCodeEditorV2).
     */
    public void setCurrentCodeArea(CodeArea codeArea) {
        this.currentCodeArea = codeArea;
        this.currentEditor = null;
    }

    /**
     * Shows the search bar and focuses the search field.
     */
    public void show() {
        setVisible(true);
        setManaged(true);
        searchField.requestFocus();
        searchField.selectAll();
    }

    /**
     * Shows the search bar in replace mode.
     */
    public void showReplace() {
        show();
        if (!replaceMode) {
            toggleReplaceMode();
        }
        replaceField.requestFocus();
    }

    /**
     * Hides the search bar.
     */
    public void hide() {
        setVisible(false);
        setManaged(false);
        if (currentEditor != null) {
            currentEditor.clearSearch();
        }
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    /**
     * Sets a callback to run when the search bar is closed.
     */
    public void setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
    }

    private void toggleReplaceMode() {
        replaceMode = !replaceMode;
        replaceBox.setVisible(replaceMode);
        replaceBox.setManaged(replaceMode);
        if (replaceMode) {
            replaceField.requestFocus();
        }
    }

    private void findNext() {
        String text = searchField.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        boolean found = false;
        if (currentEditor != null) {
            found = currentEditor.find(text, true);
        } else if (currentCodeArea != null) {
            found = findInCodeArea(text, true);
        }

        if (!found) {
            statusLabel.setText("Not found");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
        } else {
            updateSearchCount(text);
        }
    }

    private void findPrevious() {
        String text = searchField.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        boolean found = false;
        if (currentEditor != null) {
            found = currentEditor.find(text, false);
        } else if (currentCodeArea != null) {
            found = findInCodeArea(text, false);
        }

        if (!found) {
            statusLabel.setText("Not found");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
        } else {
            updateSearchCount(text);
        }
    }

    private void replaceNext() {
        String findText = searchField.getText();
        String replaceText = replaceField.getText();
        if (findText == null || findText.isEmpty()) {
            return;
        }

        boolean replaced = false;
        if (currentEditor != null) {
            replaced = currentEditor.replace(findText, replaceText);
        } else if (currentCodeArea != null) {
            replaced = replaceInCodeArea(findText, replaceText);
        }

        if (replaced) {
            updateSearchCount(findText);
            // Find next occurrence
            findNext();
        }
    }

    private void replaceAll() {
        String findText = searchField.getText();
        String replaceText = replaceField.getText();
        if (findText == null || findText.isEmpty()) {
            return;
        }

        int count = 0;
        if (currentEditor != null) {
            count = currentEditor.replaceAll(findText, replaceText);
        } else if (currentCodeArea != null) {
            count = replaceAllInCodeArea(findText, replaceText);
        }

        statusLabel.setText(count + " replaced");
        statusLabel.setStyle("-fx-text-fill: #28a745;");
    }

    private void updateSearchCount(String text) {
        if (text == null || text.isEmpty()) {
            statusLabel.setText("");
            return;
        }

        int count = 0;
        if (currentEditor != null) {
            count = currentEditor.findAll(text);
        } else if (currentCodeArea != null) {
            String content = currentCodeArea.getText();
            if (content != null) {
                String lower = content.toLowerCase();
                String lowerText = text.toLowerCase();
                int pos = 0;
                while ((pos = lower.indexOf(lowerText, pos)) >= 0) {
                    count++;
                    pos += text.length();
                }
            }
        }

        if (count > 0) {
            statusLabel.setText(count + " found");
            statusLabel.setStyle("-fx-text-fill: -fx-mid-text-color;");
        } else {
            statusLabel.setText("Not found");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
        }
    }

    // Fallback methods for direct CodeArea usage
    private boolean findInCodeArea(String text, boolean forward) {
        if (currentCodeArea == null) return false;

        String content = currentCodeArea.getText();
        if (content == null) return false;

        int currentPos = currentCodeArea.getCaretPosition();
        String lowerContent = content.toLowerCase();
        String lowerText = text.toLowerCase();

        int foundPos;
        if (forward) {
            foundPos = lowerContent.indexOf(lowerText, currentPos);
            if (foundPos < 0) {
                foundPos = lowerContent.indexOf(lowerText);
            }
        } else {
            foundPos = lowerContent.lastIndexOf(lowerText, currentPos - 1);
            if (foundPos < 0) {
                foundPos = lowerContent.lastIndexOf(lowerText);
            }
        }

        if (foundPos >= 0) {
            currentCodeArea.selectRange(foundPos, foundPos + text.length());
            currentCodeArea.requestFollowCaret();
            return true;
        }
        return false;
    }

    private boolean replaceInCodeArea(String findText, String replaceText) {
        if (currentCodeArea == null) return false;

        String selected = currentCodeArea.getSelectedText();
        if (selected != null && selected.equalsIgnoreCase(findText)) {
            currentCodeArea.replaceSelection(replaceText != null ? replaceText : "");
            return true;
        } else if (findInCodeArea(findText, true)) {
            currentCodeArea.replaceSelection(replaceText != null ? replaceText : "");
            return true;
        }
        return false;
    }

    private int replaceAllInCodeArea(String findText, String replaceText) {
        if (currentCodeArea == null) return 0;

        String content = currentCodeArea.getText();
        if (content == null) return 0;

        String lowerContent = content.toLowerCase();
        String lowerFind = findText.toLowerCase();

        int count = 0;
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        int pos = 0;

        while ((pos = lowerContent.indexOf(lowerFind, pos)) >= 0) {
            result.append(content, lastEnd, pos);
            result.append(replaceText != null ? replaceText : "");
            lastEnd = pos + findText.length();
            pos = lastEnd;
            count++;
        }

        if (count > 0) {
            result.append(content.substring(lastEnd));
            currentCodeArea.replaceText(result.toString());
        }

        return count;
    }

    /**
     * Sets the initial search text.
     */
    public void setSearchText(String text) {
        searchField.setText(text);
    }

    /**
     * Gets the search field for external focus management.
     */
    public TextField getSearchField() {
        return searchField;
    }
}
