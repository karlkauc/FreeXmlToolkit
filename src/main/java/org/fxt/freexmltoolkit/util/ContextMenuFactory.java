package org.fxt.freexmltoolkit.util;

import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating consistent, modern context menus across the application.
 *
 * <p>Provides factory methods for:
 * <ul>
 *   <li>Menu items with icons and semantic colors</li>
 *   <li>Common actions (add, edit, delete, copy, etc.) with pre-styled icons</li>
 *   <li>Submenus with icons</li>
 *   <li>Separators and headers</li>
 *   <li>Consistent CSS class application</li>
 * </ul>
 *
 * <p>All menus use the unified theme from context-menu-theme.css for visual consistency.
 *
 * @since 2.0
 */
public class ContextMenuFactory {

    private static final Logger logger = LoggerFactory.getLogger(ContextMenuFactory.class);

    /** Semantic color: Green. */
    public static final String COLOR_SUCCESS = "#28a745";
    /** Semantic color: Red. */
    public static final String COLOR_DANGER = "#dc3545";
    /** Semantic color: Yellow/Orange. */
    public static final String COLOR_WARNING = "#ffc107";
    /** Semantic color: Cyan. */
    public static final String COLOR_INFO = "#17a2b8";
    /** Semantic color: Blue. */
    public static final String COLOR_PRIMARY = "#007bff";
    /** Semantic color: Gray. */
    public static final String COLOR_SECONDARY = "#6c757d";
    /** Semantic color: Orange. */
    public static final String COLOR_ORANGE = "#fd7e14";
    /** Semantic color: Purple. */
    public static final String COLOR_PURPLE = "#6f42c1";
    /** Semantic color: Teal. */
    public static final String COLOR_TEAL = "#20c997";
    /** Semantic color: Indigo. */
    public static final String COLOR_INDIGO = "#6610f2";

    /**
     * Standard icon size for menu items (12px as per design system)
     */
    public static final int ICON_SIZE = 12;

    private ContextMenuFactory() {
        // Utility class
    }

    // ============================================
    // CONTEXT MENU CREATION
    // ============================================

    /**
     * Creates a new context menu with the unified theme.
     *
     * <p>Note: The context-menu-theme.css stylesheet should be loaded at the
     * application or scene level for styling to take effect.
     *
     * @return the context menu
     */
    public static ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("context-menu");
        menu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");
        return menu;
    }

    /**
     * Creates a context menu with XML/XSD editor specific styling.
     *
     * @param editorType "xml" or "xsd"
     * @return the context menu
     */
    public static ContextMenu createEditorContextMenu(String editorType) {
        ContextMenu menu = createContextMenu();
        menu.getStyleClass().add(editorType + "-context-menu");
        return menu;
    }

    // ============================================
    // MENU ITEMS
    // ============================================

    /**
     * Creates a menu item with text only.
     *
     * @param text the menu item text
     * @param action the action to execute
     * @return the menu item
     */
    public static MenuItem createItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        return item;
    }

    /**
     * Creates a menu item with text, icon, and action.
     *
     * @param text the menu item text
     * @param iconLiteral the Bootstrap icon literal (e.g., "bi-plus-circle")
     * @param iconColor the icon color (hex string)
     * @param action the action to execute
     * @return the menu item
     */
    public static MenuItem createItem(String text, String iconLiteral, String iconColor, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setGraphic(createIcon(iconLiteral, iconColor));
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        return item;
    }

    /**
     * Creates a menu item with text, icon, accelerator, and action.
     *
     * @param text the menu item text
     * @param iconLiteral the Bootstrap icon literal
     * @param iconColor the icon color (hex string)
     * @param accelerator the keyboard shortcut text (e.g., "Ctrl+N")
     * @param action the action to execute
     * @return the menu item
     */
    public static MenuItem createItem(String text, String iconLiteral, String iconColor,
                                     String accelerator, Runnable action) {
        MenuItem item = createItem(text, iconLiteral, iconColor, action);
        if (accelerator != null && !accelerator.isEmpty()) {
            item.setText(text + "\t" + accelerator);
        }
        return item;
    }

    /**
     * Creates a disabled menu item (always disabled, for display purposes).
     *
     * @param text the menu item text
     * @return the disabled menu item
     */
    public static MenuItem createDisabledItem(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    /**
     * Creates a disabled menu item with icon.
     *
     * @param text the menu item text
     * @param iconLiteral the Bootstrap icon literal
     * @param iconColor the icon color (hex string)
     * @return the disabled menu item
     */
    public static MenuItem createDisabledItem(String text, String iconLiteral, String iconColor) {
        MenuItem item = createItem(text, iconLiteral, iconColor, null);
        item.setDisable(true);
        return item;
    }

    // ============================================
    // COMMON ACTION ITEMS (Pre-styled)
    // ============================================

    /**
     * Creates an "Add" menu item with green icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createAddItem(String text, Runnable action) {
        return createItem(text, "bi-plus-circle", COLOR_SUCCESS, action);
    }

    /**
     * Creates an "Edit" menu item with orange icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createEditItem(String text, Runnable action) {
        return createItem(text, "bi-pencil", COLOR_ORANGE, action);
    }

    /**
     * Creates a "Delete" menu item with red icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createDeleteItem(String text, Runnable action) {
        return createItem(text, "bi-trash", COLOR_DANGER, action);
    }

    /**
     * Creates a "Copy" / "Duplicate" menu item with teal icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createCopyItem(String text, Runnable action) {
        return createItem(text, "bi-files", COLOR_TEAL, action);
    }

    /**
     * Creates a "Rename" menu item with orange icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createRenameItem(String text, Runnable action) {
        return createItem(text, "bi-pencil-square", COLOR_ORANGE, action);
    }

    /**
     * Creates a "Change" / "Modify" menu item with blue icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createChangeItem(String text, Runnable action) {
        return createItem(text, "bi-arrow-left-right", COLOR_PRIMARY, action);
    }

    /**
     * Creates an "Open" menu item with primary blue icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createOpenItem(String text, Runnable action) {
        return createItem(text, "bi-folder2-open", COLOR_PRIMARY, action);
    }

    /**
     * Creates a "Save" menu item with green icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createSaveItem(String text, Runnable action) {
        return createItem(text, "bi-save", COLOR_SUCCESS, action);
    }

    /**
     * Creates an "Info" / "Properties" menu item with cyan icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createInfoItem(String text, Runnable action) {
        return createItem(text, "bi-info-circle", COLOR_INFO, action);
    }

    /**
     * Creates a "Settings" menu item with secondary gray icon.
     *
     * @param text The menu item text.
     * @param action The action to execute.
     * @return The created menu item.
     */
    public static MenuItem createSettingsItem(String text, Runnable action) {
        return createItem(text, "bi-gear", COLOR_SECONDARY, action);
    }

    // ============================================
    // SUBMENUS
    // ============================================

    /**
     * Creates a submenu (Menu) with text only.
     *
     * @param text the submenu text
     * @return the submenu
     */
    public static Menu createSubmenu(String text) {
        return new Menu(text);
    }

    /**
     * Creates a submenu with icon.
     *
     * @param text the submenu text
     * @param iconLiteral the Bootstrap icon literal
     * @param iconColor the icon color (hex string)
     * @return the submenu
     */
    public static Menu createSubmenu(String text, String iconLiteral, String iconColor) {
        Menu menu = new Menu(text);
        menu.setGraphic(createIcon(iconLiteral, iconColor));
        return menu;
    }

    /**
     * Creates an "Add" submenu with green icon.
     *
     * @param text The submenu text.
     * @return The created submenu.
     */
    public static Menu createAddSubmenu(String text) {
        return createSubmenu(text, "bi-plus-circle", COLOR_SUCCESS);
    }

    // ============================================
    // CHECK AND RADIO ITEMS
    // ============================================

    /**
     * Creates a check menu item.
     *
     * @param text the menu item text
     * @param selected initial selected state
     * @param action the action to execute when toggled
     * @return the check menu item
     */
    public static CheckMenuItem createCheckItem(String text, boolean selected, Runnable action) {
        CheckMenuItem item = new CheckMenuItem(text);
        item.setSelected(selected);
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        return item;
    }

    /**
     * Creates a radio menu item.
     *
     * @param text the menu item text
     * @param toggleGroup the toggle group (for mutual exclusivity)
     * @param selected initial selected state
     * @param action the action to execute when selected
     * @return the radio menu item
     */
    public static RadioMenuItem createRadioItem(String text, ToggleGroup toggleGroup,
                                               boolean selected, Runnable action) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(toggleGroup);
        item.setSelected(selected);
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        return item;
    }

    // ============================================
    // SEPARATORS AND HEADERS
    // ============================================

    /**
     * Creates a separator menu item.
     *
     * @return the separator
     */
    public static SeparatorMenuItem createSeparator() {
        return new SeparatorMenuItem();
    }

    /**
     * Creates a header menu item (non-interactive label).
     *
     * @param text the header text
     * @return the header as a disabled menu item
     */
    public static MenuItem createHeader(String text) {
        MenuItem header = new MenuItem(text);
        header.setDisable(true);
        header.getStyleClass().add("menu-header");
        return header;
    }

    // ============================================
    // CUSTOM MENU ITEMS
    // ============================================

    /**
     * Creates a custom menu item with a custom graphic node.
     *
     * @param text the menu item text
     * @param graphic the custom graphic node
     * @param action the action to execute
     * @return the menu item
     */
    public static MenuItem createCustomItem(String text, javafx.scene.Node graphic, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setGraphic(graphic);
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        return item;
    }

    // ============================================
    // ICON HELPERS
    // ============================================

    /**
     * Creates a FontIcon with the specified icon literal and color.
     *
     * @param iconLiteral the Bootstrap icon literal (e.g., "bi-plus-circle")
     * @param iconColor the icon color (hex string, e.g., "#28a745")
     * @return the configured FontIcon
     */
    public static FontIcon createIcon(String iconLiteral, String iconColor) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(ICON_SIZE);
        if (iconColor != null && !iconColor.isEmpty()) {
            icon.setIconColor(Color.web(iconColor));
        }
        return icon;
    }

    /**
     * Creates a FontIcon with default secondary color.
     *
     * @param iconLiteral the Bootstrap icon literal
     * @return the configured FontIcon
     */
    public static FontIcon createIcon(String iconLiteral) {
        return createIcon(iconLiteral, COLOR_SECONDARY);
    }

    /**
     * Creates a success (green) icon.
     *
     * @param iconLiteral The icon literal.
     * @return The configured FontIcon.
     */
    public static FontIcon createSuccessIcon(String iconLiteral) {
        return createIcon(iconLiteral, COLOR_SUCCESS);
    }

    /**
     * Creates a danger (red) icon.
     *
     * @param iconLiteral The icon literal.
     * @return The configured FontIcon.
     */
    public static FontIcon createDangerIcon(String iconLiteral) {
        return createIcon(iconLiteral, COLOR_DANGER);
    }

    /**
     * Creates a warning (yellow) icon.
     *
     * @param iconLiteral The icon literal.
     * @return The configured FontIcon.
     */
    public static FontIcon createWarningIcon(String iconLiteral) {
        return createIcon(iconLiteral, COLOR_WARNING);
    }

    /**
     * Creates an info (cyan) icon.
     *
     * @param iconLiteral The icon literal.
     * @return The configured FontIcon.
     */
    public static FontIcon createInfoIcon(String iconLiteral) {
        return createIcon(iconLiteral, COLOR_INFO);
    }

    /**
     * Creates a primary (blue) icon.
     *
     * @param iconLiteral The icon literal.
     * @return The configured FontIcon.
     */
    public static FontIcon createPrimaryIcon(String iconLiteral) {
        return createIcon(iconLiteral, COLOR_PRIMARY);
    }

    // ============================================
    // BUILDER PATTERN
    // ============================================

    /**
     * Builder for creating context menus with fluent API.
     *
     * <p>Example usage:
     * <pre>{@code
     * ContextMenu menu = ContextMenuFactory.builder()
     *     .addItem("New", "bi-file-plus", COLOR_SUCCESS, this::handleNew)
     *     .addSeparator()
     *     .addItem("Delete", "bi-trash", COLOR_DANGER, this::handleDelete)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private final ContextMenu menu;

        private Builder() {
            this.menu = createContextMenu();
        }

        /**
         * Adds an item with text.
         *
         * @param text The item text.
         * @param action The action.
         * @return The builder.
         */
        public Builder addItem(String text, Runnable action) {
            menu.getItems().add(createItem(text, action));
            return this;
        }

        /**
         * Adds an item with icon.
         *
         * @param text The item text.
         * @param iconLiteral The icon literal.
         * @param iconColor The icon color.
         * @param action The action.
         * @return The builder.
         */
        public Builder addItem(String text, String iconLiteral, String iconColor, Runnable action) {
            menu.getItems().add(createItem(text, iconLiteral, iconColor, action));
            return this;
        }

        /**
         * Adds a custom menu item.
         *
         * @param item The menu item.
         * @return The builder.
         */
        public Builder addItem(MenuItem item) {
            menu.getItems().add(item);
            return this;
        }

        /**
         * Adds an "Add" item.
         *
         * @param text The item text.
         * @param action The action.
         * @return The builder.
         */
        public Builder addAddItem(String text, Runnable action) {
            menu.getItems().add(createAddItem(text, action));
            return this;
        }

        /**
         * Adds an "Edit" item.
         *
         * @param text The item text.
         * @param action The action.
         * @return The builder.
         */
        public Builder addEditItem(String text, Runnable action) {
            menu.getItems().add(createEditItem(text, action));
            return this;
        }

        /**
         * Adds a "Delete" item.
         *
         * @param text The item text.
         * @param action The action.
         * @return The builder.
         */
        public Builder addDeleteItem(String text, Runnable action) {
            menu.getItems().add(createDeleteItem(text, action));
            return this;
        }

        /**
         * Adds a "Copy" item.
         *
         * @param text The item text.
         * @param action The action.
         * @return The builder.
         */
        public Builder addCopyItem(String text, Runnable action) {
            menu.getItems().add(createCopyItem(text, action));
            return this;
        }

        /**
         * Adds a submenu.
         *
         * @param submenu The submenu.
         * @return The builder.
         */
        public Builder addSubmenu(Menu submenu) {
            menu.getItems().add(submenu);
            return this;
        }

        /**
         * Adds a separator.
         *
         * @return The builder.
         */
        public Builder addSeparator() {
            menu.getItems().add(createSeparator());
            return this;
        }

        /**
         * Adds a header item.
         *
         * @param text The header text.
         * @return The builder.
         */
        public Builder addHeader(String text) {
            menu.getItems().add(createHeader(text));
            return this;
        }

        /**
         * Adds a check item.
         *
         * @param text The item text.
         * @param selected Initial selection state.
         * @param action The action.
         * @return The builder.
         */
        public Builder addCheckItem(String text, boolean selected, Runnable action) {
            menu.getItems().add(createCheckItem(text, selected, action));
            return this;
        }

        /**
         * Builds the context menu.
         *
         * @return The built ContextMenu.
         */
        public ContextMenu build() {
            return menu;
        }
    }

    /**
     * Creates a new context menu builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
