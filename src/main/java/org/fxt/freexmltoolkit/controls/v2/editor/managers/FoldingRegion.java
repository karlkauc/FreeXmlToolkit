package org.fxt.freexmltoolkit.controls.v2.editor.managers;

/**
 * Represents a foldable region in XML code.
 *
 * <p>A foldable region is a contiguous block of lines that can be collapsed
 * (folded) or expanded (unfolded) in the code editor.</p>
 *
 * @param startLine 0-based line number where the region starts
 * @param endLine 0-based line number where the region ends (inclusive)
 * @param elementName Name of the XML element (e.g., "root", "person")
 * @param indentLevel Indentation depth of the element (0 for root level)
 */
public record FoldingRegion(int startLine,
                           int endLine,
                           String elementName,
                           int indentLevel) {

    /**
     * Returns a human-readable string representation of this folding region.
     *
     * @return formatted string with line range, element name, and indent level
     */
    @Override
    public String toString() {
        return String.format("FoldingRegion[%d-%d, %s, level=%d]",
                            startLine, endLine, elementName, indentLevel);
    }
}
