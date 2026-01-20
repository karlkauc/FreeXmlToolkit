package org.fxt.freexmltoolkit.controls.shared.utilities;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Manages the context menu for XML code editing.
 * This class handles context menu creation, styling, and action delegation.
 */
public class XmlContextMenuManager {

    private static final Logger logger = LogManager.getLogger(XmlContextMenuManager.class);

    private final CodeArea codeArea;
    private ContextMenu contextMenu;

    // Action handlers
    private XmlContextActions contextActions;

    /**
     * Interface for handling context menu actions.
     */
    public interface XmlContextActions {
        /** Toggles line comments for the selected lines. */
        void toggleLineComment();

        /** Cuts the selected text to the clipboard. */
        void cutToClipboard();

        /** Copies the selected text to the clipboard. */
        void copyToClipboard();

        /** Pastes text from the clipboard. */
        void pasteFromClipboard();

        /** Copies the XPath of the current element to the clipboard. */
        void copyXPathToClipboard();

        /** Navigates to the definition of the element under cursor. */
        void goToDefinition();

        /** Selects all text in the editor. */
        void selectAllText();

        /** Opens the find and replace dialog. */
        void openFindReplace();

        /** Formats (pretty-prints) the XML content. */
        void formatXmlContent();

        /** Validates the XML content. */
        void validateXmlContent();

        /** Expands all code folds. */
        void expandAllFolds();

        /** Collapses all code folds. */
        void collapseAllFolds();
    }

    /**
     * Constructor for XmlContextMenuManager.
     *
     * @param codeArea The CodeArea to create context menu for
     */
    public XmlContextMenuManager(CodeArea codeArea) {
        this.codeArea = codeArea;
    }

    /**
     * Sets the context actions handler.
     *
     * @param contextActions The context actions handler
     */
    public void setContextActions(XmlContextActions contextActions) {
        this.contextActions = contextActions;
    }

    /**
     * Initializes and sets up the context menu for the code editor.
     */
    public void initializeContextMenu() {
        contextMenu = new ContextMenu();

        // Comment functionality
        MenuItem commentLineMenuItem = new MenuItem("Comment Lines (Ctrl+D)");
        commentLineMenuItem.getStyleClass().add("comment-action");
        commentLineMenuItem.setGraphic(createColoredIcon("bi-chat-square-text", "#6c757d"));
        commentLineMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.toggleLineComment();
            }
        });

        // Standard editing operations
        SeparatorMenuItem separator1 = new SeparatorMenuItem();
        MenuItem cutMenuItem = new MenuItem("Cut (Ctrl+X)");
        cutMenuItem.getStyleClass().add("edit-action");
        cutMenuItem.setGraphic(createColoredIcon("bi-scissors", "#dc3545"));
        cutMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.cutToClipboard();
            }
        });

        MenuItem copyMenuItem = new MenuItem("Copy (Ctrl+C)");
        copyMenuItem.getStyleClass().add("edit-action");
        copyMenuItem.setGraphic(createColoredIcon("bi-files", "#007bff"));
        copyMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.copyToClipboard();
            }
        });

        MenuItem pasteMenuItem = new MenuItem("Paste (Ctrl+V)");
        pasteMenuItem.getStyleClass().add("edit-action");
        pasteMenuItem.setGraphic(createColoredIcon("bi-clipboard", "#28a745"));
        pasteMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.pasteFromClipboard();
            }
        });

        // XML-specific operations
        SeparatorMenuItem separator2 = new SeparatorMenuItem();
        MenuItem copyXPathMenuItem = new MenuItem("Copy XPath");
        copyXPathMenuItem.getStyleClass().add("xml-action");
        copyXPathMenuItem.setGraphic(createColoredIcon("bi-signpost-2", "#ffc107"));
        copyXPathMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.copyXPathToClipboard();
            }
        });

        MenuItem goToDefinitionMenuItem = new MenuItem("Go to Definition (Ctrl+Click)");
        goToDefinitionMenuItem.getStyleClass().add("xml-action");
        goToDefinitionMenuItem.setGraphic(createColoredIcon("bi-box-arrow-up-right", "#17a2b8"));
        goToDefinitionMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.goToDefinition();
            }
        });

        // Selection and search
        SeparatorMenuItem separator3 = new SeparatorMenuItem();
        MenuItem selectAllMenuItem = new MenuItem("Select All (Ctrl+A)");
        selectAllMenuItem.getStyleClass().add("search-action");
        selectAllMenuItem.setGraphic(createColoredIcon("bi-border-all", "#6f42c1"));
        selectAllMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.selectAllText();
            }
        });

        MenuItem findReplaceMenuItem = new MenuItem("Find & Replace (Ctrl+H)");
        findReplaceMenuItem.getStyleClass().add("search-action");
        findReplaceMenuItem.setGraphic(createColoredIcon("bi-search", "#fd7e14"));
        findReplaceMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.openFindReplace();
            }
        });

        // XML formatting and validation
        SeparatorMenuItem separator4 = new SeparatorMenuItem();
        MenuItem formatXmlMenuItem = new MenuItem("Format XML");
        formatXmlMenuItem.getStyleClass().add("format-action");
        formatXmlMenuItem.setGraphic(createColoredIcon("bi-code-square", "#20c997"));
        formatXmlMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.formatXmlContent();
            }
        });

        MenuItem validateXmlMenuItem = new MenuItem("Validate XML");
        validateXmlMenuItem.getStyleClass().add("format-action");
        validateXmlMenuItem.setGraphic(createColoredIcon("bi-check-circle", "#28a745"));
        validateXmlMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.validateXmlContent();
            }
        });

        // Code folding
        SeparatorMenuItem separator5 = new SeparatorMenuItem();
        MenuItem expandAllMenuItem = new MenuItem("Expand All");
        expandAllMenuItem.getStyleClass().add("fold-action");
        expandAllMenuItem.setGraphic(createColoredIcon("bi-arrows-expand", "#6c757d"));
        expandAllMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.expandAllFolds();
            }
        });

        MenuItem collapseAllMenuItem = new MenuItem("Collapse All");
        collapseAllMenuItem.getStyleClass().add("fold-action");
        collapseAllMenuItem.setGraphic(createColoredIcon("bi-arrows-collapse", "#6c757d"));
        collapseAllMenuItem.setOnAction(event -> {
            if (contextActions != null) {
                contextActions.collapseAllFolds();
            }
        });

        // Add all items to context menu
        contextMenu.getItems().addAll(
                commentLineMenuItem,
                separator1,
                cutMenuItem, copyMenuItem, pasteMenuItem,
                separator2,
                copyXPathMenuItem, goToDefinitionMenuItem,
                separator3,
                selectAllMenuItem, findReplaceMenuItem,
                separator4,
                formatXmlMenuItem, validateXmlMenuItem,
                separator5,
                expandAllMenuItem, collapseAllMenuItem
        );

        // Apply uniform font styling to context menu
        contextMenu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

        codeArea.setContextMenu(contextMenu);

        logger.debug("Context menu initialized with comprehensive XML editing functionality");
    }

    /**
     * Creates a colored FontIcon for menu items.
     *
     * @param iconLiteral The icon literal
     * @param color       The color in hex format
     * @return FontIcon with specified color
     */
    private FontIcon createColoredIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        icon.setIconSize(12);
        return icon;
    }

    /**
     * Gets the current context menu.
     *
     * @return The context menu instance
     */
    public ContextMenu getContextMenu() {
        return contextMenu;
    }

    /**
     * Updates the context menu state based on current editor state.
     * This can be used to enable/disable menu items based on context.
     *
     * @param hasSelection Whether text is currently selected
     * @param isXmlContent Whether the current content is XML
     * @param canUndo      Whether undo is available
     * @param canRedo      Whether redo is available
     */
    public void updateContextMenuState(boolean hasSelection, boolean isXmlContent, boolean canUndo, boolean canRedo) {
        if (contextMenu == null) {
            return;
        }

        // Update menu item states based on context
        contextMenu.getItems().forEach(item -> {
            if (item instanceof MenuItem menuItem) {
                String text = menuItem.getText();

                // Enable/disable based on selection
                if (text.startsWith("Cut") || text.startsWith("Copy")) {
                    menuItem.setDisable(!hasSelection && !text.equals("Copy XPath"));
                }

                // Enable/disable XML-specific actions based on content type
                if (text.startsWith("Format XML") || text.startsWith("Validate XML") ||
                        text.startsWith("Copy XPath") || text.startsWith("Go to Definition")) {
                    menuItem.setDisable(!isXmlContent);
                }
            }
        });

        logger.debug("Context menu state updated: hasSelection={}, isXml={}", hasSelection, isXmlContent);
    }

    /**
     * Shows the context menu at the specified coordinates.
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     */
    public void showContextMenu(double screenX, double screenY) {
        if (contextMenu != null) {
            contextMenu.show(codeArea, screenX, screenY);
        }
    }

    /**
     * Hides the context menu if it's currently showing.
     */
    public void hideContextMenu() {
        if (contextMenu != null && contextMenu.isShowing()) {
            contextMenu.hide();
        }
    }

    /**
     * Adds a custom menu item to the context menu.
     *
     * @param menuItem The menu item to add
     * @param position The position to insert at, or -1 to add at the end
     */
    public void addMenuItem(MenuItem menuItem, int position) {
        if (contextMenu != null) {
            if (position >= 0 && position < contextMenu.getItems().size()) {
                contextMenu.getItems().add(position, menuItem);
            } else {
                contextMenu.getItems().add(menuItem);
            }
        }
    }

    /**
     * Removes a menu item from the context menu.
     *
     * @param menuItem The menu item to remove
     */
    public void removeMenuItem(MenuItem menuItem) {
        if (contextMenu != null) {
            contextMenu.getItems().remove(menuItem);
        }
    }

    /**
     * Clears all menu items from the context menu.
     */
    public void clearMenu() {
        if (contextMenu != null) {
            contextMenu.getItems().clear();
        }
    }
}