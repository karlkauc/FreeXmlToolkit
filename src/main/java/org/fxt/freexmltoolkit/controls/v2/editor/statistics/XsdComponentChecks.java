package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.IssueCategory;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.IssueSeverity;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.QualityIssue;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.Component;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKind;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.Reference;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.UsageReferenceType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Quality checks that need the whole-schema {@link SchemaReferenceGraph}: unresolved
 * references, derivation and containment cycles, unused and unreachable components, missing
 * documentation on global components and single-use types (inline candidates). Kept apart from
 * {@link XsdQualityChecker}'s per-node checks; the checker merges the issues.
 */
final class XsdComponentChecks {

    private final SchemaReferenceGraph graph;

    XsdComponentChecks(SchemaReferenceGraph graph) {
        this.graph = graph;
    }

    /** @return the issues, errors first (unresolved references, derivation cycles), then hints. */
    List<QualityIssue> check() {
        List<QualityIssue> issues = new ArrayList<>();
        checkUnresolvedReferences(issues);
        checkCycles(issues);
        checkUnusedComponents(issues);
        checkMissingDocumentation(issues);
        checkInlineCandidates(issues);
        return issues;
    }

    private void checkUnresolvedReferences(List<QualityIssue> issues) {
        for (Reference reference : graph.unresolvedReferences()) {
            XsdNode referrer = reference.referrer();
            String what = describeTarget(reference);
            String where = describeReferrer(referrer);
            issues.add(QualityIssue.of(IssueCategory.UNRESOLVED_REFERENCE, IssueSeverity.ERROR,
                    what + " '" + reference.targetName() + "' referenced by " + where + " is not declared",
                    "Declare '" + reference.targetName() + "', fix the spelling, or add the namespace prefix of the schema that declares it",
                    List.of(reference.targetName()), referrer));
        }
    }

    private void checkCycles(List<QualityIssue> issues) {
        List<List<ComponentKey>> derivation = graph.derivationCycles();
        for (List<ComponentKey> cycle : derivation) {
            issues.add(QualityIssue.of(IssueCategory.CIRCULAR_REFERENCE, IssueSeverity.ERROR,
                    "Type derivation cycle: " + chain(cycle),
                    "A type cannot derive from itself — change the base type of one member of the cycle",
                    names(cycle), firstNode(cycle)));
        }
        for (List<ComponentKey> cycle : graph.cycles()) {
            if (derivation.contains(cycle)) {
                continue;
            }
            issues.add(QualityIssue.of(IssueCategory.CIRCULAR_REFERENCE, IssueSeverity.INFO,
                    "Recursive content model: " + chain(cycle),
                    "Recursion is legal; make sure at least one step in the cycle is optional (minOccurs=\"0\") so instances can terminate",
                    names(cycle), firstNode(cycle)));
        }
    }

    private void checkUnusedComponents(List<QualityIssue> issues) {
        ComponentKind[] removable = ComponentKind.REMOVABLE.toArray(ComponentKind[]::new);
        Set<ComponentKey> unreferenced = graph.unreferenced(removable);
        Set<ComponentKey> unreachable = new LinkedHashSet<>(graph.unreachable());
        for (ComponentKey key : unreferenced) {
            Component component = graph.component(key);
            String origin = component != null && component.fromInclude() ? " (declared in an included file)" : "";
            issues.add(QualityIssue.of(IssueCategory.UNUSED_COMPONENT, IssueSeverity.INFO,
                    key + " is never referenced" + origin,
                    "Remove it if it is not part of the schema's public contract, or reference it — Types → Remove unused… removes all unreachable components at once",
                    List.of(key.localName()), component != null ? component.node() : null));
        }
        unreachable.removeAll(unreferenced);
        for (ComponentKey key : unreachable) {
            Component component = graph.component(key);
            issues.add(QualityIssue.of(IssueCategory.UNUSED_COMPONENT, IssueSeverity.INFO,
                    key + " is only referenced from components that are themselves unused",
                    "Removing the unused components that reference it makes this one unused too",
                    List.of(key.localName()), component != null ? component.node() : null));
        }
    }

    private void checkMissingDocumentation(List<QualityIssue> issues) {
        for (Component component : graph.components().values()) {
            XsdNode node = component.node();
            boolean documented = (node.getDocumentation() != null && !node.getDocumentation().isBlank())
                    || (node.getDocumentations() != null && !node.getDocumentations().isEmpty());
            if (!documented) {
                issues.add(QualityIssue.of(IssueCategory.MISSING_DOCUMENTATION, IssueSeverity.SUGGESTION,
                        component.key() + " has no documentation",
                        "Add an xs:annotation/xs:documentation describing its purpose and allowed values",
                        List.of(component.key().localName()), node));
            }
        }
    }

    private void checkInlineCandidates(List<QualityIssue> issues) {
        for (ComponentKey key : graph.singleUse()) {
            Component component = graph.component(key);
            if (component == null || component.fromInclude()) {
                continue;
            }
            if (component.node() instanceof XsdComplexType complexType && complexType.isAbstract()) {
                continue;
            }
            Reference use = graph.referencesTo(key).get(0);
            if (use.fromImport()) {
                continue;
            }
            boolean inlinable = use.type() == UsageReferenceType.ELEMENT_TYPE
                    || use.type() == UsageReferenceType.ATTRIBUTE_TYPE;
            if (!inlinable) {
                continue;
            }
            issues.add(QualityIssue.of(IssueCategory.INLINE_CANDIDATE, IssueSeverity.SUGGESTION,
                    key + " is used only once, by " + describeReferrer(use.referrer()),
                    "Declare it inline as an anonymous type if no reuse is planned; keep it named if other schemas or future elements should share it",
                    List.of(key.localName()), component.node()));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String chain(List<ComponentKey> cycle) {
        StringBuilder sb = new StringBuilder();
        for (ComponentKey key : cycle) {
            if (!sb.isEmpty()) {
                sb.append(" → ");
            }
            sb.append(key.localName());
        }
        sb.append(" → ").append(cycle.get(0).localName());
        return sb.toString();
    }

    private static List<String> names(List<ComponentKey> cycle) {
        List<String> names = new ArrayList<>(cycle.size());
        for (ComponentKey key : cycle) {
            names.add(key.localName());
        }
        return names;
    }

    private XsdNode firstNode(List<ComponentKey> cycle) {
        Component component = graph.component(cycle.get(0));
        return component != null ? component.node() : null;
    }

    private static String describeTarget(Reference reference) {
        return switch (reference.type()) {
            case ELEMENT_REF, SUBSTITUTION_GROUP -> "Element";
            case ATTRIBUTE_REF -> "Attribute";
            case GROUP_REF -> "Group";
            case ATTRIBUTE_GROUP_REF -> "Attribute group";
            default -> "Type";
        };
    }

    private static String describeReferrer(XsdNode node) {
        if (node == null) {
            return "an unknown node";
        }
        String kind = node.getClass().getSimpleName().replace("Xsd", "").toLowerCase(java.util.Locale.ROOT);
        String name = node.getName();
        if (name == null || name.isBlank()) {
            XsdNode parent = node.getParent();
            String parentName = parent != null && parent.getName() != null ? parent.getName() : null;
            return parentName != null ? kind + " in '" + parentName + "'" : "an anonymous " + kind;
        }
        return kind + " '" + name + "'";
    }
}
