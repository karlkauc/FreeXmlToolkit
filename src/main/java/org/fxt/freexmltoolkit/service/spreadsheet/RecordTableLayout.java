package org.fxt.freexmltoolkit.service.spreadsheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowData;

/**
 * The "table" layout of the spreadsheet conversion: one row per record (a repeating element such as
 * {@code /Orders/Order}), one column per value inside it ({@code @Id}, {@code Date}, {@code Customer/Name},
 * {@code Item[2]/Qty}). Easier to read and edit than the XPath list (one row per node) for anyone who does not
 * know XML.
 * <p>
 * Works on the rows of {@link org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService#extractRowsFromXml}
 * (full XPaths, so schema typing keeps working per cell) and back to rows that
 * {@link org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService#buildXmlFromRows} turns into XML.
 * Everything outside the records stays in XPath form ({@link Table#otherRows()}), with a placeholder row of
 * type {@value #RECORDS_TYPE} where the records of each parent were, so the way back keeps the order.
 * <p>
 * Not kept by the table: empty elements inside a record (an empty cell means "no element") and the exact
 * order of differently ordered siblings (the columns give one order). The XPath list keeps both.
 */
public final class RecordTableLayout {

    /** Node type of the placeholder row in {@link Table#otherRows()}: "the records of this parent go here". */
    public static final String RECORDS_TYPE = "records";
    /** Column of a record element's own text ({@code <Tag>abc</Tag>} repeated). */
    public static final String OWN_VALUE = "#text";
    /** Column naming each record's parent when the records sit under more than one parent. */
    public static final String PARENT = "Parent";
    /** Records are preferred as default while they have at most this many columns. */
    static final int READABLE_COLUMNS = 60;

    private static final Pattern INDEX = Pattern.compile("\\[\\d+]");
    private static final Pattern STEP = Pattern.compile("(.*?)(\\[(\\d+)])?");

    private RecordTableLayout() {
    }

    /**
     * A repeating element that can be the table's record.
     *
     * @param path    its path without positions, e.g. {@code /Orders/Order}
     * @param count   how many there are
     * @param columns the columns the table would have
     * @param values  the values inside them (how much of the document the table shows)
     */
    public record Candidate(String path, int count, int columns, long values) {

        /** @return the element's name, e.g. {@code Order} */
        public String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    /**
     * One record: its parent's XPath and its values by column.
     *
     * @param parent the parent's XPath with positions, e.g. {@code /Root/Customer[2]}
     * @param cells  column → the row the value came from (full XPath, value, node type)
     */
    public record Record(String parent, Map<String, RowData> cells) {
    }

    /**
     * A document as a table.
     *
     * @param recordPath the record's path without positions
     * @param headers    the value columns, in document order (without {@link #PARENT})
     * @param records    the records, in document order
     * @param otherRows  everything outside the records, with {@value #RECORDS_TYPE} placeholders
     */
    public record Table(String recordPath, List<String> headers, List<Record> records, List<RowData> otherRows) {

        /** @return whether the records sit under more than one parent (then a {@link #PARENT} column is written) */
        public boolean hasParentColumn() {
            Set<String> parents = new HashSet<>();
            for (Record record : records) {
                parents.add(record.parent());
                if (parents.size() > 1) {
                    return true;
                }
            }
            return false;
        }

        /** @return the header row as written: {@link #PARENT} first when needed, then the value columns */
        public List<String> headerRow() {
            List<String> row = new ArrayList<>();
            if (hasParentColumn()) {
                row.add(PARENT);
            }
            row.addAll(headers);
            return row;
        }

        /** @return the cells of one record in {@link #headerRow()} order ({@code null} for no value) */
        public List<RowData> cellsOf(Record record) {
            List<RowData> cells = new ArrayList<>();
            if (hasParentColumn()) {
                cells.add(new RowData(record.parent(), record.parent(), PARENT));
            }
            for (String header : headers) {
                cells.add(record.cells().get(header));
            }
            return cells;
        }

        /** @return whether data outside the records exists (more than the placeholders and empty containers) */
        public boolean hasOtherData() {
            for (RowData row : otherRows) {
                if (!RECORDS_TYPE.equals(row.getNodeType())
                        && !("element".equals(row.getNodeType()) && row.getValue().isEmpty())) {
                    return true;
                }
            }
            return false;
        }
    }

    /** @return {@code xpath} without positions: {@code /a[2]/b[1]/@c} → {@code /a/b/@c} */
    public static String withoutPositions(String xpath) {
        return INDEX.matcher(xpath).replaceAll("");
    }

    /**
     * The repeating elements that can be the record, the default first: the one whose table shows most of the
     * document while staying readable ({@value #READABLE_COLUMNS} columns at most), then by values shown.
     */
    public static List<Candidate> candidates(List<RowData> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RowData row : rows) {
            if ("element".equals(row.getNodeType())) {
                counts.merge(withoutPositions(row.getXpath()), 1, Integer::sum);
            }
        }
        Set<String> repeating = new LinkedHashSet<>();
        counts.forEach((path, count) -> {
            if (count > 1) {
                repeating.add(path);
            }
        });
        if (repeating.isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> columns = new HashMap<>();
        Map<String, Long> values = new HashMap<>();
        for (RowData row : rows) {
            if (!isValue(row)) {
                continue;
            }
            // every repeating ancestor (or the row's own element) that would hold this value
            String xpath = row.getXpath();
            for (int slash = xpath.indexOf('/', 1); ; slash = xpath.indexOf('/', slash + 1)) {
                String prefix = slash < 0 ? xpath : xpath.substring(0, slash);
                String path = withoutPositions(prefix);
                boolean self = slash < 0; // the row's own element: only its text is a value of it
                if (repeating.contains(path) && (!self || "element".equals(row.getNodeType()))) {
                    // "[1]" dropped: a single Item/Qty and Item[1]/Qty are one column (numberRepeatedSteps)
                    String rel = self ? OWN_VALUE : xpath.substring(prefix.length() + 1).replace("[1]", "");
                    columns.computeIfAbsent(path, k -> new HashSet<>()).add(rel);
                    values.merge(path, 1L, Long::sum);
                }
                if (slash < 0) {
                    break;
                }
            }
        }
        List<Candidate> result = new ArrayList<>();
        for (String path : repeating) {
            result.add(new Candidate(path, counts.get(path), columns.getOrDefault(path, Set.of()).size(),
                    values.getOrDefault(path, 0L)));
        }
        result.sort(Comparator.comparing((Candidate c) -> c.columns() > READABLE_COLUMNS || c.columns() == 0)
                .thenComparing(Comparator.comparingLong(Candidate::values).reversed())
                .thenComparing(Comparator.comparingInt(Candidate::count).reversed()));
        return result;
    }

    /** @return the default record path of {@code rows}: the first candidate, or the document element */
    public static String defaultRecordPath(List<RowData> rows) {
        List<Candidate> candidates = candidates(rows);
        if (!candidates.isEmpty()) {
            return candidates.getFirst().path();
        }
        for (RowData row : rows) {
            if ("element".equals(row.getNodeType())) {
                return withoutPositions(row.getXpath());
            }
        }
        return "/Records/Record";
    }

    /** @return whether the row carries a value of its own (attribute, simple element, text, comment, CDATA) */
    static boolean isValue(RowData row) {
        return switch (row.getNodeType()) {
            case "element" -> !row.getValue().isEmpty();
            case "attribute", "text", "comment", "cdata" -> true;
            default -> false;
        };
    }

    /** Builds the table of {@code rows} with {@code recordPath} (no positions) as the record. */
    public static Table toTable(List<RowData> rows, String recordPath) {
        List<Record> records = new ArrayList<>();
        List<RowData> other = new ArrayList<>();
        String current = null;
        Record record = null;
        Set<String> parentsPlaced = new HashSet<>();
        for (RowData row : rows) {
            String xpath = row.getXpath();
            if (current != null && (xpath.equals(current) || xpath.startsWith(current + "/"))) {
                if (!isValue(row)) {
                    continue; // containers inside the record follow from their values
                }
                String key = xpath.equals(current) ? OWN_VALUE : xpath.substring(current.length() + 1);
                if (record.cells().containsKey(key)) {
                    other.add(row); // a second text()/comment() of one element: kept in XPath form
                    continue;
                }
                record.cells().put(key, row);
                continue;
            }
            current = null;
            if ("element".equals(row.getNodeType()) && withoutPositions(xpath).equals(recordPath)) {
                current = xpath;
                int slash = xpath.lastIndexOf('/');
                String parent = slash <= 0 ? "" : xpath.substring(0, slash);
                record = new Record(parent, new LinkedHashMap<>());
                records.add(record);
                if (parentsPlaced.add(parent)) {
                    other.add(new RowData(parent, "", RECORDS_TYPE, row.getOriginalIndex()));
                }
                if (!row.getValue().isEmpty()) {
                    record.cells().put(OWN_VALUE, row);
                }
                continue;
            }
            other.add(row);
        }
        List<Record> numbered = numberRepeatedSteps(records);
        return new Table(recordPath, mergeColumns(numbered), numbered, other);
    }

    /**
     * Gives a step its position when it repeats in some record: one record's single {@code Item/Qty} and
     * another's {@code Item[1]/Qty} are the same column.
     */
    private static List<Record> numberRepeatedSteps(List<Record> records) {
        Set<String> repeating = new HashSet<>();
        for (Record record : records) {
            for (String key : record.cells().keySet()) {
                String[] steps = key.split("/");
                StringBuilder prefix = new StringBuilder();
                for (String step : steps) {
                    prefix.append('/').append(INDEX.matcher(step).replaceAll(""));
                    if (INDEX.matcher(step).find()) {
                        repeating.add(prefix.toString());
                    }
                }
            }
        }
        if (repeating.isEmpty()) {
            return records;
        }
        List<Record> result = new ArrayList<>(records.size());
        for (Record record : records) {
            Map<String, RowData> cells = new LinkedHashMap<>();
            record.cells().forEach((key, row) -> cells.put(numbered(key, repeating), row));
            result.add(new Record(record.parent(), cells));
        }
        return result;
    }

    private static String numbered(String key, Set<String> repeating) {
        if (OWN_VALUE.equals(key)) {
            return key;
        }
        String[] steps = key.split("/");
        StringBuilder prefix = new StringBuilder();
        StringBuilder out = new StringBuilder();
        for (String step : steps) {
            prefix.append('/').append(INDEX.matcher(step).replaceAll(""));
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(step);
            if (repeating.contains(prefix.toString()) && !INDEX.matcher(step).find()) {
                out.append("[1]");
            }
        }
        return out.toString();
    }

    /**
     * One column order for all records: a column first seen in a later record goes right before the next
     * column that record shares with the earlier ones, or to the end, so the columns follow the order the
     * records use even when an early record lacks some values.
     */
    private static List<String> mergeColumns(List<Record> records) {
        List<String> headers = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (Record record : records) {
            List<String> keys = new ArrayList<>(record.cells().keySet());
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                if (!known.add(key)) {
                    continue;
                }
                int at = afterPreviousSibling(headers, key);
                if (at < 0) {
                    at = headers.size();
                    for (int j = i + 1; j < keys.size(); j++) {
                        if (known.contains(keys.get(j))) {
                            at = headers.indexOf(keys.get(j));
                            break;
                        }
                    }
                }
                headers.add(at, key);
            }
        }
        return List.copyOf(headers);
    }

    private static final Pattern LAST_INDEX = Pattern.compile("^(.*)\\[(\\d+)]([^\\[]*)$");

    /**
     * For {@code Amount[2]/@ccy}: the place right after the last column of {@code Amount[1]}, so numbered
     * siblings stand together.
     *
     * @return the insert position, or -1 when the key has no earlier sibling column
     */
    private static int afterPreviousSibling(List<String> headers, String key) {
        String step = key;
        while (true) {
            var m = LAST_INDEX.matcher(step);
            if (!m.matches()) {
                return -1;
            }
            int n = Integer.parseInt(m.group(2));
            if (n > 1) {
                // after the columns of the same numbered step (Amount[2] already placed), else of the one before
                for (int k : new int[]{n, n - 1}) {
                    String group = m.group(1) + "[" + k + "]";
                    int last = -1;
                    for (int i = 0; i < headers.size(); i++) {
                        String h = headers.get(i);
                        if (h.equals(group) || h.startsWith(group + "/")) {
                            last = i;
                        }
                    }
                    if (last >= 0) {
                        return last + 1;
                    }
                }
            }
            step = m.group(1); // an earlier step may be the numbered one: Item[2]/Price/Amount
        }
    }

    // ------------------------------------------------------------------ table → rows

    /**
     * The columns of a table read back: each header as a relative path of valid XML names (a foreign
     * spreadsheet's "Order date" becomes {@code Order_date}, "2024" {@code _2024}), {@code null} for the
     * {@link #PARENT} column and blank headers, a repeated header numbered ({@code Name}, {@code Name[2]}).
     *
     * @param parentColumn the index of the {@link #PARENT} column, or -1
     */
    public static List<String> columnPaths(List<String> headers, int parentColumn) {
        List<String> paths = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i) == null ? "" : headers.get(i).strip();
            if (i == parentColumn || header.isEmpty()) {
                paths.add(null);
                continue;
            }
            String path = OWN_VALUE.equals(header) ? OWN_VALUE : relativePath(header);
            int n = seen.merge(path, 1, Integer::sum);
            if (n > 1 && !path.contains("@") && !path.endsWith("()")) {
                path = path + "[" + n + "]";
            }
            paths.add(path);
        }
        return paths;
    }

    /**
     * The {@link #PARENT} column: a header named so whose values are all XPaths ({@code /…}); a foreign
     * spreadsheet's own "Parent" column with ordinary values stays a value column.
     *
     * @return its index, or -1
     */
    public static int parentColumn(List<String> headers, List<List<String>> values) {
        int index = -1;
        for (int i = 0; i < headers.size(); i++) {
            if (PARENT.equals(headers.get(i) == null ? null : headers.get(i).strip())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return -1;
        }
        for (List<String> row : values) {
            String value = index < row.size() ? row.get(index) : "";
            if (value != null && !value.isBlank() && !value.strip().startsWith("/")) {
                return -1;
            }
        }
        return index;
    }

    /** @return {@code header} as a relative path of valid XML names, keeping {@code @}, positions and node tests */
    static String relativePath(String header) {
        String[] steps = header.split("/");
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < steps.length; i++) {
            String step = steps[i].strip();
            if (step.isEmpty()) {
                continue;
            }
            boolean last = i == steps.length - 1;
            if (path.length() > 0) {
                path.append('/');
            }
            if (last && (step.equals("text()") || step.equals("comment()") || step.equals("cdata()"))) {
                path.append(step);
            } else if (last && step.startsWith("@")) {
                path.append('@').append(xmlName(step.substring(1)));
            } else {
                var m = STEP.matcher(step);
                m.matches();
                path.append(xmlName(m.group(1)));
                if (m.group(3) != null) {
                    path.append('[').append(m.group(3)).append(']');
                }
            }
        }
        return path.length() == 0 ? "_" : path.toString();
    }

    /** @return {@code name} as a valid XML name: invalid characters become {@code _}, a bad start gets one before it */
    public static String xmlName(String name) {
        String s = name.strip();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.'
                    || (c == ':' && i > 0 && i < s.length() - 1 && s.indexOf(':') == i);
            out.append(ok ? c : '_');
        }
        if (out.length() == 0 || !(Character.isLetter(out.charAt(0)) || out.charAt(0) == '_')
                || out.toString().regionMatches(true, 0, "xml", 0, 3) && !out.toString().startsWith("xmlns")) {
            out.insert(0, '_');
        }
        return out.toString();
    }

    /** @return the XPath of the {@code ordinal}-th record (1-based) under {@code parent} */
    public static String instancePath(String recordPath, String parent, int ordinal) {
        String name = recordPath.substring(recordPath.lastIndexOf('/') + 1);
        return parent + "/" + name + "[" + ordinal + "]";
    }

    /** @return the parent records sit under when the table names none: the record path without its last step */
    public static String defaultParent(String recordPath, List<RowData> otherRows) {
        for (RowData row : otherRows) {
            if (RECORDS_TYPE.equals(row.getNodeType())) {
                return row.getXpath();
            }
        }
        int slash = recordPath.lastIndexOf('/');
        return slash <= 0 ? "" : recordPath.substring(0, slash);
    }

    /** @return the full XPath of the value in column {@code path} of the record at {@code instance} */
    public static String cellXPath(String instance, String path) {
        return OWN_VALUE.equals(path) ? instance : instance + "/" + path;
    }

    /** @return the node type of a column: attribute, text, comment, cdata or element */
    public static String nodeTypeOf(String path) {
        int slash = path.lastIndexOf('/');
        String last = path.substring(slash + 1);
        if (last.startsWith("@")) {
            return "attribute";
        }
        return switch (last) {
            case "text()" -> "text";
            case "comment()" -> "comment";
            case "cdata()" -> "cdata";
            default -> "element";
        };
    }

    /**
     * Puts read records back between the other rows: the records of each parent where its placeholder was,
     * records of parents without one (added in the spreadsheet, or a foreign table) at the end. The original
     * indices are renumbered in that order.
     *
     * @param recordsByParent per parent XPath, the records in table order, each as its rows (the record
     *                        element first)
     */
    public static List<RowData> assemble(List<RowData> otherRows, Map<String, List<List<RowData>>> recordsByParent) {
        List<RowData> result = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (RowData row : otherRows) {
            if (RECORDS_TYPE.equals(row.getNodeType())) {
                if (placed.add(row.getXpath())) {
                    recordsByParent.getOrDefault(row.getXpath(), List.of()).forEach(result::addAll);
                }
            } else {
                result.add(row);
            }
        }
        recordsByParent.forEach((parent, records) -> {
            if (placed.add(parent)) {
                records.forEach(result::addAll);
            }
        });
        List<RowData> numbered = new ArrayList<>(result.size());
        int index = 0;
        for (RowData row : result) {
            numbered.add(new RowData(row.getXpath(), row.getValue(), row.getNodeType(), index++));
        }
        return numbered;
    }
}
