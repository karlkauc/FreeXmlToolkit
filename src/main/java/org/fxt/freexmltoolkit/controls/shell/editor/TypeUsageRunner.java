package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageFinder;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * UI-free "find type usages" for the shell, reusing {@link TypeUsageFinder}.
 * Parses the XSD text into the model and returns human-readable usage locations.
 */
public final class TypeUsageRunner {

    private TypeUsageRunner() {
    }

    /** @return usage descriptions ("Node 'x' (kind) — path") for {@code typeName}, or empty. */
    public static List<String> findUsages(String xsdText, String typeName) {
        return findUsages(xsdText, null, typeName);
    }

    /**
     * Finds usages of {@code typeName}, resolving relative {@code xs:import}/{@code xs:include}
     * {@code schemaLocation}s against {@code baseDirectory} (issue #36: without a base directory
     * relative imports fall back to a namespace-URL download instead of being read from disk, so
     * usages defined in imported schemas are missed).
     *
     * @return usage descriptions ("Node 'x' (kind) — path") for {@code typeName}, or empty.
     */
    public static List<String> findUsages(String xsdText, Path baseDirectory, String typeName) {
        try {
            XsdNodeFactory factory = new XsdNodeFactory();
            XsdNode root = baseDirectory != null
                    ? factory.fromString(xsdText, baseDirectory)
                    : factory.fromString(xsdText);
            if (!(root instanceof XsdSchema schema)) {
                return List.of();
            }
            return new TypeUsageFinder(schema).findUsages(typeName).stream()
                    .map(u -> u.getDescription() + " — " + u.getPath())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
