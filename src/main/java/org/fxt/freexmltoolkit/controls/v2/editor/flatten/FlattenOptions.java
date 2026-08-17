package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

/**
 * Options controlling how a flattened schema is reduced for deployment
 * (e.g. server-side validation where documentation and unused components
 * only cost resources).
 *
 * @param removeAnnotations      strip all xs:documentation and xs:appinfo content
 * @param removeComments         strip all XML comments (including leading comments)
 * @param minify                 collapse inter-tag whitespace in the serialized output
 * @param removeUnusedTypes      remove global types/groups unreachable from any
 *                               global element or attribute declaration
 * @param removeResolvedIncludes drop xs:include directives whose content was inlined
 *                               during parsing (unresolved directives are kept)
 */
public record FlattenOptions(boolean removeAnnotations, boolean removeComments,
                             boolean minify, boolean removeUnusedTypes,
                             boolean removeResolvedIncludes) {

    /** Plain flatten without any reduction — the legacy behavior. */
    public static final FlattenOptions NONE = new FlattenOptions(false, false, false, false, false);

    /** @return true if any option requires a model transform before serialization */
    public boolean requiresTransform() {
        return removeAnnotations || removeComments || removeUnusedTypes || removeResolvedIncludes;
    }
}
