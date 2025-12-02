package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks the include hierarchy during XSD parsing.
 * <p>
 * When parsing an XSD schema that contains xs:include statements, this tracker
 * maintains a context stack to track which include file is currently being processed.
 * This allows each parsed XsdNode to be tagged with its source file information.
 * <p>
 * Usage:
 * <pre>
 * IncludeTracker tracker = new IncludeTracker(mainSchemaPath);
 * // When encountering an xs:include:
 * tracker.pushContext(includeNode, resolvedPath);
 * // ... parse children from included file ...
 * tracker.popContext();
 * </pre>
 *
 * @since 2.0
 */
public class IncludeTracker {

    /**
     * Internal context record for tracking include nesting.
     */
    private record IncludeContext(
            XsdInclude includeNode,
            Path resolvedPath,
            String schemaLocation
    ) {}

    private final Deque<IncludeContext> contextStack = new ArrayDeque<>();
    private final Path mainSchemaPath;

    /**
     * Creates a new include tracker for a schema.
     *
     * @param mainSchemaPath the absolute path to the main schema file
     */
    public IncludeTracker(Path mainSchemaPath) {
        this.mainSchemaPath = mainSchemaPath;
    }

    /**
     * Gets the main schema file path.
     *
     * @return the main schema path
     */
    public Path getMainSchemaPath() {
        return mainSchemaPath;
    }

    /**
     * Pushes a new include context onto the stack.
     * Call this when starting to parse an included schema file.
     *
     * @param include      the XsdInclude node being processed
     * @param resolvedPath the absolute path to the resolved include file
     */
    public void pushContext(XsdInclude include, Path resolvedPath) {
        String schemaLocation = include != null ? include.getSchemaLocation() : null;
        contextStack.push(new IncludeContext(include, resolvedPath, schemaLocation));
    }

    /**
     * Pops the current include context from the stack.
     * Call this when finished parsing an included schema file.
     *
     * @throws IllegalStateException if the context stack is empty
     */
    public void popContext() {
        if (contextStack.isEmpty()) {
            throw new IllegalStateException("Cannot pop from empty include context stack");
        }
        contextStack.pop();
    }

    /**
     * Gets the current include depth (0 = main schema, 1 = first level include, etc.).
     *
     * @return the current nesting depth
     */
    public int getDepth() {
        return contextStack.size();
    }

    /**
     * Checks if currently parsing content from the main schema (not an include).
     *
     * @return true if in main schema context
     */
    public boolean isInMainSchema() {
        return contextStack.isEmpty();
    }

    /**
     * Gets the current include node being processed.
     *
     * @return the current XsdInclude, or null if in main schema
     */
    public XsdInclude getCurrentInclude() {
        IncludeContext ctx = contextStack.peek();
        return ctx != null ? ctx.includeNode() : null;
    }

    /**
     * Gets the current source file path.
     *
     * @return the current file path (main schema if no includes are being processed)
     */
    public Path getCurrentSourceFile() {
        IncludeContext ctx = contextStack.peek();
        return ctx != null ? ctx.resolvedPath() : mainSchemaPath;
    }

    /**
     * Gets the current schema location (the original xs:include schemaLocation attribute).
     *
     * @return the schema location, or null if in main schema
     */
    public String getCurrentSchemaLocation() {
        IncludeContext ctx = contextStack.peek();
        return ctx != null ? ctx.schemaLocation() : null;
    }

    /**
     * Gets the source information for the current parsing context.
     * This creates an appropriate IncludeSourceInfo based on whether we're
     * parsing the main schema or an included file.
     *
     * @return the source info for tagging nodes
     */
    public IncludeSourceInfo getCurrentSourceInfo() {
        if (contextStack.isEmpty()) {
            return IncludeSourceInfo.forMainSchema(mainSchemaPath);
        }

        IncludeContext ctx = contextStack.peek();
        return IncludeSourceInfo.forIncludedSchema(
                ctx.resolvedPath(),
                ctx.schemaLocation(),
                ctx.includeNode()
        );
    }

    /**
     * Tags an XsdNode with the current source information.
     * This sets the node's sourceInfo property based on the current parsing context.
     *
     * @param node the node to tag (if null, this method does nothing)
     */
    public void tagNode(XsdNode node) {
        if (node != null) {
            node.setSourceInfo(getCurrentSourceInfo());
        }
    }

    /**
     * Tags an XsdNode and all its descendants with the current source information.
     * Useful when inlining an entire included schema.
     *
     * @param node the root node to tag (if null, this method does nothing)
     */
    public void tagNodeRecursively(XsdNode node) {
        if (node == null) {
            return;
        }

        tagNode(node);
        for (XsdNode child : node.getChildren()) {
            tagNodeRecursively(child);
        }
    }

    /**
     * Clears all include contexts.
     * Call this when starting a fresh parse or to reset state.
     */
    public void clear() {
        contextStack.clear();
    }

    /**
     * Checks if there are any active include contexts.
     *
     * @return true if at least one include context is on the stack
     */
    public boolean hasActiveContext() {
        return !contextStack.isEmpty();
    }

    @Override
    public String toString() {
        if (contextStack.isEmpty()) {
            return "IncludeTracker[main=" + mainSchemaPath + "]";
        }
        return "IncludeTracker[depth=" + contextStack.size() +
               ", current=" + getCurrentSchemaLocation() + "]";
    }
}
