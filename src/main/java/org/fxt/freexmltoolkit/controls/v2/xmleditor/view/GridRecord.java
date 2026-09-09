package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.List;
import java.util.Map;

/**
 * Model-agnostic data source for one row of a {@link RepeatingElementsTable}.
 *
 * <p>The XMLSpy-style grid renders repeating siblings (XML elements with the same tag name,
 * JSON arrays of objects) as an embedded table. The table itself only needs, per record, the
 * ordered column keys, the cell texts, and which cells hide a nested structure that can be
 * expanded inline. Implementations: {@link XmlGridRecord} (one {@code XmlElement}) and the
 * JSON grid's record over one {@code JsonObject}.</p>
 */
public interface GridRecord {

    /** @return the underlying model node this record represents (XmlElement / JsonObject) */
    Object node();

    /**
     * Ordered column keys in document order. Keys are used to merge the column order across
     * all records and as the column-order cache key; they may carry a model-specific prefix
     * (e.g. {@code "@id"} for an XML attribute) that {@link #columnName(String)} strips.
     */
    List<String> columnKeys();

    /** @return the display/lookup name of the column for the given key (e.g. {@code "@id"} → {@code "id"}) */
    String columnName(String key);

    /** @return the kind of column the given key produces */
    RepeatingElementsTable.ColumnType columnType(String key);

    /** @return cell text per column name (only for columns this record has a value for) */
    Map<String, String> values();

    /** @return nested model nodes per column name for cells that can be expanded inline */
    Map<String, Object> complexChildren();

    /** @return display-only suffixes per column name (e.g. {@code ccy=EUR}); may be empty */
    Map<String, String> attributeSuffixes();

    /**
     * Flattens the nested node of a complex cell into rows for inline expansion.
     *
     * @param columnName the column whose complex child should be flattened
     * @return the flat rows (empty when the column has no complex child)
     */
    List<FlatRow> flattenComplexChild(String columnName);
}
