package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.theme.ActionColor;

/**
 * Side panel of an activity whose content is a page in the main editor area (Settings, Schema
 * Library): a list of the page's sections. A click jumps to the section; the section shown on
 * the page is marked ({@code fxt-nav-current}). An optional filter field narrows the list by
 * label and keywords and reports the query, so the page can filter its own content as well.
 */
public final class PageNavigator extends VBox {

    /** Style class of the row of the section currently shown on the page. */
    static final String CURRENT_STYLE_CLASS = "fxt-nav-current";

    /**
     * One section of the page.
     *
     * @param key         stable identifier, passed to the selection callback
     * @param label       row text
     * @param iconLiteral a bundled Bootstrap icon name ({@code bi-*})
     * @param keywords    extra words the filter matches (e.g. the settings inside a section)
     */
    public record Entry(String key, String label, String iconLiteral, String keywords) {
    }

    private final Map<String, PanelActionButton> rows = new LinkedHashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final TextField filter;
    private Consumer<String> onFilter = query -> { };

    /**
     * @param idPrefix   prefix of the node ids ({@code <prefix>-nav-<key>}, {@code <prefix>-nav-filter})
     * @param title      panel title (upper case, like every side panel)
     * @param hint       one sentence above the list, or {@code null}
     * @param sections   the page's sections, in page order
     * @param onSelect   called with the key of a clicked section
     * @param searchable whether to show the filter field
     */
    public PageNavigator(String idPrefix, String title, String hint, List<Entry> sections,
                         Consumer<String> onSelect, boolean searchable) {
        getStyleClass().add("fxt-page-navigator");
        getChildren().add(SidePanelHeader.create(title));
        VBox body = new VBox(8);
        body.getStyleClass().add("fxt-side-panel-body");
        if (hint != null && !hint.isBlank()) {
            Label hintLabel = new Label(hint);
            hintLabel.setWrapText(true);
            hintLabel.getStyleClass().add("fxt-placeholder-text");
            body.getChildren().add(hintLabel);
        }
        if (searchable) {
            filter = new TextField();
            filter.setId(idPrefix + "-nav-filter");
            filter.setPromptText("Search " + title.toLowerCase(Locale.ROOT) + "…");
            filter.textProperty().addListener((obs, oldV, query) -> applyFilter(query));
            body.getChildren().add(filter);
        } else {
            filter = null;
        }
        PanelActionList list = new PanelActionList();
        for (Entry entry : sections) {
            PanelActionButton row = new PanelActionButton();
            row.setId(idPrefix + "-nav-" + entry.key());
            row.setText(entry.label());
            row.setColor(ActionColor.NAVIGATE);
            row.setIconLiteral(entry.iconLiteral());
            row.managedProperty().bind(row.visibleProperty());
            row.setOnAction(e -> onSelect.accept(entry.key()));
            rows.put(entry.key(), row);
            entries.put(entry.key(), entry);
            list.getChildren().add(row);
        }
        body.getChildren().add(list);
        getChildren().add(body);
    }

    /** Marks the section currently shown on the page ({@code null} clears the mark). */
    public void setCurrent(String key) {
        rows.forEach((k, row) -> {
            row.getStyleClass().remove(CURRENT_STYLE_CLASS);
            if (k.equals(key)) {
                row.getStyleClass().add(CURRENT_STYLE_CLASS);
            }
        });
    }

    /** Called with the (trimmed, lower-case) filter text whenever it changes. */
    public void setOnFilter(Consumer<String> handler) {
        this.onFilter = handler != null ? handler : query -> { };
    }

    /** @return whether a section matches the filter text (empty text matches everything) */
    public boolean matches(String key, String query) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        String q = normalize(query);
        return q.isEmpty() || (entry.label() + " " + entry.keywords()).toLowerCase(Locale.ROOT).contains(q);
    }

    /** @return the filter field, or {@code null} without one */
    public TextField filterField() {
        return filter;
    }

    /** @return the keys of the rows currently listed (for tests) */
    List<String> visibleKeys() {
        List<String> keys = new ArrayList<>();
        rows.forEach((k, row) -> {
            if (row.isVisible()) {
                keys.add(k);
            }
        });
        return keys;
    }

    /** @return the key of the marked section, or {@code null} (for tests) */
    String currentKey() {
        return rows.entrySet().stream().filter(e -> e.getValue().getStyleClass().contains(CURRENT_STYLE_CLASS))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private void applyFilter(String query) {
        rows.forEach((k, row) -> row.setVisible(matches(k, query)));
        onFilter.accept(normalize(query));
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }
}
