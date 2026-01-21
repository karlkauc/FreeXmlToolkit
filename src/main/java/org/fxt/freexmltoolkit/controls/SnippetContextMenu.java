package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Context menu integration for one-click XPath/XQuery snippet execution.
 * Provides smart context-aware snippet suggestions based on cursor position and selected text.
 */
public class SnippetContextMenu {

    private final XPathSnippetRepository snippetRepository;
    private final ContextMenu contextMenu;
    private BiConsumer<XPathSnippet, String> executeCallback;
    private Consumer<String> insertCallback;

    /**
     * Creates a new snippet context menu with default configuration.
     * Initializes the snippet repository and builds the menu structure with
     * favorite and popular snippets, category-based submenus, and insert options.
     */
    public SnippetContextMenu() {
        this.snippetRepository = XPathSnippetRepository.getInstance();
        this.contextMenu = new ContextMenu();
        buildMenu();
    }

    private void buildMenu() {
        contextMenu.getItems().clear();

        // Quick Execute Section
        Menu quickExecuteMenu = new Menu("Quick Execute XPath");
        quickExecuteMenu.setGraphic(createIcon("⚡"));

        // Get popular/favorite snippets for quick access
        List<XPathSnippet> favoriteSnippets = snippetRepository.getFavoriteSnippets();
        List<XPathSnippet> popularSnippets = snippetRepository.getPopularSnippets();

        // Add favorite snippets
        if (!favoriteSnippets.isEmpty()) {
            quickExecuteMenu.getItems().add(new SeparatorMenuItem());
            Label favHeader = new Label("★ Favorites");
            favHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: gold;");
            CustomMenuItem favHeaderItem = new CustomMenuItem(favHeader);
            favHeaderItem.setHideOnClick(false);
            quickExecuteMenu.getItems().add(favHeaderItem);

            for (XPathSnippet snippet : favoriteSnippets.subList(0, Math.min(5, favoriteSnippets.size()))) {
                MenuItem item = createSnippetMenuItem(snippet);
                quickExecuteMenu.getItems().add(item);
            }
        }

        // Add popular snippets
        if (!popularSnippets.isEmpty()) {
            quickExecuteMenu.getItems().add(new SeparatorMenuItem());
            Label popularHeader = new Label("🔥 Popular");
            popularHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: orange;");
            CustomMenuItem popularHeaderItem = new CustomMenuItem(popularHeader);
            popularHeaderItem.setHideOnClick(false);
            quickExecuteMenu.getItems().add(popularHeaderItem);

            for (XPathSnippet snippet : popularSnippets.subList(0, Math.min(5, popularSnippets.size()))) {
                MenuItem item = createSnippetMenuItem(snippet);
                quickExecuteMenu.getItems().add(item);
            }
        }

        contextMenu.getItems().add(quickExecuteMenu);

        // Category-based snippets
        addCategoryMenus();

        // Separator
        contextMenu.getItems().add(new SeparatorMenuItem());

        // Insert snippet query (without execution)
        Menu insertMenu = new Menu("Insert XPath Expression");
        insertMenu.setGraphic(createIcon("📝"));

        // Common XPath patterns for insertion
        addInsertSnippets(insertMenu);

        contextMenu.getItems().add(insertMenu);

        // Separator
        contextMenu.getItems().add(new SeparatorMenuItem());

        // Open snippet manager
        MenuItem openManager = new MenuItem("Open Snippet Manager...");
        openManager.setGraphic(createIcon("⚙"));
        openManager.setAccelerator(new KeyCodeCombination(KeyCode.F3, KeyCombination.CONTROL_DOWN));
        openManager.setOnAction(e -> {
            if (insertCallback != null) {
                insertCallback.accept("OPEN_SNIPPET_MANAGER");
            }
        });
        contextMenu.getItems().add(openManager);
    }

    private void addCategoryMenus() {
        for (XPathSnippet.SnippetCategory category : XPathSnippet.SnippetCategory.values()) {
            List<XPathSnippet> categorySnippets = snippetRepository.getSnippetsByCategory(category);
            if (!categorySnippets.isEmpty()) {
                Menu categoryMenu = new Menu(category.getDisplayName());
                categoryMenu.setGraphic(createIcon(getCategoryIcon(category)));

                for (XPathSnippet snippet : categorySnippets.subList(0, Math.min(8, categorySnippets.size()))) {
                    MenuItem item = createSnippetMenuItem(snippet);
                    categoryMenu.getItems().add(item);
                }

                if (categorySnippets.size() > 8) {
                    categoryMenu.getItems().add(new SeparatorMenuItem());
                    MenuItem moreItem = new MenuItem("Show all " + categorySnippets.size() + " " + category.getDisplayName().toLowerCase() + "...");
                    moreItem.setOnAction(e -> {
                        if (insertCallback != null) {
                            insertCallback.accept("SHOW_CATEGORY:" + category.name());
                        }
                    });
                    categoryMenu.getItems().add(moreItem);
                }

                contextMenu.getItems().add(categoryMenu);
            }
        }
    }

    private void addInsertSnippets(Menu insertMenu) {
        // Common XPath expressions for quick insertion
        String[][] insertSnippets = {
                {"Get element text", "//elementName/text()"},
                {"Get attribute value", "//@attributeName"},
                {"Find by attribute", "//element[@attribute='value']"},
                {"Get all children", "//parent/*"},
                {"Count elements", "count(//element)"},
                {"Get first element", "(//element)[1]"},
                {"Get last element", "(//element)[last()]"},
                {"Contains text", "//element[contains(text(), 'searchText')]"},
                {"Not empty", "//element[text()]"},
                {"Has attribute", "//element[@attribute]"},
                {"Following sibling", "//element/following-sibling::*"},
                {"Preceding sibling", "//element/preceding-sibling::*"},
                {"Parent element", "//element/parent::*"},
                {"Ancestor element", "//element/ancestor::*"}
        };

        for (String[] snippetData : insertSnippets) {
            MenuItem item = new MenuItem(snippetData[0]);
            item.setOnAction(e -> {
                if (insertCallback != null) {
                    insertCallback.accept(snippetData[1]);
                }
            });
            insertMenu.getItems().add(item);
        }
    }

    private MenuItem createSnippetMenuItem(XPathSnippet snippet) {
        MenuItem item = new MenuItem(snippet.getName());

        // Add visual indicators
        String prefix = "";
        if (snippet.isFavorite()) prefix += "★ ";
        if (snippet.getUsageCount() > 10) prefix += "🔥 ";

        item.setText(prefix + snippet.getName());
        item.setGraphic(createIcon(getTypeIcon(snippet.getType())));

        // Tooltip with snippet details
        Tooltip tooltip = new Tooltip(
                snippet.getDescription() + "\n\n" +
                        "Query: " + snippet.getQuery() + "\n" +
                        "Category: " + snippet.getCategory().getDisplayName() + "\n" +
                        "Usage: " + snippet.getUsageCount() + " times"
        );
        Tooltip.install(item.getGraphic(), tooltip);

        // Execute action
        item.setOnAction(e -> {
            if (executeCallback != null) {
                executeCallback.accept(snippet, snippet.getQuery());
            }
        });

        // Add keyboard shortcut for popular snippets
        if (snippet.getUsageCount() > 20) {
            char accelerator = snippet.getName().charAt(0);
            item.setAccelerator(new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(accelerator).toUpperCase())));
        }

        return item;
    }

    private Label createIcon(String iconText) {
        Label icon = new Label(iconText);
        icon.setStyle("-fx-font-size: 12px;");
        return icon;
    }

    private String getCategoryIcon(XPathSnippet.SnippetCategory category) {
        return switch (category) {
            case NAVIGATION -> "🧭";
            case EXTRACTION -> "📝";
            case FILTERING -> "🔍";
            case TRANSFORMATION -> "⚙";
            case VALIDATION -> "✓";
            case ANALYSIS -> "🌐";
            case UTILITY -> "⚡";
            case CUSTOM -> "👤";
        };
    }

    private String getTypeIcon(XPathSnippet.SnippetType type) {
        return switch (type) {
            case XPATH -> "📍";
            case XQUERY -> "📊";
            case XPATH_FUNCTION -> "⚙";
            case XQUERY_MODULE -> "📦";
            case FLWOR -> "🔄";
            case TEMPLATE -> "📋";
        };
    }

    /**
     * Shows the context menu with context-aware suggestions at the specified screen position.
     * Updates the menu with intelligent suggestions based on the selected text and XML context
     * before displaying.
     *
     * @param node         the control node to anchor the context menu to
     * @param screenX      the X coordinate on screen where the menu should appear
     * @param screenY      the Y coordinate on screen where the menu should appear
     * @param selectedText the currently selected text in the editor, may be null or empty
     * @param xmlContext   the surrounding XML context for intelligent suggestions
     */
    public void showContextMenu(Control node, double screenX, double screenY, String selectedText, String xmlContext) {
        // Update menu with context-aware suggestions
        updateContextAwareSuggestions(selectedText, xmlContext);

        contextMenu.show(node, screenX, screenY);
    }

    private void updateContextAwareSuggestions(String selectedText, String xmlContext) {
        // TODO: Implement intelligent context-aware suggestions based on:
        // - Selected text (element name, attribute, etc.)
        // - Cursor position in XML structure
        // - Nearby XML elements and attributes
        // - Previous user patterns

        if (selectedText != null && !selectedText.trim().isEmpty()) {
            // Add dynamic menu for selected text
            Menu selectedTextMenu = new Menu("XPath for '" + selectedText + "'");
            selectedTextMenu.setGraphic(createIcon("🎯"));

            // Generate context-specific XPath suggestions
            String[] suggestions = generateContextSuggestions(selectedText, xmlContext);
            for (String suggestion : suggestions) {
                MenuItem item = new MenuItem("Execute: " + suggestion);
                item.setOnAction(e -> {
                    if (insertCallback != null) {
                        insertCallback.accept(suggestion);
                    }
                });
                selectedTextMenu.getItems().add(item);
            }

            // Add to top of menu
            contextMenu.getItems().add(0, selectedTextMenu);
            contextMenu.getItems().add(1, new SeparatorMenuItem());
        }
    }

    private String[] generateContextSuggestions(String selectedText, String xmlContext) {
        // Smart context-aware XPath generation
        return new String[]{
                "//text()[contains(., '" + selectedText + "')]",
                "//*[text()='" + selectedText + "']",
                "//@*[.='" + selectedText + "']",
                "//" + selectedText,
                "//" + selectedText + "/text()",
                "count(//" + selectedText + ")"
        };
    }

    /**
     * Sets the callback to be invoked when a snippet is executed from the context menu.
     * The callback receives the selected snippet and its query string.
     *
     * @param callback the bi-consumer callback that handles snippet execution,
     *                 receiving the XPathSnippet and its query string
     */
    public void setExecuteCallback(BiConsumer<XPathSnippet, String> callback) {
        this.executeCallback = callback;
    }

    /**
     * Sets the callback to be invoked when an XPath expression should be inserted into the editor.
     * This is used for insert operations that do not immediately execute the snippet.
     *
     * @param callback the consumer callback that handles text insertion,
     *                 receiving the XPath expression string to insert
     */
    public void setInsertCallback(Consumer<String> callback) {
        this.insertCallback = callback;
    }

    /**
     * Returns the underlying JavaFX ContextMenu instance.
     * Can be used to attach the menu to controls or for additional customization.
     *
     * @return the ContextMenu instance managed by this SnippetContextMenu
     */
    public ContextMenu getContextMenu() {
        return contextMenu;
    }

    /**
     * Hides the context menu if it is currently showing.
     * This method delegates to the underlying ContextMenu's hide method.
     */
    public void hide() {
        contextMenu.hide();
    }

    /**
     * Refreshes the menu items by rebuilding the entire menu structure.
     * This should be called when the snippet repository has been modified
     * to ensure the menu reflects the latest snippets, favorites, and usage statistics.
     */
    public void refresh() {
        buildMenu();
    }
}