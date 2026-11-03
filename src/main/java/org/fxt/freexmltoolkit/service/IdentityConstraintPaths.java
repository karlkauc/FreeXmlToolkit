package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * Resolves the selector and field XPaths of identity constraints (xs:key, xs:unique, xs:keyref)
 * against the element map of the XSD documentation. Used for sample data generation
 * ({@link IdentityConstraintTracker}) and for the links of the documentation diagrams.
 */
public final class IdentityConstraintPaths {

    private IdentityConstraintPaths() {
    }

    /**
     * Resolves a selector or field XPath of the restricted identity-constraint syntax against the element map:
     * alternatives ({@code a|b}), a leading {@code ./} or {@code .//} (descendants), name tests with or without a
     * prefix (the map is keyed by local names, element references by their QName), {@code *} and {@code @attribute}.
     * Structural containers (SEQUENCE, CHOICE, ALL) in the map are skipped.
     *
     * @param baseXpath  XPath (element map key) the path is relative to
     * @param path       the selector or field
     * @param elementMap the documentation's element map
     * @return the XPaths of the element map the path selects, in map order; empty if none
     */
    public static List<String> resolve(String baseXpath, String path, Map<String, XsdExtendedElement> elementMap) {
        Set<String> result = new LinkedHashSet<>();
        if (baseXpath == null || path == null) {
            return List.of();
        }
        for (String alternative : path.split("\\|")) {
            String steps = alternative.trim();
            boolean descendants = steps.startsWith(".//");
            if (descendants) {
                steps = steps.substring(3);
            } else if (steps.startsWith("./")) {
                steps = steps.substring(2);
            }
            if (steps.isEmpty() || ".".equals(steps)) {
                if (elementMap.containsKey(baseXpath)) {
                    result.add(baseXpath);
                }
                continue;
            }
            String[] segments = steps.split("/");
            if (descendants) {
                String prefix = baseXpath + "/";
                for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
                    if (entry.getKey().startsWith(prefix) && nameMatches(entry.getValue(), segments[0])) {
                        resolveSegments(entry.getKey(), segments, 1, elementMap, result);
                    }
                }
            } else {
                resolveSegments(baseXpath, segments, 0, elementMap, result);
            }
        }
        return new ArrayList<>(result);
    }

    private static void resolveSegments(String currentXpath, String[] segments, int index,
                                 Map<String, XsdExtendedElement> elementMap, Set<String> result) {
        if (index >= segments.length) {
            if (elementMap.containsKey(currentXpath)) {
                result.add(currentXpath);
            }
            return;
        }
        String segment = segments[index].trim();
        // Direct concatenation, for maps whose parents do not list their children
        String direct = currentXpath + "/" + localName(segment);
        if (!"*".equals(segment) && elementMap.containsKey(direct)) {
            resolveSegments(direct, segments, index + 1, elementMap, result);
        }
        XsdExtendedElement current = elementMap.get(currentXpath);
        if (current == null || current.getChildren() == null) {
            return;
        }
        for (String childXpath : current.getChildren()) {
            XsdExtendedElement child = elementMap.get(childXpath);
            if (child == null || child.getElementName() == null || childXpath.equals(direct)) {
                continue;
            }
            if (isContainer(child)) {
                resolveSegments(childXpath, segments, index, elementMap, result);
            } else if (nameMatches(child, segment)) {
                resolveSegments(childXpath, segments, index + 1, elementMap, result);
            }
        }
    }

    /** Whether a name test ({@code name}, {@code p:name}, {@code *}, {@code @name}, {@code @p:name}) matches. */
    private static boolean nameMatches(XsdExtendedElement element, String segment) {
        String name = element.getElementName();
        if (name == null || isContainer(element)) {
            return false;
        }
        boolean attributeTest = segment.startsWith("@");
        if (attributeTest != name.startsWith("@")) {
            return false;
        }
        String test = localName(attributeTest ? segment.substring(1) : segment);
        String actual = localName(attributeTest ? name.substring(1) : name);
        return "*".equals(test) || test.equals(actual);
    }

    private static boolean isContainer(XsdExtendedElement element) {
        String name = element.getElementName();
        return name != null && (name.startsWith("SEQUENCE") || name.startsWith("CHOICE") || name.startsWith("ALL"));
    }

    /** {@code p:name} → {@code name}; an attribute test keeps its {@code @}. */
    private static String localName(String step) {
        if (step.startsWith("@")) {
            return "@" + localName(step.substring(1));
        }
        return step.substring(step.indexOf(':') + 1);
    }
}
