package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Everything the Schema Analysis tool tab shows for one XSD document: the parsed schema
 * (a fresh model, independent of the editor's live model) and the four engine results.
 *
 * @param documentName display name of the analyzed document
 * @param path         the document's file, or {@code null} for unsaved buffers
 * @param schema       the parsed schema the results refer to
 * @param statistics   structural statistics incl. type usage / unused types
 * @param quality      quality checks (score, naming, best practices, duplicates …)
 * @param constraints  identity constraints (key / keyref / unique / assert)
 * @param xpath        validation of the XPath expressions used by those constraints
 */
public record SchemaAnalysisData(String documentName,
                                 Path path,
                                 XsdSchema schema,
                                 XsdStatistics statistics,
                                 XsdQualityChecker.QualityResult quality,
                                 XsdIdentityConstraintAnalyzer.AnalysisResult constraints,
                                 XsdXPathValidator.ValidationResult xpath) {
}
