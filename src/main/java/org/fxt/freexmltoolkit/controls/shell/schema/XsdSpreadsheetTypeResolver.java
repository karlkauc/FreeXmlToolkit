package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.SpreadsheetCellKind;
import org.fxt.freexmltoolkit.service.SpreadsheetTypeResolver;

/**
 * Value types for the XML → Excel export from the XSD bound to the document: each instance path is
 * mapped to its declaration ({@link XsdInstanceLocator}) and that declaration's built-in base type
 * ({@link SchemaFacets#builtinBase}). Results are cached per position-free path, so the rows of a
 * repeated element share one lookup. Paths the schema does not declare, {@code xsi:}/{@code xml:}
 * attributes and lists/unions are text. UI-free; reads the model only.
 */
public final class XsdSpreadsheetTypeResolver implements SpreadsheetTypeResolver {

    private final XsdSchema schema;
    private final Map<String, SpreadsheetCellKind> cache = new ConcurrentHashMap<>();

    /** @param schema the bound schema's model */
    public XsdSpreadsheetTypeResolver(XsdSchema schema) {
        this.schema = schema;
    }

    @Override
    public SpreadsheetCellKind kindOf(String elementXPath, String attributeName) {
        if (schema == null || elementXPath == null) {
            return SpreadsheetCellKind.TEXT;
        }
        List<String> path = XsdInstanceLocator.instancePath(elementXPath);
        String key = String.join("/", path) + (attributeName == null ? "" : "/@" + attributeName);
        return cache.computeIfAbsent(key, k -> resolve(path, attributeName));
    }

    private SpreadsheetCellKind resolve(List<String> path, String attributeName) {
        if (attributeName != null && attributeName.contains(":")) {
            return SpreadsheetCellKind.TEXT; // xsi:type, xml:lang … are not declared by the schema
        }
        return XsdInstanceLocator.locate(schema, path, attributeName)
                .flatMap(located -> SchemaFacets.builtinBase(located.declaration(), schema))
                .map(SpreadsheetCellKind::fromBuiltin)
                .orElse(SpreadsheetCellKind.TEXT);
    }
}
