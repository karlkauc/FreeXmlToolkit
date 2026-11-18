package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.service.standards.FunctionCatalog;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.sf.saxon.functions.MathFunctionSet;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.XPath20FunctionSet;
import net.sf.saxon.functions.registry.XPath30FunctionSet;
import net.sf.saxon.functions.registry.XPath31FunctionSet;
import net.sf.saxon.functions.registry.XSLT30FunctionSet;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * The function catalog of completion ({@code intellisense/xpath-functions.json}) is generated from Saxon's own function
 * library, so it lists every function the processor knows, with its arities, parameter names and types, the version
 * that introduced it and whether it exists only in XSLT. This test fails when the bundled Saxon knows other functions
 * than the file; {@code -Dfxt.functions.update=true} rewrites the file (descriptions and the hand-written
 * {@code extra} entries, such as the {@code xs:} constructors, are kept).
 *
 * <p>Saxon has no public way to list its functions; reading the private table by reflection is fine in a test, the
 * application only reads the JSON.
 */
class XPathFunctionCatalogTest {

    /** Functions with any number of arguments, which Saxon registers with one parameter. */
    private static final java.util.Set<String> VARIADIC = java.util.Set.of("concat");

    static final Path FILE = Path.of("src/main/resources/intellisense/xpath-functions.json");

    /** Descriptions of functions the hand-written list lacked (kept in the file once written). */
    private static final Map<String, String> NEW_DESCRIPTIONS = Map.ofEntries(
            Map.entry("parse-json", "Parses a JSON string into maps, arrays and atomic values"),
            Map.entry("json-doc", "Reads and parses a JSON file"),
            Map.entry("json-to-xml", "Converts a JSON string to its XML representation (fn: vocabulary)"),
            Map.entry("xml-to-json", "Converts the XML representation of JSON back to a JSON string"),
            Map.entry("analyze-string", "Splits a string into regex matches and non-matches (as XML)"),
            Map.entry("random-number-generator", "Returns a map with a random number and a next() function"),
            Map.entry("transform", "Runs an XSLT transformation from within an expression"),
            Map.entry("load-xquery-module", "Loads an XQuery library module and returns its functions and variables"),
            Map.entry("sort", "Sorts a sequence, optionally by a key function"),
            Map.entry("apply", "Calls a function with the members of an array as arguments"),
            Map.entry("for-each", "Applies a function to every item of a sequence"),
            Map.entry("filter", "Keeps the items for which a function returns true"),
            Map.entry("fold-left", "Accumulates the items of a sequence from the left"),
            Map.entry("fold-right", "Accumulates the items of a sequence from the right"),
            Map.entry("for-each-pair", "Applies a function to pairs of items of two sequences"),
            Map.entry("head", "Returns the first item of a sequence"),
            Map.entry("tail", "Returns all items of a sequence except the first"),
            Map.entry("innermost", "Returns the nodes that have no descendant in the sequence"),
            Map.entry("outermost", "Returns the nodes that have no ancestor in the sequence"),
            Map.entry("path", "Returns the path of a node as an XPath expression"),
            Map.entry("has-children", "Returns true if a node has children"),
            Map.entry("serialize", "Serializes a value to a string, optionally with serialization parameters"),
            Map.entry("parse-xml", "Parses a string as an XML document"),
            Map.entry("parse-xml-fragment", "Parses a string as an XML fragment"),
            Map.entry("parse-ietf-date", "Parses a date in the format of HTTP / e-mail headers"),
            Map.entry("format-integer", "Formats an integer by a picture string (e.g. 'Ww', 'i', '001')"),
            Map.entry("format-date", "Formats a date by a picture string"),
            Map.entry("format-dateTime", "Formats a date and time by a picture string"),
            Map.entry("format-time", "Formats a time by a picture string"),
            Map.entry("unparsed-text", "Reads a text file"),
            Map.entry("unparsed-text-lines", "Reads a text file as a sequence of lines"),
            Map.entry("unparsed-text-available", "Returns true if a text file can be read"),
            Map.entry("environment-variable", "Returns the value of an environment variable"),
            Map.entry("available-environment-variables", "Returns the names of the environment variables"),
            Map.entry("generate-id", "Returns a unique identifier of a node"),
            Map.entry("function-lookup", "Returns the function with a given name and arity"),
            Map.entry("function-name", "Returns the name of a function item"),
            Map.entry("function-arity", "Returns the number of arguments of a function item"),
            Map.entry("collation-key", "Returns a key that sorts like a string under a collation"),
            Map.entry("contains-token", "Returns true if a whitespace-separated list contains a token"),
            Map.entry("trace", "Writes a value to the trace output (shown under the result) and returns it"),
            Map.entry("error", "Raises an error, optionally with a code, a message and a value"),
            Map.entry("current", "XSLT: the item that was the context item outside the current XPath expression"),
            Map.entry("current-group", "XSLT: the items of the current group (xsl:for-each-group)"),
            Map.entry("current-grouping-key", "XSLT: the key of the current group (xsl:for-each-group)"),
            Map.entry("current-merge-group", "XSLT: the items of the current merge group (xsl:merge)"),
            Map.entry("current-merge-key", "XSLT: the key of the current merge group (xsl:merge)"),
            Map.entry("regex-group", "XSLT: a captured group of the current match (xsl:analyze-string)"),
            Map.entry("key", "XSLT: the nodes with a given value of an xsl:key"),
            Map.entry("document", "XSLT: reads documents by URI, relative to the stylesheet"),
            Map.entry("system-property", "XSLT: the value of a system property such as xsl:version"),
            Map.entry("accumulator-before", "XSLT: the value of an accumulator before a node"),
            Map.entry("accumulator-after", "XSLT: the value of an accumulator after a node"),
            Map.entry("copy-of", "XSLT: a deep copy of the items (for streaming)"),
            Map.entry("snapshot", "XSLT: a copy of the items with their ancestors (for streaming)"),
            Map.entry("element-available", "XSLT: true if an instruction is available"),
            Map.entry("function-available", "XSLT: true if a function is available"),
            Map.entry("type-available", "XSLT: true if a type is available"),
            Map.entry("unparsed-entity-uri", "XSLT: the URI of an unparsed entity"),
            Map.entry("unparsed-entity-public-id", "XSLT: the public identifier of an unparsed entity"),
            Map.entry("current-output-uri", "XSLT: the URI of the result document being written"),
            Map.entry("map:entry", "Creates a map with one entry"),
            Map.entry("map:find", "Finds the values of a key in nested maps and arrays"),
            Map.entry("map:for-each", "Applies a function to every key and value of a map"),
            Map.entry("map:size", "Returns the number of entries of a map"),
            Map.entry("array:subarray", "Returns a part of an array"),
            Map.entry("array:head", "Returns the first member of an array"),
            Map.entry("array:tail", "Returns an array without its first member"),
            Map.entry("array:reverse", "Reverses an array"),
            Map.entry("array:insert-before", "Inserts a member into an array"),
            Map.entry("array:remove", "Removes members from an array"),
            Map.entry("array:for-each", "Applies a function to every member of an array"),
            Map.entry("array:filter", "Keeps the members for which a function returns true"),
            Map.entry("array:fold-left", "Accumulates the members of an array from the left"),
            Map.entry("array:fold-right", "Accumulates the members of an array from the right"),
            Map.entry("array:for-each-pair", "Applies a function to pairs of members of two arrays"),
            Map.entry("array:sort", "Sorts the members of an array"),
            Map.entry("array:flatten", "Flattens nested arrays into a sequence"),
            Map.entry("math:pi", "Returns the value of pi"),
            Map.entry("math:sqrt", "Returns the square root"),
            Map.entry("math:pow", "Raises a number to a power"),
            Map.entry("math:exp", "Returns e raised to a power"),
            Map.entry("math:log", "Returns the natural logarithm"),
            Map.entry("math:log10", "Returns the base-10 logarithm"),
            Map.entry("math:sin", "Returns the sine"),
            Map.entry("math:cos", "Returns the cosine"),
            Map.entry("math:tan", "Returns the tangent"),
            Map.entry("math:atan2", "Returns the angle of a point from the x axis"));

    @Test
    void theCatalogListsWhatSaxonKnows() throws Exception {
        String generated = generate();
        if (Boolean.getBoolean("fxt.functions.update")) {
            Files.writeString(FILE, generated, StandardCharsets.UTF_8);
        }
        assertEquals(generated, Files.readString(FILE, StandardCharsets.UTF_8),
                "Saxon's function library changed: run with -Dfxt.functions.update=true and review the diff");
    }

    static String generate() throws Exception {
        JsonObject current = JsonParser.parseString(Files.readString(seedFile(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        Map<String, JsonObject> known = new HashMap<>();
        for (String part : List.of("functions", "extra")) {
            if (current.has(part)) {
                for (JsonElement e : current.getAsJsonArray(part)) {
                    known.put(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject());
                }
            }
        }

        Map<String, Entry> functions = new TreeMap<>();
        collect(functions, XPath31FunctionSet.getInstance(), false);
        collect(functions, MapFunctionSet.getInstance(31), false);
        collect(functions, ArrayFunctionSet.getInstance(31), false);
        collect(functions, MathFunctionSet.getInstance(), false);
        collect(functions, XSLT30FunctionSet.getInstance(), true);
        java.util.Set<String> v20 = names(XPath20FunctionSet.getInstance());
        java.util.Set<String> v30 = names(XPath30FunctionSet.getInstance());

        JsonArray out = new JsonArray();
        for (Entry f : functions.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", f.name);
            boolean variadic = VARIADIC.contains(f.name);
            o.addProperty("min", variadic ? 2 : f.min);
            o.addProperty("max", variadic ? -1 : f.max); // -1: any number of further arguments
            JsonArray params = new JsonArray();
            for (int i = 0; i < f.params.size(); i++) {
                JsonObject p = new JsonObject();
                p.addProperty("name", f.params.get(i));
                p.addProperty("type", f.types.get(i));
                params.add(p);
            }
            o.add("params", params);
            o.addProperty("returns", f.returns);
            o.addProperty("since", f.xsltOnly ? "3.0" : since(f, v20, v30));
            o.addProperty("xsltOnly", f.xsltOnly);
            JsonObject before = known.get(f.name);
            String description = before != null && before.has("description")
                    ? before.get("description").getAsString() : NEW_DESCRIPTIONS.getOrDefault(f.name, "");
            o.addProperty("description", description);
            out.add(o);
        }
        JsonArray extra = new JsonArray();
        if (current.has("extra")) {
            for (JsonElement e : current.getAsJsonArray("extra")) {
                if (!functions.containsKey(e.getAsJsonObject().get("name").getAsString())) {
                    extra.add(e);
                }
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("generatedFrom", "Saxon-HE " + net.sf.saxon.Version.getProductVersion());
        root.add("functions", out);
        root.add("extra", extra);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(root) + "\n";
    }

    /** The file to merge descriptions from: the catalog itself, or a seed given by {@code -Dfxt.functions.seed}. */
    private static Path seedFile() {
        String seed = System.getProperty("fxt.functions.seed");
        return seed != null ? Path.of(seed) : FILE;
    }

    private static String since(Entry f, java.util.Set<String> v20, java.util.Set<String> v30) {
        if (f.name.contains(":")) {
            return f.name.startsWith("math:") ? "3.0" : "3.1";
        }
        if (v20.contains(f.name)) {
            return FunctionCatalog.exists(XPathVersion.V1_0, f.name, VARIADIC.contains(f.name) ? 2 : f.min)
                    ? "1.0" : "2.0";
        }
        return v30.contains(f.name) ? "3.0" : "3.1";
    }

    private static final class Entry {
        String name;
        int min = Integer.MAX_VALUE;
        int max = -1;
        List<String> params = new ArrayList<>();
        List<String> types = new ArrayList<>();
        String returns;
        boolean xsltOnly;
    }

    private static Collection<BuiltInFunctionSet.Entry> entries(BuiltInFunctionSet set) throws Exception {
        Field table = BuiltInFunctionSet.class.getDeclaredField("functionTable");
        table.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, BuiltInFunctionSet.Entry> map = (Map<String, BuiltInFunctionSet.Entry>) table.get(set);
        return map.values();
    }

    private static java.util.Set<String> names(BuiltInFunctionSet set) throws Exception {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (BuiltInFunctionSet.Entry e : entries(set)) {
            names.add(e.name.getLocalPart());
        }
        return names;
    }

    private static void collect(Map<String, Entry> into, BuiltInFunctionSet set, boolean xslt) throws Exception {
        String prefix = set.getConventionalPrefix();
        boolean fn = prefix == null || prefix.isEmpty() || "fn".equals(prefix);
        for (BuiltInFunctionSet.Entry e : entries(set)) {
            String local = e.name.getLocalPart();
            String name = fn ? local : prefix + ":" + local;
            if (local.startsWith("_")) {
                continue; // internal functions of the processor
            }
            if (xslt && into.containsKey(name)) {
                continue; // an XPath function the XSLT set imports
            }
            e.ensurePopulated();
            Entry f = into.computeIfAbsent(name, k -> {
                Entry created = new Entry();
                created.name = k;
                created.xsltOnly = xslt;
                return created;
            });
            f.min = Math.min(f.min, e.minArity);
            if (e.maxArity > f.max) {
                f.max = e.maxArity;
                f.params = new ArrayList<>();
                f.types = new ArrayList<>();
                int shown = Math.min(e.maxArity, e.paramTypes == null ? 0 : e.paramTypes.length);
                for (int i = 0; i < shown; i++) {
                    f.params.add(e.paramNames != null && i < e.paramNames.length && e.paramNames[i] != null
                            ? e.paramNames[i] : "arg" + (i + 1));
                    f.types.add(type(e.paramTypes[i]));
                }
                f.returns = e.itemType == null ? "item()*"
                        : e.itemType + Cardinality.getOccurrenceIndicator(e.cardinality);
            }
        }
    }

    private static String type(SequenceType type) {
        return type == null ? "item()*" : type.toString();
    }
}
