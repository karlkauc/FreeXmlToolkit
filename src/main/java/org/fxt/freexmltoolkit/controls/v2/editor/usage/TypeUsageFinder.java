package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Service for finding usages of a type within an XSD schema.
 * Searches through all nodes to find where a given type name is referenced.
 * <p>
 * Supports finding usages in:
 * <ul>
 *   <li>Element type attributes</li>
 *   <li>Attribute type attributes</li>
 *   <li>Restriction base types</li>
 *   <li>Extension base types</li>
 *   <li>List item types</li>
 *   <li>Union member types</li>
 *   <li>Alternative types (XSD 1.1)</li>
 * </ul>
 * <p>
 * Uses cycle detection to prevent infinite loops in recursive schemas.
 *
 * @since 2.0
 */
public class TypeUsageFinder {

    private static final Logger logger = LogManager.getLogger(TypeUsageFinder.class);

    private final XsdSchema schema;

    /**
     * Creates a new TypeUsageFinder for the given schema.
     *
     * @param schema the XSD schema to search (must not be null)
     * @throws NullPointerException if schema is null
     */
    public TypeUsageFinder(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        this.schema = schema;
    }

    /**
     * Finds all usages of a type by name.
     *
     * @param typeName the type name to search for (must not be null or empty)
     * @return list of usage locations (never null, may be empty)
     * @throws IllegalArgumentException if typeName is null or empty
     */
    public List<TypeUsageLocation> findUsages(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("Type name cannot be null or empty");
        }
        logger.debug("Finding usages of type: {}", typeName);
        // The graph is rebuilt per call: callers create the finder once and keep editing the
        // model (tests add nodes after construction), so a cached snapshot would go stale.
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        List<TypeUsageLocation> usages = new ArrayList<>();
        for (SchemaReferenceGraph.Reference reference : graph.typeReferencesTo(typeName)) {
            usages.add(reference.toUsageLocation());
        }
        logger.debug("Found {} usages of type '{}'", usages.size(), typeName);
        return usages;
    }

    /**
     * Counts the number of usages of a type.
     *
     * @param typeName the type name to search for
     * @return the number of usages (0 if not used)
     */
    public int countUsages(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return 0;
        }
        return findUsages(typeName).size();
    }

    /**
     * Checks if a type is used anywhere in the schema.
     *
     * @param typeName the type name to check
     * @return true if the type is used, false otherwise
     */
    public boolean isTypeUsed(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        return !findUsages(typeName).isEmpty();
    }
}
