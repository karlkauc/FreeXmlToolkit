package org.fxt.freexmltoolkit.plugins.findatex;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A FinDatEx template file read from CSV text: either one instrument per row under a header row of
 * field codes, or transposed (one field per row, its code in the first column, one instrument per
 * column). The separator ({@code ;}, {@code ,}, {@code |} or tab) is detected from the header.
 */
public final class FinDatExFile {

    /**
     * A value with the line it is on.
     */
    public record Cell(String value, int line) {
    }

    /** An instrument (one row, or one column of a transposed file). */
    public record Record(int number, List<Cell> cells) {
    }

    private final FinDatExTemplates.Template template;
    private final List<String> codes;
    private final List<Record> records;
    private final boolean transposed;
    private final char separator;

    private FinDatExFile(FinDatExTemplates.Template template, List<String> codes, List<Record> records,
                         boolean transposed, char separator) {
        this.template = template;
        this.codes = codes;
        this.records = records;
        this.transposed = transposed;
        this.separator = separator;
    }

    /** @return the file, or empty when the text is no FinDatEx template file */
    public static Optional<FinDatExFile> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String start = text.length() > 200_000 ? text.substring(0, 200_000) : text;
        FinDatExTemplates.Template template = null;
        for (FinDatExTemplates.Template t : FinDatExTemplates.all()) {
            if (start.contains(t.versionField())) {
                template = t;
                break;
            }
        }
        if (template == null) {
            return Optional.empty();
        }
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char separator = detectSeparator(firstLine);
        List<List<Cell>> rows = rows(text, separator);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // A header row names several fields; a transposed file has one field code per row, in column 0
        FinDatExTemplates.Template known = template;
        boolean header = rows.getFirst().stream().filter(c -> known.field(c.value().strip()).isPresent()).count() >= 2;
        List<String> codes = new ArrayList<>();
        List<Record> records = new ArrayList<>();
        if (header) {
            rows.getFirst().forEach(c -> codes.add(c.value().strip()));
            for (int r = 1; r < rows.size(); r++) {
                List<Cell> row = rows.get(r);
                if (row.stream().allMatch(c -> c.value().isBlank())) {
                    continue;
                }
                records.add(new Record(records.size() + 1, row));
            }
        } else {
            // Transposed: column 0 holds the codes, columns 1… the instruments
            int width = rows.stream().mapToInt(List::size).max().orElse(0);
            List<List<Cell>> columns = new ArrayList<>();
            for (int c = 1; c < width; c++) {
                columns.add(new ArrayList<>());
            }
            for (List<Cell> row : rows) {
                codes.add(row.isEmpty() ? "" : row.getFirst().value().strip());
                for (int c = 1; c < width; c++) {
                    Cell cell = c < row.size() ? row.get(c) : new Cell("", row.isEmpty() ? 0 : row.getFirst().line());
                    columns.get(c - 1).add(cell);
                }
            }
            for (List<Cell> column : columns) {
                if (column.stream().anyMatch(c -> !c.value().isBlank())) {
                    records.add(new Record(records.size() + 1, column));
                }
            }
        }
        return Optional.of(new FinDatExFile(template, List.copyOf(codes), List.copyOf(records), !header, separator));
    }

    static char detectSeparator(String line) {
        char best = ';';
        long most = -1;
        for (char candidate : new char[] {';', '|', '\t', ','}) {
            long count = line.chars().filter(c -> c == candidate).count();
            if (count > most) {
                most = count;
                best = candidate;
            }
        }
        return best;
    }

    /** RFC 4180 style: quoted values may hold separators, line breaks and doubled quotes. */
    static List<List<Cell>> rows(String text, char separator) {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int valueLine = 1;
        boolean fresh = true; // nothing read for the current value yet
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        value.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    value.append(c);
                }
            } else if (c == '"' && fresh) {
                quoted = true;
                fresh = false;
            } else if (c == separator) {
                row.add(new Cell(value.toString(), valueLine));
                value.setLength(0);
                fresh = true;
                valueLine = line;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(new Cell(value.toString(), valueLine));
                if (!(row.size() == 1 && row.getFirst().value().isEmpty())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                value.setLength(0);
                fresh = true;
                line++;
                valueLine = line;
            } else {
                if (fresh) {
                    valueLine = line;
                }
                value.append(c);
                fresh = false;
            }
        }
        if (!value.isEmpty() || !row.isEmpty()) {
            row.add(new Cell(value.toString(), valueLine));
            rows.add(row);
        }
        return rows;
    }

    public FinDatExTemplates.Template template() {
        return template;
    }

    /** @return the field codes in file order */
    public List<String> codes() {
        return codes;
    }

    public List<Record> records() {
        return records;
    }

    public boolean transposed() {
        return transposed;
    }

    public char separator() {
        return separator;
    }

    /** @return a record's value of a field ({@code ""} when the field or value is missing) */
    public Cell cell(Record record, String code) {
        int index = codes.indexOf(code);
        if (index < 0 || index >= record.cells().size()) {
            return new Cell("", record.cells().isEmpty() ? 0 : record.cells().getFirst().line());
        }
        return record.cells().get(index);
    }
}
