package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.AnalysisResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.IdentityConstraintInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.IssueSeverity;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.NamingConvention;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.QualityIssue;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.QualityResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdSchemaReferenceInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics.ComplexityMetrics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics.ComponentUsage;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics.TypeUsageEntry;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.XPathValidationIssue;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.KeyValue;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportModel;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportRow;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportSection;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportTable;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.Tone;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo.UsageRef;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * Turns a {@link SchemaAnalysisData} into the format-independent {@link ReportModel}. The full
 * report carries <em>every</em> field of every engine result (a test enforces this by
 * reflection); the per-tab exports are subsets of the same sections.
 */
public final class SchemaAnalysisReport {

    public static final String TITLE = "Schema Analysis Report";

    // Section ids (stable: JSON keys, HTML anchors)
    static final String OVERVIEW = "overview";
    static final String NODE_COUNTS = "node-counts";
    static final String DOCUMENTATION = "documentation";
    static final String CARDINALITY = "cardinality";
    static final String TYPE_USAGE = "type-usage";
    static final String TYPES = "types";
    static final String COMPLEXITY = "complexity";
    static final String CIRCULAR = "circular-references";
    static final String QUALITY = "quality";
    static final String CONSTRAINTS = "identity-constraints";
    static final String XPATH = "xpath-validation";

    private SchemaAnalysisReport() {
    }

    /** The complete report with all sections. */
    public static ReportModel full(SchemaAnalysisData data) {
        List<ReportSection> sections = new ArrayList<>();
        sections.add(overview(data));
        sections.add(nodeCounts(data.statistics()));
        sections.add(documentation(data.statistics()));
        sections.add(cardinality(data.statistics()));
        sections.add(typeUsage(data.statistics()));
        sections.add(types(data.components()));
        sections.add(complexity(data.statistics().complexity()));
        sections.add(circularReferences(data.statistics().componentUsage()));
        sections.add(quality(data.quality()));
        sections.add(constraints(data.constraints()));
        sections.add(xpathSection(data));
        return new ReportModel(TITLE, subject(data), data.statistics().collectedAt(), sections);
    }

    /** The Statistics tab: overview, node counts, documentation, cardinality, type usage, complexity, cycles. */
    public static ReportModel statistics(SchemaAnalysisData data) {
        return full(data).only(OVERVIEW, NODE_COUNTS, DOCUMENTATION, CARDINALITY, TYPE_USAGE, COMPLEXITY, CIRCULAR);
    }

    /** The Types tab. */
    public static ReportModel types(SchemaAnalysisData data) {
        return full(data).only(TYPES);
    }

    /** The Quality Checks tab. */
    public static ReportModel quality(SchemaAnalysisData data) {
        return full(data).only(QUALITY);
    }

    /** The Identity Constraints tab. */
    public static ReportModel constraints(SchemaAnalysisData data) {
        return full(data).only(CONSTRAINTS);
    }

    /** The XPath Validation tab. */
    public static ReportModel xpath(SchemaAnalysisData data) {
        return full(data).only(XPATH);
    }

    private static String subject(SchemaAnalysisData data) {
        return data.path() != null ? data.documentName() + " (" + data.path() + ")" : data.documentName();
    }

    // ---------------------------------------------------------------- sections

    private static ReportSection overview(SchemaAnalysisData data) {
        XsdStatistics s = data.statistics();
        List<KeyValue> facts = new ArrayList<>();
        facts.add(KeyValue.of("Document", data.documentName()));
        facts.add(KeyValue.of("Path", data.path() != null ? data.path().toString() : "(unsaved)"));
        facts.add(KeyValue.of("XSD version", s.xsdVersion()));
        facts.add(KeyValue.of("Target namespace", s.targetNamespace().isEmpty() ? "(none)" : s.targetNamespace()));
        facts.add(KeyValue.of("elementFormDefault", s.elementFormDefault()));
        facts.add(KeyValue.of("attributeFormDefault", s.attributeFormDefault()));
        facts.add(KeyValue.of("Namespaces declared", s.namespaceCount()));
        facts.add(KeyValue.of("Schema files", s.fileCount()));
        facts.add(KeyValue.of("Main schema path", s.mainSchemaPath() != null ? s.mainSchemaPath().toString() : ""));
        facts.add(KeyValue.of("Included files", join(s.includedFiles().stream().map(Path::toString).sorted().toList())));
        facts.add(KeyValue.of("Includes", s.getIncludeCount()));
        facts.add(KeyValue.of("Imports", s.getImportCount()));
        facts.add(KeyValue.of("Unresolved references", s.unresolvedReferencesCount(),
                s.unresolvedReferencesCount() > 0 ? Tone.ERROR : Tone.NONE));
        facts.add(KeyValue.of("Total nodes", s.totalNodeCount()));
        facts.add(KeyValue.of("Collected at", s.collectedAt().format(org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportWriters.TIMESTAMP)));

        List<ReportRow> rows = new ArrayList<>();
        for (XsdSchemaReferenceInfo ref : s.schemaReferences()) {
            rows.add(ReportRow.withTone(ref.resolved() ? Tone.NONE : Tone.ERROR,
                    ref.getTypeDisplayName(), ref.schemaLocation(), ref.getStatusDisplayName(),
                    ref.namespace(), ref.resolvedPath() != null ? ref.resolvedPath().toString() : "",
                    ref.elementCount(), ref.typeCount(), ref.groupCount(), ref.errorMessage()));
        }
        ReportTable references = new ReportTable("schema-references", "Schema references (includes / imports)",
                List.of("Type", "Location", "Status", "Namespace", "Resolved path", "Elements", "Types", "Groups", "Error"), rows);
        return new ReportSection(OVERVIEW, "Overview", facts, List.of(references), null);
    }

    private static ReportSection nodeCounts(XsdStatistics s) {
        List<ReportRow> byType = new ArrayList<>();
        for (XsdNodeType type : XsdNodeType.values()) {
            int count = s.nodeCountsByType().getOrDefault(type, 0);
            if (count > 0) {
                byType.add(ReportRow.of(nodeTypeLabel(type), count));
            }
        }
        List<KeyValue> facts = List.of(
                KeyValue.of("Total nodes", s.totalNodeCount()),
                KeyValue.of("Elements", s.getElementCount()),
                KeyValue.of("Attributes", s.getAttributeCount()),
                KeyValue.of("Complex types", s.getComplexTypeCount()),
                KeyValue.of("Simple types", s.getSimpleTypeCount()),
                KeyValue.of("Groups", s.getGroupCount()),
                KeyValue.of("Attribute groups", s.getAttributeGroupCount()));
        List<ReportRow> perFile = new ArrayList<>();
        List<Path> files = new ArrayList<>(s.nodeCountsByFile().keySet());
        files.sort(Comparator.comparing(Path::toString));
        for (Path file : files) {
            Map<XsdNodeType, Integer> counts = s.nodeCountsByFile().get(file);
            perFile.add(ReportRow.of(file.getFileName() != null ? file.getFileName().toString() : file.toString(),
                    file.toString(), s.getTotalNodeCountForFile(file),
                    counts.getOrDefault(XsdNodeType.ELEMENT, 0), counts.getOrDefault(XsdNodeType.ATTRIBUTE, 0),
                    counts.getOrDefault(XsdNodeType.COMPLEX_TYPE, 0), counts.getOrDefault(XsdNodeType.SIMPLE_TYPE, 0),
                    counts.getOrDefault(XsdNodeType.GROUP, 0), counts.getOrDefault(XsdNodeType.ATTRIBUTE_GROUP, 0)));
        }
        return new ReportSection(NODE_COUNTS, "Node counts", facts, List.of(
                new ReportTable("by-type", "Nodes by type", List.of("Node type", "Count"), byType),
                new ReportTable("by-file", "Nodes per file",
                        List.of("File", "Path", "Total", "Elements", "Attributes", "Complex types", "Simple types", "Groups", "Attribute groups"), perFile)),
                null);
    }

    private static ReportSection documentation(XsdStatistics s) {
        double coverage = s.documentationCoveragePercent();
        List<KeyValue> facts = List.of(
                KeyValue.of("Documentation coverage", String.format("%.1f %%", coverage),
                        coverage >= 75 ? Tone.OK : coverage >= 40 ? Tone.WARNING : Tone.ERROR),
                KeyValue.of("Nodes with documentation", s.nodesWithDocumentation()),
                KeyValue.of("Nodes with appinfo", s.nodesWithAppInfo()),
                KeyValue.of("Documentation languages", join(s.documentationLanguages().stream().sorted().toList())));
        List<ReportRow> tags = new ArrayList<>();
        new TreeMap<>(s.appInfoTagCounts()).forEach((tag, count) -> tags.add(ReportRow.of(tag, count)));
        return new ReportSection(DOCUMENTATION, "Documentation", facts,
                List.of(new ReportTable("appinfo-tags", "AppInfo tags", List.of("Tag", "Count"), tags)), null);
    }

    private static ReportSection cardinality(XsdStatistics s) {
        return new ReportSection(CARDINALITY, "Cardinality", List.of(
                KeyValue.of("Optional elements (minOccurs=0)", s.optionalElements()),
                KeyValue.of("Required elements (minOccurs≥1)", s.requiredElements()),
                KeyValue.of("Unbounded elements (maxOccurs=unbounded)", s.unboundedElements())),
                List.of(), null);
    }

    private static ReportSection typeUsage(XsdStatistics s) {
        ComponentUsage usage = s.componentUsage();
        List<KeyValue> facts = List.of(
                KeyValue.of("Named types", s.typeUsageCounts().size()),
                KeyValue.of("Unused types", s.unusedTypes().size(), s.unusedTypes().isEmpty() ? Tone.NONE : Tone.WARNING),
                KeyValue.of("Unused groups", usage.unusedGroups().size(), usage.unusedGroups().isEmpty() ? Tone.NONE : Tone.WARNING),
                KeyValue.of("Unused attribute groups", usage.unusedAttributeGroups().size(), usage.unusedAttributeGroups().isEmpty() ? Tone.NONE : Tone.WARNING),
                KeyValue.of("Unreachable components", usage.unreachableComponents().size()),
                KeyValue.of("Single-use types", usage.singleUseTypes().size()));
        List<ReportRow> all = new ArrayList<>();
        s.typeUsageCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> all.add(ReportRow.withTone(e.getValue() == 0 ? Tone.WARNING : Tone.NONE, e.getKey(), e.getValue())));
        List<ReportRow> top = new ArrayList<>();
        for (TypeUsageEntry entry : s.topUsedTypes()) {
            top.add(ReportRow.of(entry.typeName(), entry.usageCount()));
        }
        return new ReportSection(TYPE_USAGE, "Type usage", facts, List.of(
                new ReportTable("usage", "Usage per named type", List.of("Type", "Usages"), all),
                new ReportTable("top", "Most used types", List.of("Type", "Usages"), top),
                names("unused-types", "Unused types", s.unusedTypes().stream().sorted().toList(), Tone.WARNING),
                names("unused-groups", "Unused groups", usage.unusedGroups(), Tone.WARNING),
                names("unused-attribute-groups", "Unused attribute groups", usage.unusedAttributeGroups(), Tone.WARNING),
                names("unreachable", "Unreachable components (removable)", usage.unreachableComponents(), Tone.WARNING),
                names("single-use", "Single-use types (inline candidates)", usage.singleUseTypes(), Tone.SUGGESTION)),
                "Unused = never referenced. Unreachable = not reachable from any global element or attribute, directly or through other components.");
    }

    private static ReportSection types(List<ComponentInfo> components) {
        List<ReportRow> rows = new ArrayList<>();
        for (ComponentInfo c : components) {
            List<String> usedIn = new ArrayList<>();
            for (UsageRef ref : c.usages()) {
                usedIn.add(ref.referrerDisplay() + " @ " + ref.referrerXPath()
                        + (ref.fromImport() ? " [import]" : "")
                        + (ref.sourceFileName() != null && !ref.sourceFileName().isBlank() && !"(main)".equals(ref.sourceFileName())
                        ? " [" + ref.sourceFileName() + "]" : ""));
            }
            Tone tone = c.isUnused() ? Tone.ERROR : c.unreachable() ? Tone.WARNING : c.isSingleUse() ? Tone.SUGGESTION : Tone.NONE;
            rows.add(ReportRow.withTone(tone, c.kind().getDisplayName(), c.name(), c.baseType(), c.documentation(),
                    c.usageCount(), c.unreachable() ? "yes" : "no", c.fromInclude() ? "yes" : "no",
                    c.sourceFileName(), c.xpath(), String.join("; ", usedIn)));
        }
        return new ReportSection(TYPES, "Types", List.of(KeyValue.of("Components", components.size())), List.of(
                new ReportTable("components", "Global types, groups and attribute groups",
                        List.of("Kind", "Name", "Base", "Documentation", "Usages", "Unreachable", "From include", "Source file", "XPath", "Used in"), rows)),
                null);
    }

    private static ReportSection complexity(ComplexityMetrics c) {
        List<KeyValue> facts = List.of(
                KeyValue.of("Max element nesting depth", c.maxElementNestingDepth()),
                KeyValue.of("Deepest element", c.deepestElementXPath()),
                KeyValue.of("Avg declarations per complex type", c.avgChildrenPerComplexType()),
                KeyValue.of("Max declarations in one complex type", c.maxChildrenPerComplexType()),
                KeyValue.of("Widest complex type", c.widestComplexType()),
                KeyValue.of("Max type derivation depth", c.maxTypeDerivationDepth()),
                KeyValue.of("Abstract types", c.abstractTypes()),
                KeyValue.of("Abstract elements", c.abstractElements()),
                KeyValue.of("Substitution group heads", c.substitutionGroupHeads()),
                KeyValue.of("Substitution group members", c.substitutionGroupMembers()),
                KeyValue.of("Anonymous complex types", c.anonymousComplexTypes()),
                KeyValue.of("Anonymous simple types", c.anonymousSimpleTypes()),
                KeyValue.of("Mixed content types", c.mixedContentTypes()),
                KeyValue.of("Extensions", c.extensions()),
                KeyValue.of("Restrictions", c.restrictions()),
                KeyValue.of("Facets (total)", c.totalFacets()),
                KeyValue.of("Enumeration values", c.enumerationValues()),
                KeyValue.of("XSD 1.1 features", c.xsd11Features()));
        List<ReportRow> facets = new ArrayList<>();
        for (XsdFacetType type : XsdFacetType.values()) {
            int count = c.facetCounts().getOrDefault(type, 0);
            if (count > 0) {
                facets.add(ReportRow.of(type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), count));
            }
        }
        return new ReportSection(COMPLEXITY, "Complexity", facts,
                List.of(new ReportTable("facets", "Facet usage", List.of("Facet", "Count"), facets)),
                "Nesting depth counts lexical element nesting only (type references are not followed).");
    }

    private static ReportSection circularReferences(ComponentUsage usage) {
        List<ReportRow> derivation = new ArrayList<>();
        for (List<String> cycle : usage.derivationCycles()) {
            derivation.add(ReportRow.withTone(Tone.ERROR, chain(cycle), cycle.size()));
        }
        List<ReportRow> containment = new ArrayList<>();
        for (List<String> cycle : usage.containmentCycles()) {
            containment.add(ReportRow.withTone(Tone.INFO, chain(cycle), cycle.size()));
        }
        return new ReportSection(CIRCULAR, "Circular references", List.of(
                KeyValue.of("Derivation cycles (errors)", derivation.size(), derivation.isEmpty() ? Tone.NONE : Tone.ERROR),
                KeyValue.of("Recursive content models", containment.size())), List.of(
                new ReportTable("derivation", "Type derivation cycles", List.of("Chain", "Members"), derivation),
                new ReportTable("containment", "Recursive content models", List.of("Chain", "Members"), containment)),
                null);
    }

    private static ReportSection quality(QualityResult q) {
        List<KeyValue> facts = List.of(
                KeyValue.of("Score", q.score() + " / 100", scoreTone(q.score())),
                KeyValue.of("Rating", q.getScoreDescription()),
                KeyValue.of("Total checks", q.totalChecks()),
                KeyValue.of("Passed checks", q.passedChecks()),
                KeyValue.of("Issues", q.issues().size()),
                KeyValue.of("Dominant naming convention", q.dominantNamingConvention() != null ? q.dominantNamingConvention().getDisplayName() : ""));
        List<ReportRow> naming = new ArrayList<>();
        for (NamingConvention convention : NamingConvention.values()) {
            int count = q.namingDistribution().getOrDefault(convention, 0);
            if (count > 0) {
                naming.add(ReportRow.withTone(convention == q.dominantNamingConvention() ? Tone.OK : Tone.NONE,
                        convention.getDisplayName(), count));
            }
        }
        List<QualityIssue> sorted = new ArrayList<>(q.issues());
        sorted.sort(Comparator.comparing(QualityIssue::severity).thenComparing(QualityIssue::category));
        List<ReportRow> issues = new ArrayList<>();
        for (QualityIssue issue : sorted) {
            issues.add(ReportRow.withTone(tone(issue.severity()),
                    issue.severity().name(), AnalysisSupport.titleCase(issue.category()), issue.message(), issue.suggestion(),
                    issue.xpath(), issue.getSourceFileName(), issue.affectedElements().size(),
                    String.join("; ", issue.affectedElements())));
        }
        return new ReportSection(QUALITY, "Quality checks", facts, List.of(
                new ReportTable("naming", "Naming convention distribution", List.of("Convention", "Names"), naming),
                new ReportTable("issues", "Issues",
                        List.of("Severity", "Category", "Message", "Suggestion", "Location (XPath)", "Source file", "Affected", "Affected elements"), issues)),
                "Score = passed / total naming checks; only ERROR and WARNING issues reduce it.");
    }

    private static ReportSection constraints(AnalysisResult r) {
        List<KeyValue> facts = List.of(
                KeyValue.of("Total constraints", r.totalCount()),
                KeyValue.of("Keys", r.keys().size()),
                KeyValue.of("Key references", r.keyRefs().size()),
                KeyValue.of("Unique", r.uniques().size()),
                KeyValue.of("Assertions", r.asserts().size()),
                KeyValue.of("Errors", r.errorCount(), r.errorCount() > 0 ? Tone.ERROR : Tone.NONE),
                KeyValue.of("Warnings", r.warningCount(), r.warningCount() > 0 ? Tone.WARNING : Tone.NONE));
        List<ReportRow> rows = new ArrayList<>();
        for (IdentityConstraintInfo c : r.getAllConstraints()) {
            Tone tone = switch (c.status()) {
                case VALID -> Tone.NONE;
                case WARNING -> Tone.WARNING;
                case ERROR -> Tone.ERROR;
            };
            rows.add(ReportRow.withTone(tone, c.type().name().toLowerCase(java.util.Locale.ROOT), c.name(), c.parentElementName(),
                    c.selectorXPath(), String.join("; ", c.fieldXPaths() != null ? c.fieldXPaths() : List.of()),
                    c.referTo(), c.testExpression(), c.status().name(), c.statusMessage(),
                    c.sourceFile() != null && c.sourceFile().getFileName() != null ? c.sourceFile().getFileName().toString() : ""));
        }
        return new ReportSection(CONSTRAINTS, "Identity constraints", facts, List.of(
                new ReportTable("constraints", "Keys, key references, unique constraints and assertions",
                        List.of("Type", "Name", "Parent element", "Selector", "Fields", "Refers to", "Test", "Status", "Message", "Source file"), rows)),
                null);
    }

    private static ReportSection xpathSection(SchemaAnalysisData data) {
        ValidationResult v = data.xpath();
        List<KeyValue> facts = List.of(
                KeyValue.of("Expressions checked", v.totalXPaths()),
                KeyValue.of("Valid", v.validCount(), Tone.OK),
                KeyValue.of("Errors", v.errorCount(), v.errorCount() > 0 ? Tone.ERROR : Tone.NONE),
                KeyValue.of("Warnings", v.warningCount(), v.warningCount() > 0 ? Tone.WARNING : Tone.NONE),
                KeyValue.of("Infos", v.infoCount()));
        List<ReportRow> rows = new ArrayList<>();
        for (XPathSection.Row row : XPathSection.buildRows(data)) {
            Tone tone = switch (row.status()) {
                case VALID -> Tone.NONE;
                case ERROR -> Tone.ERROR;
                case WARNING -> Tone.WARNING;
                case INFO -> Tone.INFO;
            };
            int matches = -1;
            for (XPathValidationIssue issue : row.issues()) {
                matches = Math.max(matches, issue.matchCount());
            }
            rows.add(ReportRow.withTone(tone, row.constraintName(), XPathSection.sourceLabel(row.source()), row.xpath(),
                    row.status().name(), row.message(), matches >= 0 ? String.valueOf(matches) : ""));
        }
        return new ReportSection(XPATH, "XPath validation", facts, List.of(
                new ReportTable("expressions", "Expressions",
                        List.of("Constraint", "Source", "XPath", "Status", "Message", "Matches"), rows)),
                "Expressions are checked statically against the schema; evaluation against a sample XML is not part of this report.");
    }

    // ---------------------------------------------------------------- helpers

    private static ReportTable names(String id, String title, java.util.Collection<String> names, Tone tone) {
        List<ReportRow> rows = new ArrayList<>();
        for (String name : names) {
            rows.add(ReportRow.withTone(tone, name));
        }
        return new ReportTable(id, title, List.of("Name"), rows);
    }

    private static String chain(List<String> cycle) {
        return String.join(" → ", cycle) + (cycle.isEmpty() ? "" : " → " + cycle.get(0));
    }

    private static String join(List<String> values) {
        return String.join(", ", values);
    }

    static String nodeTypeLabel(XsdNodeType type) {
        return AnalysisSupport.titleCase(type);
    }

    static Tone tone(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> Tone.ERROR;
            case WARNING -> Tone.WARNING;
            case INFO -> Tone.INFO;
            case SUGGESTION -> Tone.SUGGESTION;
        };
    }

    static Tone scoreTone(int score) {
        return score >= 75 ? Tone.OK : score >= 40 ? Tone.WARNING : Tone.ERROR;
    }
}
