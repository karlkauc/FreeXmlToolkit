package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsCollector;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * UI-free entry point of the Schema Analysis tool: parses an XSD once, builds the reference
 * graph once and runs the statistics, quality, identity-constraint and XPath engines on the
 * result, plus the per-component usage rows of the Types tab. Meant to
 * run off the FX thread; the returned model is private to the caller.
 */
public final class SchemaAnalysisRunner {

    private SchemaAnalysisRunner() {
    }

    /**
     * Parses {@code xsdText} and analyzes it. When {@code path} is given, relative
     * {@code xs:import}/{@code xs:include} locations are resolved against its directory
     * (issue #36: without it they would fall back to a namespace-URL download).
     *
     * @param xsdText      the schema source
     * @param documentName display name shown in the report header
     * @param path         the document's file, or {@code null} for an unsaved buffer
     * @throws Exception if the text is not a parseable XSD
     */
    public static SchemaAnalysisData analyze(String xsdText, String documentName, Path path) throws Exception {
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = path != null
                ? factory.fromStringWithSchemaFile(xsdText, path, path.getParent())
                : factory.fromString(xsdText);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        return new SchemaAnalysisData(documentName, path, schema,
                new XsdStatisticsCollector(schema, graph).collect(),
                new XsdQualityChecker(schema, graph).check(),
                new XsdIdentityConstraintAnalyzer(schema).analyze(),
                new XsdXPathValidator(schema).validateAll(),
                ComponentInfo.collect(graph));
    }
}
