package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaReference;

/**
 * Finds the includes and imports of a schema that could not be loaded, also inside imported
 * schemas: the components they hold are missing from every view of the schema.
 */
public final class UnresolvedSchemaReferences {

    /**
     * One reference that could not be loaded.
     *
     * @param location the schemaLocation as written in the schema
     * @param error    why it failed
     */
    public record Entry(String location, String error) {
    }

    private UnresolvedSchemaReferences() {
    }

    /**
     * Collects the failed references of a schema and its imported schemas.
     *
     * @param schema the schema
     * @return the failures, one per location, in document order
     */
    public static List<Entry> of(XsdSchema schema) {
        Map<String, Entry> failures = new LinkedHashMap<>();
        collect(schema, failures);
        schema.getImportedSchemas().values().forEach(imported -> collect(imported, failures));
        return List.copyOf(failures.values());
    }

    private static void collect(XsdSchema schema, Map<String, Entry> failures) {
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSchemaReference reference && reference.getResolutionError() != null) {
                String location = reference.getSchemaLocation();
                failures.putIfAbsent(location, new Entry(location, reference.getResolutionError()));
            }
        }
        schema.getNestedIncludeFailures().forEach((location, error) ->
                failures.putIfAbsent(location, new Entry(location, error)));
    }

    /**
     * Words the failures for a single line of UI text.
     *
     * @param entries the failures, at least one
     * @return the sentence
     */
    public static String message(List<Entry> entries) {
        if (entries.size() == 1) {
            Entry entry = entries.getFirst();
            return "Could not load " + entry.location() + ": " + entry.error()
                    + ". Types and elements from it are missing.";
        }
        return "Could not load " + entries.size() + " referenced schemas: "
                + entries.stream().map(Entry::location).collect(Collectors.joining(", "))
                + ". Types and elements from them are missing.";
    }
}
