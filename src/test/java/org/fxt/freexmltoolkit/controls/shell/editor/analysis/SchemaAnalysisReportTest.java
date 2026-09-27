package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdSchemaReferenceInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportModel;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportSection;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportTable;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo;
import org.junit.jupiter.api.Test;

/**
 * The full report must carry every field of every engine result. The completeness guard
 * reflects over the result records and checks that each component's accessor is called in the
 * builder's source — a cheap ratchet against silently dropped fields.
 */
class SchemaAnalysisReportTest {

    /** Components deliberately not exported (model objects, redundant aggregates). */
    private static final Set<String> EXCLUDED = Set.of(
            "sourceNode",          // XsdNode — navigation only
            "schema",              // the parsed model
            "nodeCountsByType",    // rendered via getNodeCount / node-type table (accessor referenced anyway)
            "candidateKinds", "target", "referrer", "owner" // graph internals (not part of the data record)
    );

    @Test
    void everyResultFieldIsReferencedByTheBuilder() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/SchemaAnalysisReport.java"));
        List<Class<?>> records = List.of(
                SchemaAnalysisData.class,
                XsdStatistics.class, XsdStatistics.ComponentUsage.class, XsdStatistics.ComplexityMetrics.class,
                XsdStatistics.TypeUsageEntry.class, XsdSchemaReferenceInfo.class,
                XsdQualityChecker.QualityResult.class, XsdQualityChecker.QualityIssue.class,
                XsdIdentityConstraintAnalyzer.AnalysisResult.class, XsdIdentityConstraintAnalyzer.IdentityConstraintInfo.class,
                XsdXPathValidator.ValidationResult.class, XsdXPathValidator.XPathValidationIssue.class,
                ComponentInfo.class, ComponentInfo.UsageRef.class);
        List<String> missing = new ArrayList<>();
        for (Class<?> record : records) {
            assertTrue(record.isRecord(), record.getName());
            for (RecordComponent component : record.getRecordComponents()) {
                String name = component.getName();
                if (EXCLUDED.contains(name)) {
                    continue;
                }
                if (!source.contains("." + name + "()")) {
                    missing.add(record.getSimpleName() + "." + name);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Fields missing from SchemaAnalysisReport: " + missing);
    }

    @Test
    void fullReportHasAllSectionsAndSubsetsPickTheirs() throws Exception {
        SchemaAnalysisData data = SchemaAnalysisRunner.analyze(SchemaAnalysisRunnerTest.XSD, "test.xsd", null);
        ReportModel full = SchemaAnalysisReport.full(data);
        List<String> ids = full.sections().stream().map(ReportSection::id).toList();
        assertEquals(List.of("overview", "node-counts", "documentation", "cardinality", "type-usage", "types",
                "complexity", "circular-references", "quality", "identity-constraints", "xpath-validation"), ids);
        assertEquals(SchemaAnalysisReport.TITLE, full.title());
        assertEquals("test.xsd", full.subject());

        ReportSection types = section(full, "types");
        ReportTable components = types.tables().get(0);
        assertEquals(2, components.rows().size());
        assertTrue(components.rows().stream().anyMatch(r -> r.cells().get(1).equals("OrphanType") && r.cells().get(5).equals("yes")));
        assertTrue(components.rows().stream().anyMatch(r -> r.cells().get(1).equals("PersonType")
                && r.cells().get(9).contains("xs:element[@name='person']")), "Used in column carries the XPath");

        ReportSection quality = section(full, "quality");
        assertTrue(quality.facts().get(0).key().equals("Score"));
        ReportTable issues = quality.tables().get(1);
        assertEquals(data.quality().issues().size(), issues.rows().size());

        ReportSection constraints = section(full, "identity-constraints");
        assertEquals(2, constraints.tables().get(0).rows().size(), "key + keyref");
        assertTrue(constraints.tables().get(0).rows().get(1).cells().get(5).equals("personKey"), "Refers to");

        ReportSection xpath = section(full, "xpath-validation");
        assertEquals(4, xpath.tables().get(0).rows().size(), "2 selectors + 2 fields");

        assertEquals(List.of("quality"), SchemaAnalysisReport.quality(data).sections().stream().map(ReportSection::id).toList());
        assertEquals(List.of("types"), SchemaAnalysisReport.types(data).sections().stream().map(ReportSection::id).toList());
        assertEquals(List.of("identity-constraints"), SchemaAnalysisReport.constraints(data).sections().stream().map(ReportSection::id).toList());
        assertEquals(List.of("xpath-validation"), SchemaAnalysisReport.xpath(data).sections().stream().map(ReportSection::id).toList());
        assertEquals(7, SchemaAnalysisReport.statistics(data).sections().size());
    }

    private static ReportSection section(ReportModel model, String id) {
        return model.sections().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }
}
