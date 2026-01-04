package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Utility class for common command validation logic.
 * <p>
 * Centralizes validation patterns used across 30+ command classes:
 * - Null checks for editor context and nodes
 * - Type checking for nodes
 * - String validation (non-null, non-empty)
 * - Composite validation (context + node checks)
 * <p>
 * Usage:
 * <pre>{@code
 * // Basic validations
 * CommandValidation.requireNonNull(element, "Element");
 * CommandValidation.requireNonEmpty(name, "Name");
 *
 * // Type checking
 * XsdElement element = CommandValidation.requireNodeType(node, XsdElement.class, "Add element");
 *
 * // Combined validation
 * CommandValidation.requireEditorContextAndNode(context, node);
 * }</pre>
 *
 * @since 2.0
 */
public class CommandValidation {

    private CommandValidation() {
        // Utility class, not instantiable
    }

    /**
     * Validates that an object is not null.
     *
     * @param object     the object to check
     * @param fieldName  the field name for error message
     * @throws IllegalArgumentException if object is null
     */
    public static void requireNonNull(Object object, String fieldName) {
        if (object == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    /**
     * Validates that a string is not null or empty (after trimming).
     *
     * @param value     the string to check
     * @param fieldName the field name for error message
     * @throws IllegalArgumentException if value is null or empty
     */
    public static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
    }

    /**
     * Validates that an editor context and node are both non-null.
     * This is a common validation pattern for most commands.
     *
     * @param context the editor context to check
     * @param node    the node to check
     * @throws IllegalArgumentException if either is null
     */
    public static void requireEditorContextAndNode(XsdEditorContext context, XsdNode node) {
        requireNonNull(context, "Editor context");
        requireNonNull(node, "Node");
    }

    /**
     * Validates that a node is of a specific type and returns it cast.
     * Useful for operations that require a specific node type.
     *
     * @param node            the node to check
     * @param expectedType    the expected node type
     * @param operationName   human-readable name of the operation for error messages
     * @param <T>             the expected node type
     * @return the node cast to the expected type
     * @throws IllegalArgumentException if node is not of expectedType
     */
    public static <T extends XsdNode> T requireNodeType(
            XsdNode node,
            Class<T> expectedType,
            String operationName) {
        if (!expectedType.isInstance(node)) {
            throw new IllegalArgumentException(
                operationName + " can only be applied to " +
                expectedType.getSimpleName() + ", not to " + node.getClass().getSimpleName());
        }
        return expectedType.cast(node);
    }


    /**
     * Validates that a node is NOT of a specific type.
     * Useful for operations that exclude certain node types.
     *
     * @param node          the node to check
     * @param excludedType  the type to exclude
     * @param operationName human-readable name of the operation for error messages
     * @throws IllegalArgumentException if node is of excludedType
     */
    public static void requireNodeTypeNot(
            XsdNode node,
            Class<?> excludedType,
            String operationName) {
        if (excludedType.isInstance(node)) {
            throw new IllegalArgumentException(
                operationName + " cannot be applied to " + excludedType.getSimpleName());
        }
    }

    /**
     * Validates that a node is of one of multiple allowed types.
     *
     * @param node          the node to check
     * @param operationName human-readable name of the operation for error messages
     * @param allowedTypes  the allowed node types
     * @throws IllegalArgumentException if node is not of any allowedType
     */
    @SafeVarargs
    public static void requireNodeTypeOneOf(
            XsdNode node,
            String operationName,
            Class<? extends XsdNode>... allowedTypes) {
        for (Class<?> type : allowedTypes) {
            if (type.isInstance(node)) {
                return; // Found a match
            }
        }
        // No match found, build error message
        StringBuilder typeList = new StringBuilder();
        for (int i = 0; i < allowedTypes.length; i++) {
            if (i > 0) {
                typeList.append(" or ");
            }
            typeList.append(allowedTypes[i].getSimpleName());
        }
        throw new IllegalArgumentException(
            operationName + " can only be applied to " +
            typeList + ", not to " + node.getClass().getSimpleName());
    }
}
