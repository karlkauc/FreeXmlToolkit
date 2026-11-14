package org.fxt.freexmltoolkit.plugin;

import java.util.List;
import java.util.Map;

/**
 * Describes XML elements a plugin knows - the label and explanation of a reporting code, a
 * template, a field. The descriptions appear in the Inspector (DOCUMENTATION) and in the tooltip
 * of a grid row's name. Called on the JavaFX thread: answer from memory, return {@code null}
 * while data is still loading.
 */
@FunctionalInterface
public interface ElementDescriber {

    /** @return the description of the element (plain text, may span lines), or {@code null} */
    String describe(ElementInfo element);

    /**
     * What is known about an element.
     *
     * @param path             element names from the root to the element itself (local names)
     * @param attributes       the element's attributes
     * @param childTexts       the text of each leaf child element (first one per name)
     * @param parentChildTexts the same for the parent element (the siblings), empty at the root
     * @param text             the element's own text (trimmed; empty for containers)
     * @param ancestorAttributes the attributes of each ancestor, root first (same order as {@code path}
     *                         without the element itself)
     */
    record ElementInfo(List<String> path, Map<String, String> attributes, Map<String, String> childTexts,
                       Map<String, String> parentChildTexts, String text, List<Map<String, String>> ancestorAttributes) {

        /** @return the element's own name */
        public String name() {
            return path.isEmpty() ? "" : path.get(path.size() - 1);
        }

        /** @return the name of the root element */
        public String rootName() {
            return path.isEmpty() ? "" : path.get(0);
        }
    }
}
