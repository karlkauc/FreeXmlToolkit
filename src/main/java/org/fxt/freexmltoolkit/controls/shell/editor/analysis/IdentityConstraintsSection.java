package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import javafx.geometry.Pos;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.AnalysisResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.IdentityConstraintInfo;

/**
 * "Identity Constraints" sub-tab: every {@code xs:key}, {@code xs:keyref}, {@code xs:unique}
 * and {@code xs:assert} with its selector/fields and validation status. Selecting a row
 * reveals the constraint in the Tree view.
 */
final class IdentityConstraintsSection extends VBox {

    private final FlowPane chips = new FlowPane(6, 6);
    private final TableView<IdentityConstraintInfo> table = new TableView<>();

    IdentityConstraintsSection(EditorHost editorHost) {
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);
        chips.setAlignment(Pos.CENTER_LEFT);

        table.setId("analysis-constraints-table");
        table.getColumns().add(AnalysisSupport.column("Type", IdentityConstraintInfo::getTypeDisplayName, 80));
        table.getColumns().add(AnalysisSupport.column("Name", IdentityConstraintInfo::name, 160));
        table.getColumns().add(AnalysisSupport.column("Parent element", IdentityConstraintInfo::parentElementName, 150));
        table.getColumns().add(AnalysisSupport.column("Selector", IdentityConstraintInfo::selectorXPath, 180));
        table.getColumns().add(AnalysisSupport.column("Fields",
                i -> i.fieldXPaths() == null ? "" : String.join(", ", i.fieldXPaths()), 160));
        table.getColumns().add(AnalysisSupport.column("Refers to / test",
                i -> i.isAssert() ? i.testExpression() : i.referTo(), 160));
        table.getColumns().add(AnalysisSupport.column("Status",
                i -> AnalysisSupport.titleCase(i.status())
                        + (i.statusMessage() == null || i.statusMessage().isBlank() ? "" : " – " + i.statusMessage()), -1));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(AnalysisSupport.emptyLabel("The schema declares no identity constraints or assertions."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && n.sourceNode() != null && editorHost != null) {
                editorHost.revealSchemaNodeByXPath(n.sourceNode().getXPath());
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(chips, table);
    }

    void setData(SchemaAnalysisData data) {
        AnalysisResult r = data.constraints();
        chips.getChildren().setAll(
                AnalysisSupport.chip(AnalysisSupport.plural(r.keys().size(), "key"), "info"),
                AnalysisSupport.chip(AnalysisSupport.plural(r.keyRefs().size(), "keyref"), "info"),
                AnalysisSupport.chip(AnalysisSupport.plural(r.uniques().size(), "unique"), "info"),
                AnalysisSupport.chip(AnalysisSupport.plural(r.asserts().size(), "assert"), "info"));
        if (r.errorCount() > 0) {
            chips.getChildren().add(AnalysisSupport.chip(AnalysisSupport.plural(r.errorCount(), "error"), "error"));
        }
        if (r.warningCount() > 0) {
            chips.getChildren().add(AnalysisSupport.chip(AnalysisSupport.plural(r.warningCount(), "warning"), "warning"));
        }
        if (r.errorCount() == 0 && r.warningCount() == 0 && r.totalCount() > 0) {
            chips.getChildren().add(AnalysisSupport.chip("All valid", "ok"));
        }
        table.getItems().setAll(r.getAllConstraints());
    }
}
