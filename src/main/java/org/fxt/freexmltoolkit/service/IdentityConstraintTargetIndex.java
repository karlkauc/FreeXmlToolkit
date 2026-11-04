package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * For the XSD documentation: which identity constraints point to an element, i.e. which
 * constraints an ancestor declares whose selector plus one of its fields leads to it (the field
 * target, e.g. {@code BenchmarkID} for a key on {@code Fund}). Built once from the element map;
 * fields that select an attribute are attached to the attribute's element and name the attribute.
 */
public final class IdentityConstraintTargetIndex {

    /**
     * A constraint that points to an element.
     *
     * @param constraint      the constraint
     * @param declaringXpath  element map key of the element that declares it
     * @param declaringName   that element's name
     * @param field           the field that leads to the target
     * @param attribute       the attribute name ({@code @id}) if the field selects an attribute of
     *                        the target, else null
     */
    public record Incoming(IdentityConstraint constraint, String declaringXpath, String declaringName,
                           String field, String attribute) {
    }

    private final Map<String, List<Incoming>> byTarget;

    private IdentityConstraintTargetIndex(Map<String, List<Incoming>> byTarget) {
        this.byTarget = byTarget;
    }

    /**
     * @param elementMap the documentation's element map (XPath → element)
     * @return the index over all constraints of the map
     */
    public static IdentityConstraintTargetIndex build(Map<String, XsdExtendedElement> elementMap) {
        Map<String, List<Incoming>> byTarget = new LinkedHashMap<>();
        if (elementMap == null) {
            return new IdentityConstraintTargetIndex(byTarget);
        }
        for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
            List<IdentityConstraint> constraints = entry.getValue().getIdentityConstraints();
            if (constraints == null || constraints.isEmpty()) {
                continue;
            }
            String base = entry.getKey();
            for (IdentityConstraint constraint : constraints) {
                if (constraint.getSelector() == null || constraint.getFields() == null) {
                    continue;
                }
                Set<String> added = new LinkedHashSet<>();
                for (String selected : IdentityConstraintPaths.resolve(base, constraint.getSelector(), elementMap)) {
                    for (String field : constraint.getFields()) {
                        for (String target : IdentityConstraintPaths.resolve(selected, field, elementMap)) {
                            addTarget(byTarget, added, elementMap, target,
                                    constraint, base, entry.getValue().getElementName(), field);
                        }
                    }
                }
            }
        }
        return new IdentityConstraintTargetIndex(byTarget);
    }

    private static void addTarget(Map<String, List<Incoming>> byTarget, Set<String> added,
                                  Map<String, XsdExtendedElement> elementMap, String target,
                                  IdentityConstraint constraint, String base, String declaringName, String field) {
        XsdExtendedElement targetElement = elementMap.get(target);
        String name = targetElement == null ? null : targetElement.getElementName();
        String key = target;
        String attribute = null;
        if (name != null && name.startsWith("@")) {
            int slash = target.lastIndexOf('/');
            if (slash <= 0) {
                return;
            }
            key = target.substring(0, slash);
            attribute = name;
        }
        if (added.add(key + "|" + attribute)) {
            byTarget.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new Incoming(constraint, base, declaringName, field.strip(), attribute));
        }
    }

    /**
     * @param xpath element map key of a (non-attribute) element
     * @return the constraints that point to it or one of its attributes, in declaration order
     */
    public List<Incoming> of(String xpath) {
        return xpath == null ? List.of() : byTarget.getOrDefault(xpath, List.of());
    }
}
