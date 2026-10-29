package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The per-document view state of a grid canvas that must survive the row rebuild that
 * follows every edit, undo or sort: which containers are expanded, which row is selected,
 * the user-dragged name-column widths, and the per-group table/standard and flip choices.
 *
 * <p>Everything is keyed by the rows' <em>stable keys</em> ({@link GridModelAdapter#stableKey}):
 * the positional XPath of an XML node, the path of a JSON node. The key of a block
 * (a container's child rows) is the key of the container; the root block's key is {@code ""}.</p>
 */
public final class GridViewState {

    /** Key of the root block (the rows without a parent). */
    public static final String ROOT_BLOCK = "";

    private final Map<String, Boolean> expanded = new HashMap<>();
    private String selectedKey;
    private final Map<String, Double> nameColumnWidths = new HashMap<>();
    private final Set<String> tablesOff = new HashSet<>();
    private final Set<String> flipped = new HashSet<>();

    // ==================== Expand state ====================

    /** Remembers whether the row with the given key is expanded. */
    public void setExpanded(String key, boolean value) {
        if (key != null) {
            expanded.put(key, value);
        }
    }

    /** @return the remembered expand state, or {@code null} when the key is unknown */
    public Boolean expandedState(String key) {
        return key == null ? null : expanded.get(key);
    }

    /** Forgets every remembered expand state. */
    public void clearExpanded() {
        expanded.clear();
    }

    // ==================== Selection ====================

    /** @return the key of the selected row, or {@code null} */
    public String getSelectedKey() {
        return selectedKey;
    }

    /** @param key the key of the selected row, or {@code null} */
    public void setSelectedKey(String key) {
        this.selectedKey = key;
    }

    // ==================== Name-column widths ====================

    /** @return the user-dragged name-column width of the block, or {@code null} for automatic */
    public Double nameColumnWidth(String blockKey) {
        return nameColumnWidths.get(blockKey == null ? ROOT_BLOCK : blockKey);
    }

    /**
     * Sets or clears the user-dragged name-column width of a block.
     *
     * @param blockKey the block's key ({@code null} = root block)
     * @param width    the width, or {@code null} to return to automatic sizing
     */
    public void setNameColumnWidth(String blockKey, Double width) {
        String key = blockKey == null ? ROOT_BLOCK : blockKey;
        if (width == null) {
            nameColumnWidths.remove(key);
        } else {
            nameColumnWidths.put(key, width);
        }
    }

    /** Returns every block to automatic name-column sizing. */
    public void clearNameColumnWidths() {
        nameColumnWidths.clear();
    }

    /** @return whether any block has a user-dragged name-column width */
    public boolean hasNameColumnWidths() {
        return !nameColumnWidths.isEmpty();
    }

    // ==================== Filters ====================

    private final Map<String, String> filters = new java.util.LinkedHashMap<>();
    private final Set<String> filtersOff = new HashSet<>();

    /** @return the filter expression of the row with the given key, or {@code null} */
    public String filter(String key) {
        return filters.get(key);
    }

    /** Sets ({@code null}/blank = removes) the filter of a row; a new filter starts active. */
    public void setFilter(String key, String expression) {
        if (expression == null || expression.isBlank()) {
            filters.remove(key);
            filtersOff.remove(key);
        } else {
            filters.put(key, expression.strip());
        }
    }

    /** @return the filtered rows' keys and expressions */
    public Map<String, String> filters() {
        return java.util.Collections.unmodifiableMap(filters);
    }

    /** @return whether the filter of the row is switched on */
    public boolean isFilterActive(String key) {
        return filters.containsKey(key) && !filtersOff.contains(key);
    }

    /** Switches the filter of a row on or off (it stays defined). */
    public void setFilterActive(String key, boolean active) {
        if (active) {
            filtersOff.remove(key);
        } else {
            filtersOff.add(key);
        }
    }

    // ==================== Table display per group ====================

    /** @return whether the repeating group with the given key is shown as a table */
    public boolean isTable(String groupKey) {
        return !tablesOff.contains(groupKey);
    }

    /** Shows the group as a table ({@code true}) or as individual numbered rows ({@code false}). */
    public void setTable(String groupKey, boolean table) {
        if (table) {
            tablesOff.remove(groupKey);
        } else {
            tablesOff.add(groupKey);
        }
    }

    /** @return whether the table of the given group is shown with rows and columns flipped */
    public boolean isFlipped(String groupKey) {
        return flipped.contains(groupKey);
    }

    /** Sets whether the group's table is shown flipped. */
    public void setFlipped(String groupKey, boolean value) {
        if (value) {
            flipped.add(groupKey);
        } else {
            flipped.remove(groupKey);
        }
    }
}
