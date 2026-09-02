package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.XPathValidationIssue;

/**
 * "XPath Validation" sub-tab: findings for the XPath expressions used by identity constraints
 * and assertions (syntax, unknown element names, unsupported axes …). Selecting a row reveals
 * the owning constraint in the Tree view.
 */
final class XPathSection extends VBox {

    private final FlowPane chips = new FlowPane(6, 6);
    private final TableView<XPathValidationIssue> table = new TableView<>();

    XPathSection(EditorHost editorHost) {
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);
        chips.setAlignment(Pos.CENTER_LEFT);

        table.setId("analysis-xpath-table");
        table.getColumns().add(AnalysisSupport.column("Severity", i -> AnalysisSupport.titleCase(i.severity()), 90));
        table.getColumns().add(AnalysisSupport.column("Constraint", XPathValidationIssue::constraintName, 160));
        table.getColumns().add(AnalysisSupport.column("Source", XPathValidationIssue::getSourceDescription, 130));
        table.getColumns().add(AnalysisSupport.column("XPath", XPathValidationIssue::xpath, 220));
        table.getColumns().add(AnalysisSupport.column("Message", XPathValidationIssue::message, -1));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(AnalysisSupport.emptyLabel("All constraint XPath expressions are valid."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && n.sourceNode() != null && editorHost != null) {
                editorHost.revealSchemaNodeByXPath(n.sourceNode().getXPath());
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Label note = AnalysisSupport.emptyLabel(
                "Expressions are checked statically against the schema; evaluation against a sample XML is not part of this report.");
        note.setWrapText(true);

        getChildren().addAll(chips, table, note);
    }

    void setData(SchemaAnalysisData data) {
        ValidationResult r = data.xpath();
        chips.getChildren().clear();
        if (r.issues().isEmpty()) {
            chips.getChildren().add(AnalysisSupport.chip("No findings", "ok"));
        }
        if (r.errorCount() > 0) {
            chips.getChildren().add(AnalysisSupport.chip(AnalysisSupport.plural(r.errorCount(), "error"), "error"));
        }
        if (r.warningCount() > 0) {
            chips.getChildren().add(AnalysisSupport.chip(AnalysisSupport.plural(r.warningCount(), "warning"), "warning"));
        }
        if (r.infoCount() > 0) {
            chips.getChildren().add(AnalysisSupport.chip(AnalysisSupport.plural(r.infoCount(), "note"), "info"));
        }
        table.getItems().setAll(r.issues());
    }
}
