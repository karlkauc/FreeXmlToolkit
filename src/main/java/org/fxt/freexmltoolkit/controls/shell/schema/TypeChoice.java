package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.List;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * One entry a type picker offers for an element's or attribute's {@code type} attribute: a
 * built-in datatype or a named simple/complex type of the schema (own, included or imported).
 *
 * @param qualifiedName what is written into {@code type="…"} ({@code xs:string}, {@code Money}, {@code cmn:Money})
 * @param displayName   the local name, used for filtering
 * @param kind          built-in, simple or complex
 * @param group         the list section the entry belongs to ("Simple types", "Numeric", …)
 * @param summary       a one-line description shown next to the name (may be empty)
 * @param details       the explanation shown while the entry is highlighted; computed lazily
 * @param node          the schema node of a simple/complex type, {@code null} for built-ins
 */
public record TypeChoice(String qualifiedName, String displayName, Kind kind, String group, String summary,
                         Supplier<TypeDetails> details, XsdNode node) {

    /** The three sources of a type reference. */
    public enum Kind { BUILTIN, SIMPLE, COMPLEX }

    /**
     * The explanation of a type.
     *
     * @param title         the qualified name
     * @param kindLabel     "Built-in type · Numeric", "Simple type", "Complex type"
     * @param baseType      the base type (qualified), or {@code null}
     * @param derivation    "restriction", "extension", "list", "union", or {@code null}
     * @param documentation the description (built-in) or the schema's {@code xs:documentation}; may be {@code null}
     * @param facts         short facts such as enumerations, patterns, "mixed content"
     * @param source        where the type is defined when not in the edited schema, or {@code null}
     * @param example       an example literal (built-ins), or {@code null}
     * @param note          a caveat such as "XSD 1.1 only", or {@code null}
     */
    public record TypeDetails(String title, String kindLabel, String baseType, String derivation,
                              String documentation, List<String> facts, String source, String example,
                              String note) {
    }

    /** @return whether {@code text} matches the local or qualified name (case-insensitive contains). */
    public boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String needle = text.trim().toLowerCase(java.util.Locale.ROOT);
        return displayName.toLowerCase(java.util.Locale.ROOT).contains(needle)
                || qualifiedName.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    /** @return whether the local or qualified name starts with {@code text} (ranks ahead of plain matches). */
    public boolean startsWith(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String needle = text.trim().toLowerCase(java.util.Locale.ROOT);
        return displayName.toLowerCase(java.util.Locale.ROOT).startsWith(needle)
                || qualifiedName.toLowerCase(java.util.Locale.ROOT).startsWith(needle);
    }
}
