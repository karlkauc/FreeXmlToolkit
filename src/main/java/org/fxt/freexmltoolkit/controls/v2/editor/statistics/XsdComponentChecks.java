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
import org.fxt.freexmltoolkit.i18n.I18n;

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
                    I18n.t("XsdComponentChecks.unresolved", what, reference.targetName(), where),
                    I18n.t("XsdComponentChecks.unresolved-suggestion", reference.targetName()),
                    List.of(reference.targetName()), referrer));
        }
    }

    private void checkCycles(List<QualityIssue> issues) {
        List<List<ComponentKey>> derivation = graph.derivationCycles();
        for (List<ComponentKey> cycle : derivation) {
            issues.add(QualityIssue.of(IssueCategory.CIRCULAR_REFERENCE, IssueSeverity.ERROR,
                    I18n.t("XsdComponentChecks.derivation-cycle", chain(cycle)),
                    I18n.t("XsdComponentChecks.derivation-cycle-suggestion"),
                    names(cycle), firstNode(cycle)));
        }
        for (List<ComponentKey> cycle : graph.cycles()) {
            if (derivation.contains(cycle)) {
                continue;
            }
            issues.add(QualityIssue.of(IssueCategory.CIRCULAR_REFERENCE, IssueSeverity.INFO,
                    I18n.t("XsdComponentChecks.recursive-content", chain(cycle)),
                    I18n.t("XsdComponentChecks.recursive-content-suggestion"),
                    names(cycle), firstNode(cycle)));
        }
    }

    private void checkUnusedComponents(List<QualityIssue> issues) {
        ComponentKind[] removable = ComponentKind.REMOVABLE.toArray(ComponentKind[]::new);
        Set<ComponentKey> unreferenced = graph.unreferenced(removable);
        Set<ComponentKey> unreachable = new LinkedHashSet<>(graph.unreachable());
        for (ComponentKey key : unreferenced) {
            Component component = graph.component(key);
            boolean included = component != null && component.fromInclude();
            issues.add(QualityIssue.of(IssueCategory.UNUSED_COMPONENT, IssueSeverity.INFO,
                    I18n.t(included ? "XsdComponentChecks.never-referenced-included" : "XsdComponentChecks.never-referenced",
                            key.label()),
                    I18n.t("XsdComponentChecks.never-referenced-suggestion"),
                    List.of(key.localName()), component != null ? component.node() : null));
        }
        unreachable.removeAll(unreferenced);
        for (ComponentKey key : unreachable) {
            Component component = graph.component(key);
            issues.add(QualityIssue.of(IssueCategory.UNUSED_COMPONENT, IssueSeverity.INFO,
                    I18n.t("XsdComponentChecks.unreachable", key.label()),
                    I18n.t("XsdComponentChecks.unreachable-suggestion"),
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
                        I18n.t("XsdComponentChecks.no-documentation", component.key().label()),
                        I18n.t("XsdComponentChecks.no-documentation-suggestion"),
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
                    I18n.t("XsdComponentChecks.used-once", key.label(), describeReferrer(use.referrer())),
                    I18n.t("XsdComponentChecks.used-once-suggestion"),
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
            case ELEMENT_REF, SUBSTITUTION_GROUP -> I18n.t("XsdComponentChecks.target-element");
            case ATTRIBUTE_REF -> I18n.t("XsdComponentChecks.target-attribute");
            case GROUP_REF -> I18n.t("XsdComponentChecks.target-group");
            case ATTRIBUTE_GROUP_REF -> I18n.t("XsdComponentChecks.target-attribute-group");
            default -> I18n.t("XsdComponentChecks.target-type");
        };
    }

    private static String describeReferrer(XsdNode node) {
        if (node == null) {
            return I18n.t("XsdComponentChecks.unknown-node");
        }
        String kind = node.getClass().getSimpleName().replace("Xsd", "").toLowerCase(java.util.Locale.ROOT);
        String name = node.getName();
        if (name == null || name.isBlank()) {
            XsdNode parent = node.getParent();
            String parentName = parent != null && parent.getName() != null ? parent.getName() : null;
            return parentName != null ? I18n.t("XsdComponentChecks.referrer-in", kind, parentName)
                    : I18n.t("XsdComponentChecks.referrer-anonymous", kind);
        }
        return I18n.t("XsdComponentChecks.referrer-named", kind, name);
    }
}
