package org.fxt.freexmltoolkit.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Change;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Difference;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.NodeKind;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Result;
import org.fxt.freexmltoolkit.service.yaml.YamlDocument;
import org.fxt.freexmltoolkit.util.JsonPointerLocator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Compares two JSON or YAML documents by their data (a JSON file against a YAML file too): member
 * order, formatting, comments, quoting styles and YAML anchors do not count. Arrays are compared
 * in order with a longest-common-subsequence alignment (an inserted item is one difference, not a
 * cascade), or matched by key members ({@code id}), or regardless of order. The result has the
 * shape of {@link XmlStructuralDiff}'s, with JSONPath locations ({@code $.jobs.build.steps[2]}) in
 * the XPath fields, so the Compare view and {@code fxt-cli compare} show both alike. UI-free.
 */
public final class DataStructuralDiff {

    /** The data format of one side. */
    public enum Format { JSON, YAML }

    /**
     * Comparison options.
     *
     * @param ignoreArrayOrder items of arrays are matched regardless of their position
     * @param keyMembers       members that identify an object among the items of an array (e.g. {@code id})
     * @param numericTolerance numbers that differ by at most this are equal ({@code 0} = exact)
     */
    public record Options(boolean ignoreArrayOrder, List<String> keyMembers, double numericTolerance) {

        /** In order, no keys, exact numbers. */
        public static final Options DEFAULTS = new Options(false, List.of(), 0);

        public Options {
            keyMembers = keyMembers == null ? List.of() : List.copyOf(keyMembers);
            numericTolerance = Double.isFinite(numericTolerance) && numericTolerance > 0 ? numericTolerance : 0;
        }

        /** The data counterpart of XML options: element order → array order, key attributes → key members. */
        public static Options of(XmlStructuralDiff.Options xml) {
            return new Options(xml.ignoreElementOrder(), xml.keyAttributes(), xml.numericTolerance());
        }
    }

    /** Arrays longer than this on both sides are aligned by position only (the alignment is quadratic). */
    static final int MAX_ALIGNED_ITEMS = 3000;

    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private DataStructuralDiff() {
    }

    /**
     * @param fileName a file name, may be null
     * @param text     its content (used when the name says nothing)
     * @return the data format, {@code null} when the document is neither JSON nor YAML by its name
     *         and does not start like JSON
     */
    public static Format formatOf(String fileName, String text) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json") || name.endsWith(".json5") || name.endsWith(".jsonc")) {
            return Format.JSON;
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return Format.YAML;
        }
        if (name.contains(".")) {
            return null;
        }
        String start = text == null ? "" : text.stripLeading();
        return start.startsWith("{") || start.startsWith("[") ? Format.JSON : null;
    }

    /**
     * @return the differences in document order of the left side; parse errors as
     *         {@link Result#leftError()} / {@link Result#rightError()}
     */
    public static Result compare(String left, Format leftFormat, String right, Format rightFormat, Options options) {
        Side l = Side.read(left, leftFormat);
        Side r = Side.read(right, rightFormat);
        if (l.error != null || r.error != null) {
            return new Result(List.of(), l.error, r.error);
        }
        List<Difference> out = new ArrayList<>();
        new Walker(l, r, options == null ? Options.DEFAULTS : options, out).compare(l.data, r.data,
                new ArrayList<>(), new ArrayList<>(), null);
        return new Result(List.copyOf(out), null, null);
    }

    /** One parsed side: its data and how to find a path's line. */
    private static final class Side {
        JsonElement data;
        String error;
        YamlDocument yaml;
        JsonPointerLocator json;

        static Side read(String text, Format format) {
            Side side = new Side();
            if (format == Format.YAML) {
                YamlDocument document = YamlDocument.parse(text == null ? "" : text);
                if (!document.isValid()) {
                    YamlDocument.Problem p = document.problem();
                    side.error = "Line " + p.line() + ", column " + p.column() + ": " + p.message();
                } else {
                    side.yaml = document;
                    side.data = document.json() == null ? JsonNull.INSTANCE : document.json();
                }
                return side;
            }
            try {
                side.data = JsonParser.parseString(text == null ? "" : text);
                side.json = new JsonPointerLocator(text);
            } catch (JsonParseException | IllegalStateException e) {
                side.error = e.getMessage() == null ? "Not valid JSON" : e.getMessage();
            }
            return side;
        }

        int lineOf(List<Object> path) {
            if (yaml != null) {
                return yaml.lineOf(path);
            }
            for (int n = path.size(); n >= 0; n--) {
                int line = json.lineOf(path.subList(0, n));
                if (line > 0) {
                    return line;
                }
            }
            return 0;
        }
    }

    private record Walker(Side left, Side right, Options options, List<Difference> out) {

        void compare(JsonElement l, JsonElement r, List<Object> lp, List<Object> rp, Object name) {
            if (l.isJsonObject() && r.isJsonObject()) {
                compareObjects(l.getAsJsonObject(), r.getAsJsonObject(), lp, rp);
            } else if (l.isJsonArray() && r.isJsonArray()) {
                compareArrays(l.getAsJsonArray(), r.getAsJsonArray(), lp, rp);
            } else if (!same(l, r)) {
                out.add(difference(Change.CHANGED, lp, rp, name, l, r));
            }
        }

        private void compareObjects(JsonObject l, JsonObject r, List<Object> lp, List<Object> rp) {
            for (Map.Entry<String, JsonElement> e : l.entrySet()) {
                JsonElement other = r.get(e.getKey());
                if (other == null) {
                    out.add(difference(Change.REMOVED, with(lp, e.getKey()), rp, e.getKey(), e.getValue(), null));
                } else {
                    compare(e.getValue(), other, with(lp, e.getKey()), with(rp, e.getKey()), e.getKey());
                }
            }
            for (Map.Entry<String, JsonElement> e : r.entrySet()) {
                if (!l.has(e.getKey())) {
                    out.add(difference(Change.ADDED, lp, with(rp, e.getKey()), e.getKey(), null, e.getValue()));
                }
            }
        }

        private void compareArrays(JsonArray l, JsonArray r, List<Object> lp, List<Object> rp) {
            int[] pairOf = keyed(l, r);
            if (pairOf == null && options.ignoreArrayOrder()) {
                pairOf = unordered(l, r);
            }
            if (pairOf == null) {
                pairOf = aligned(l, r);
            }
            boolean[] used = new boolean[r.size()];
            int lastRight = -1;
            for (int i = 0; i < l.size(); i++) {
                int j = pairOf[i];
                if (j < 0) {
                    out.add(difference(Change.REMOVED, with(lp, i), rp, i, l.get(i), null));
                    continue;
                }
                used[j] = true;
                if (!options.ignoreArrayOrder() && j < lastRight && !same(l.get(i), r.get(j))) {
                    // matched by key out of order: reported where it is, with its content compared
                    compare(l.get(i), r.get(j), with(lp, i), with(rp, j), i);
                } else if (!options.ignoreArrayOrder() && j < lastRight) {
                    out.add(new Difference(Change.MOVED, NodeKind.ITEM, String.valueOf(i),
                            YamlDocument.toJsonPath(with(lp, i)), YamlDocument.toJsonPath(with(rp, j)),
                            "", "", left.lineOf(with(lp, i)), right.lineOf(with(rp, j)), true));
                } else {
                    compare(l.get(i), r.get(j), with(lp, i), with(rp, j), i);
                }
                lastRight = Math.max(lastRight, j);
            }
            for (int j = 0; j < r.size(); j++) {
                if (!used[j]) {
                    out.add(difference(Change.ADDED, lp, with(rp, j), j, null, r.get(j)));
                }
            }
        }

        /** Items paired by the first key member both objects have; {@code null} when no key applies. */
        private int[] keyed(JsonArray l, JsonArray r) {
            String key = null;
            for (String candidate : options.keyMembers()) {
                if (allHave(l, candidate) && allHave(r, candidate)) {
                    key = candidate;
                    break;
                }
            }
            if (key == null) {
                return null;
            }
            Map<String, List<Integer>> rightByKey = new HashMap<>();
            for (int j = 0; j < r.size(); j++) {
                rightByKey.computeIfAbsent(canonical(r.get(j).getAsJsonObject().get(key)), k -> new ArrayList<>()).add(j);
            }
            int[] pairOf = new int[l.size()];
            for (int i = 0; i < l.size(); i++) {
                List<Integer> candidates = rightByKey.get(canonical(l.get(i).getAsJsonObject().get(key)));
                pairOf[i] = candidates == null || candidates.isEmpty() ? -1 : candidates.removeFirst();
            }
            return pairOf;
        }

        private static boolean allHave(JsonArray array, String key) {
            if (array.isEmpty()) {
                return true;
            }
            for (JsonElement item : array) {
                if (!item.isJsonObject() || !item.getAsJsonObject().has(key)) {
                    return false;
                }
            }
            return true;
        }

        /** Equal items paired first, the rest by their position among the unpaired ones. */
        private int[] unordered(JsonArray l, JsonArray r) {
            Map<String, List<Integer>> rightByValue = new HashMap<>();
            for (int j = 0; j < r.size(); j++) {
                rightByValue.computeIfAbsent(canonical(r.get(j)), k -> new ArrayList<>()).add(j);
            }
            int[] pairOf = new int[l.size()];
            boolean[] used = new boolean[r.size()];
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) {
                List<Integer> candidates = rightByValue.get(canonical(l.get(i)));
                if (candidates != null && !candidates.isEmpty()) {
                    pairOf[i] = candidates.removeFirst();
                    used[pairOf[i]] = true;
                } else {
                    pairOf[i] = -1;
                    open.add(i);
                }
            }
            int j = 0;
            for (int i : open) {
                while (j < r.size() && used[j]) {
                    j++;
                }
                if (j < r.size() && sameShape(l.get(i), r.get(j))) {
                    pairOf[i] = j;
                    used[j] = true;
                }
            }
            return pairOf;
        }

        /**
         * In order: equal items aligned by a longest common subsequence; between two aligned items the
         * remaining ones are paired by position (compared member by member), the surplus is added or removed.
         */
        private int[] aligned(JsonArray l, JsonArray r) {
            int n = l.size();
            int m = r.size();
            int[] pairOf = new int[n];
            java.util.Arrays.fill(pairOf, -1);
            if ((long) n * m > (long) MAX_ALIGNED_ITEMS * MAX_ALIGNED_ITEMS / 4) {
                for (int i = 0; i < Math.min(n, m); i++) {
                    pairOf[i] = i;
                }
                return pairOf;
            }
            String[] a = new String[n];
            String[] b = new String[m];
            for (int i = 0; i < n; i++) {
                a[i] = canonical(l.get(i));
            }
            for (int j = 0; j < m; j++) {
                b[j] = canonical(r.get(j));
            }
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lcs[i][j] = a[i].equals(b[j]) || sameNumbers(l.get(i), r.get(j))
                            ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            List<Integer> gapLeft = new ArrayList<>();
            List<Integer> gapRight = new ArrayList<>();
            while (i < n || j < m) {
                if (i < n && j < m && (a[i].equals(b[j]) || sameNumbers(l.get(i), r.get(j)))) {
                    pairGap(gapLeft, gapRight, pairOf, l, r);
                    pairOf[i++] = j++;
                } else if (j >= m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1])) {
                    gapLeft.add(i++);
                } else {
                    gapRight.add(j++);
                }
            }
            pairGap(gapLeft, gapRight, pairOf, l, r);
            return pairOf;
        }

        /** Pairs the items of a gap by position while they have the same shape (both objects, …). */
        private static void pairGap(List<Integer> gapLeft, List<Integer> gapRight, int[] pairOf,
                                    JsonArray l, JsonArray r) {
            for (int k = 0; k < Math.min(gapLeft.size(), gapRight.size()); k++) {
                if (sameShape(l.get(gapLeft.get(k)), r.get(gapRight.get(k)))) {
                    pairOf[gapLeft.get(k)] = gapRight.get(k);
                }
            }
            gapLeft.clear();
            gapRight.clear();
        }

        private static boolean sameShape(JsonElement a, JsonElement b) {
            return a.isJsonObject() == b.isJsonObject() && a.isJsonArray() == b.isJsonArray();
        }

        private boolean same(JsonElement a, JsonElement b) {
            return canonical(a).equals(canonical(b)) || sameNumbers(a, b);
        }

        private boolean sameNumbers(JsonElement a, JsonElement b) {
            if (!(a instanceof JsonPrimitive pa) || !(b instanceof JsonPrimitive pb) || !pa.isNumber() || !pb.isNumber()) {
                return false;
            }
            try {
                BigDecimal x = new BigDecimal(pa.getAsString());
                BigDecimal y = new BigDecimal(pb.getAsString());
                return x.compareTo(y) == 0 || (options.numericTolerance() > 0
                        && x.subtract(y).abs().compareTo(BigDecimal.valueOf(options.numericTolerance())) <= 0);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private Difference difference(Change change, List<Object> lp, List<Object> rp, Object name,
                                      JsonElement l, JsonElement r) {
            NodeKind kind = name instanceof Integer ? NodeKind.ITEM : name == null ? NodeKind.DOCUMENT : NodeKind.MEMBER;
            String label = name == null ? "Document" : String.valueOf(name);
            return new Difference(change, kind, label, YamlDocument.toJsonPath(lp), YamlDocument.toJsonPath(rp),
                    display(l), display(r), left.lineOf(lp), right.lineOf(rp));
        }
    }

    /** Key-order-independent text of a value (objects with sorted members), for equality and matching. */
    static String canonical(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonObject()) {
            StringBuilder out = new StringBuilder("{");
            Set<String> keys = new java.util.TreeSet<>(value.getAsJsonObject().keySet());
            for (String key : keys) {
                out.append(COMPACT.toJson(key)).append(':').append(canonical(value.getAsJsonObject().get(key))).append(',');
            }
            return out.append('}').toString();
        }
        if (value.isJsonArray()) {
            StringBuilder out = new StringBuilder("[");
            for (JsonElement item : value.getAsJsonArray()) {
                out.append(canonical(item)).append(',');
            }
            return out.append(']').toString();
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        if (p.isNumber()) {
            try {
                return "n:" + new BigDecimal(p.getAsString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return "n:" + p.getAsString();
            }
        }
        return COMPACT.toJson(p);
    }

    /** A value as compact JSON (strings without quotes), shortened like the XML comparison's. */
    static String display(JsonElement value) {
        if (value == null) {
            return "";
        }
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : COMPACT.toJson(value);
        String oneLine = text.replace("\r", "\\r").replace("\n", "\\n");
        return oneLine.length() > XmlStructuralDiff.MAX_VALUE_LENGTH
                ? oneLine.substring(0, XmlStructuralDiff.MAX_VALUE_LENGTH) + "…" : oneLine;
    }

    private static List<Object> with(List<Object> path, Object segment) {
        List<Object> next = new ArrayList<>(path.size() + 1);
        next.addAll(path);
        next.add(segment);
        return next;
    }
}
