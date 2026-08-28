package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdElementDisplayUtils;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Node;

/**
 * Computes which child elements may be inserted at the caret, given the direct children that
 * already exist before and after it.
 *
 * <p>The schema structure comes from the documentation element map, where compositors appear
 * as synthetic {@code SEQUENCE_n} / {@code CHOICE_n} / {@code ALL_n} nodes whose children are
 * in schema order. Rules:</p>
 * <ul>
 *   <li><b>sequence</b>: the particle containing the last existing sibling stays offered while
 *       its {@code maxOccurs} allows; then the following particles are offered up to and
 *       including the first non-emptiable one. Siblings after the caret cap the range.</li>
 *   <li><b>choice</b>: once an alternative is present (and the choice is not repeatable),
 *       nothing more is offered; otherwise every alternative.</li>
 *   <li><b>all</b>: every child that has not reached {@code maxOccurs}.</li>
 * </ul>
 */
final class AllowedChildrenCalculator {

    private enum Kind { SEQUENCE, CHOICE, ALL, ELEMENT }

    private final XsdDocumentationData data;
    private final Map<String, Set<String>> descendantNames = new HashMap<>();

    AllowedChildrenCalculator(XsdDocumentationData data) {
        this.data = data;
    }

    /**
     * @param parent         the schema element the caret is inside
     * @param siblingsBefore local names of the direct children before the caret, in order
     * @param siblingsAfter  local names of the direct children after the caret, in order
     * @return the allowed children in schema order (deduplicated by local name)
     */
    List<XsdExtendedElement> compute(XsdExtendedElement parent, List<String> siblingsBefore, List<String> siblingsAfter) {
        List<XsdExtendedElement> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        Map<String, Integer> before = counts(siblingsBefore);
        Map<String, Integer> after = counts(siblingsAfter);
        // The element itself behaves like a sequence of its (usually single compositor) particles.
        collect(parent, Kind.SEQUENCE, before, after, result, new HashSet<>());

        Map<String, XsdExtendedElement> deduped = new LinkedHashMap<>();
        for (XsdExtendedElement el : result) {
            deduped.putIfAbsent(el.getElementName(), el);
        }
        return new ArrayList<>(deduped.values());
    }

    private void collect(XsdExtendedElement container, Kind kind, Map<String, Integer> before,
                         Map<String, Integer> after, List<XsdExtendedElement> out, Set<String> visited) {
        if (container == null || !visited.add(container.getCurrentXpath())) {
            return;
        }
        List<XsdExtendedElement> particles = particles(container);
        switch (kind) {
            case CHOICE -> collectChoice(container, particles, before, after, out, visited);
            case ALL -> {
                for (XsdExtendedElement p : particles) {
                    offer(p, before, after, out, visited);
                }
            }
            default -> collectSequence(container, particles, before, after, out, visited);
        }
        visited.remove(container.getCurrentXpath());
    }

    private void collectSequence(XsdExtendedElement container, List<XsdExtendedElement> particles,
                                 Map<String, Integer> before, Map<String, Integer> after,
                                 List<XsdExtendedElement> out, Set<String> visited) {
        int lastIdx = -1;
        int afterIdx = particles.size();
        for (int i = 0; i < particles.size(); i++) {
            if (occurrences(particles.get(i), before) > 0) {
                lastIdx = i;
            }
            if (afterIdx == particles.size() && occurrences(particles.get(i), after) > 0) {
                afterIdx = i;
            }
        }
        if (afterIdx < lastIdx) {
            afterIdx = particles.size(); // document is out of order — ignore the cap
        }

        int sizeBefore = out.size();
        if (lastIdx >= 0) {
            offer(particles.get(lastIdx), before, after, out, visited);
        }
        for (int i = lastIdx + 1; i < particles.size() && i <= afterIdx; i++) {
            XsdExtendedElement p = particles.get(i);
            if (i == afterIdx) {
                // This particle already occurs after the caret: it may only be inserted here
                // if its total occurrence count still leaves room.
                if (kindOf(p) != Kind.ELEMENT) {
                    offer(p, before, after, out, visited);
                } else if (before.getOrDefault(p.getElementName(), 0)
                        + after.getOrDefault(p.getElementName(), 0) < maxOccurs(p)) {
                    out.add(p);
                }
                break;
            }
            offer(p, before, after, out, visited);
            if (!isEmptiable(p, new HashSet<>())) {
                break;
            }
        }

        // A repeatable sequence group that is exhausted may start over. This applies to
        // compositors only: an element's maxOccurs repeats the element, not its content.
        if (out.size() == sizeBefore && lastIdx >= 0 && kindOf(container) != Kind.ELEMENT
                && maxOccurs(container) > 1) {
            collectSequence(container, particles, Map.of(), after, out, visited);
        }
    }

    private void collectChoice(XsdExtendedElement choice, List<XsdExtendedElement> particles,
                               Map<String, Integer> before, Map<String, Integer> after,
                               List<XsdExtendedElement> out, Set<String> visited) {
        int present = 0;
        for (XsdExtendedElement p : particles) {
            present += occurrences(p, before);
        }
        if (present > 0 && present >= maxOccurs(choice)) {
            return;
        }
        for (XsdExtendedElement p : particles) {
            if (present > 0 && occurrences(p, before) > 0) {
                offer(p, before, after, out, visited);   // the chosen alternative may repeat
            } else if (present == 0) {
                offer(p, before, after, out, visited);
            }
        }
    }

    /** Offers one particle: an element if it has room left, a compositor by recursion. */
    private void offer(XsdExtendedElement particle, Map<String, Integer> before, Map<String, Integer> after,
                       List<XsdExtendedElement> out, Set<String> visited) {
        Kind kind = kindOf(particle);
        if (kind == Kind.ELEMENT) {
            if (before.getOrDefault(particle.getElementName(), 0) < maxOccurs(particle)) {
                out.add(particle);
            }
        } else {
            collect(particle, kind, before, after, out, visited);
        }
    }

    // -- structure helpers ------------------------------------------------------------------

    private List<XsdExtendedElement> particles(XsdExtendedElement container) {
        List<XsdExtendedElement> result = new ArrayList<>();
        if (container.getChildren() == null) {
            return result;
        }
        for (String xpath : container.getChildren()) {
            XsdExtendedElement child = data.getExtendedXsdElementMap().get(xpath);
            if (child != null && child.getElementName() != null && !child.getElementName().startsWith("@")) {
                result.add(child);
            }
        }
        return result;
    }

    private static Kind kindOf(XsdExtendedElement el) {
        String name = el.getElementName();
        if (!XsdElementDisplayUtils.isCompositorElement(name)) {
            return Kind.ELEMENT;
        }
        if (name.startsWith("CHOICE")) {
            return Kind.CHOICE;
        }
        if (name.startsWith("ALL")) {
            return Kind.ALL;
        }
        return Kind.SEQUENCE;
    }

    /** How often the particle (or, for a compositor, any of its descendant elements) occurs. */
    private int occurrences(XsdExtendedElement particle, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String name : descendantNames(particle)) {
            n += counts.getOrDefault(name, 0);
        }
        return n;
    }

    private Set<String> descendantNames(XsdExtendedElement particle) {
        String key = particle.getCurrentXpath();
        Set<String> cached = descendantNames.get(key);
        if (cached != null) {
            return cached;
        }
        Set<String> names = new HashSet<>();
        collectNames(particle, names, new HashSet<>());
        descendantNames.put(key, names);
        return names;
    }

    private void collectNames(XsdExtendedElement el, Set<String> names, Set<String> visited) {
        if (el == null || !visited.add(el.getCurrentXpath())) {
            return;
        }
        if (kindOf(el) == Kind.ELEMENT) {
            names.add(el.getElementName());
            return;
        }
        for (XsdExtendedElement child : particles(el)) {
            collectNames(child, names, visited);
        }
    }

    private boolean isEmptiable(XsdExtendedElement particle, Set<String> visited) {
        if (minOccurs(particle) == 0) {
            return true;
        }
        Kind kind = kindOf(particle);
        if (kind == Kind.ELEMENT || !visited.add(particle.getCurrentXpath())) {
            return false;
        }
        List<XsdExtendedElement> children = particles(particle);
        if (kind == Kind.CHOICE) {
            return children.stream().anyMatch(c -> isEmptiable(c, visited));
        }
        return children.stream().allMatch(c -> isEmptiable(c, visited));
    }

    // -- cardinality ------------------------------------------------------------------------

    private static Node cardinalitySource(XsdExtendedElement el) {
        return el.getCardinalityNode() != null ? el.getCardinalityNode() : el.getCurrentNode();
    }

    /** @return maxOccurs, {@link Integer#MAX_VALUE} for unbounded (and when unknown). */
    static int maxOccurs(XsdExtendedElement el) {
        Node source = cardinalitySource(el);
        if (source == null) {
            return Integer.MAX_VALUE;
        }
        String max = XsdElementDisplayUtils.getNodeAttribute(source, "maxOccurs");
        if (max == null || max.isEmpty()) {
            return 1;
        }
        if ("unbounded".equals(max)) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(max);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    static int minOccurs(XsdExtendedElement el) {
        Node source = cardinalitySource(el);
        if (source == null) {
            return 1;
        }
        String min = XsdElementDisplayUtils.getNodeAttribute(source, "minOccurs");
        if (min == null || min.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(min);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static Map<String, Integer> counts(List<String> names) {
        Map<String, Integer> counts = new HashMap<>();
        if (names != null) {
            for (String n : names) {
                counts.merge(n, 1, Integer::sum);
            }
        }
        return counts;
    }
}
