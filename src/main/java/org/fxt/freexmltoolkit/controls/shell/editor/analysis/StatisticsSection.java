package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsExporter;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * "Statistics" sub-tab: schema metrics as key/value groups, the most used named types, and the
 * list of unused named types (click reveals the type in the Tree view). Exports via
 * {@link XsdStatisticsExporter}.
 */
final class StatisticsSection extends VBox {

    private final EditorHost editorHost;
    private final Label status = new Label();
    private final FlowPane groups = new FlowPane(16, 16);
    private final TableView<XsdStatistics.TypeUsageEntry> topTypes = new TableView<>();
    private final ListView<String> unusedTypes = new ListView<>();
    private final Label unusedTitle = AnalysisSupport.groupTitle("Unused types");
    private final Label topTypesTitle = AnalysisSupport.groupTitle("Most used types");
    private XsdStatistics statistics;

    StatisticsSection(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        status.getStyleClass().add("fxt-placeholder-text");
        HBox toolbar = new HBox(8, AnalysisSupport.exportMenu("Schema Statistics", status, this::export), status);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        groups.setPrefWrapLength(900);

        topTypes.setId("analysis-top-types");
        topTypes.getColumns().add(AnalysisSupport.column("Type", XsdStatistics.TypeUsageEntry::typeName, 260));
        topTypes.getColumns().add(AnalysisSupport.column("Usages", e -> Integer.toString(e.usageCount()), 90));
        topTypes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        topTypes.setPlaceholder(AnalysisSupport.emptyLabel("No user-defined types are referenced."));
        topTypes.setPrefHeight(220);
        topTypes.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && editorHost != null) {
                editorHost.revealTypeByName(newV.typeName());
            }
        });

        unusedTypes.setId("analysis-unused-types");
        unusedTypes.setPlaceholder(AnalysisSupport.emptyLabel("All named types are used."));
        unusedTypes.setPrefHeight(220);
        unusedTypes.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && editorHost != null) {
                editorHost.revealTypeByName(newV);
            }
        });

        VBox topBox = new VBox(4, topTypesTitle, topTypes);
        VBox unusedBox = new VBox(4, unusedTitle, unusedTypes);
        HBox.setHgrow(topBox, Priority.ALWAYS);
        HBox.setHgrow(unusedBox, Priority.ALWAYS);
        HBox typeRow = new HBox(16, topBox, unusedBox);

        VBox content = new VBox(16, groups, typeRow);
        content.setPadding(new Insets(4, 4, 12, 4));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(toolbar, scroll);
    }

    void setData(SchemaAnalysisData data) {
        statistics = data.statistics();
        XsdStatistics s = statistics;
        groups.getChildren().setAll(
                group("Schema", new String[][]{
                        {"XSD version", s.isXsd11() ? "1.1" : "1.0"},
                        {"Target namespace", s.targetNamespace() == null || s.targetNamespace().isBlank()
                                ? "(none)" : s.targetNamespace()},
                        {"elementFormDefault", s.elementFormDefault()},
                        {"attributeFormDefault", s.attributeFormDefault()},
                        {"Namespaces", Integer.toString(s.namespaceCount())},
                        {"Schema files", Integer.toString(s.fileCount())},
                        {"Includes / imports", s.getIncludeCount() + " / " + s.getImportCount()},
                        {"Unresolved references", Integer.toString(s.unresolvedReferencesCount())}}),
                group("Declarations", new String[][]{
                        {"Elements", Integer.toString(s.getElementCount())},
                        {"Attributes", Integer.toString(s.getAttributeCount())},
                        {"Complex types", Integer.toString(s.getComplexTypeCount())},
                        {"Simple types", Integer.toString(s.getSimpleTypeCount())},
                        {"Groups", Integer.toString(s.getGroupCount())},
                        {"Attribute groups", Integer.toString(s.getAttributeGroupCount())},
                        {"Total nodes", Integer.toString(s.totalNodeCount())}}),
                group("Constraints", new String[][]{
                        {"Keys", Integer.toString(s.getNodeCount(XsdNodeType.KEY))},
                        {"Key references", Integer.toString(s.getNodeCount(XsdNodeType.KEYREF))},
                        {"Unique", Integer.toString(s.getNodeCount(XsdNodeType.UNIQUE))},
                        {"Assertions", Integer.toString(s.getNodeCount(XsdNodeType.ASSERT))}}),
                group("Cardinality", new String[][]{
                        {"Optional elements", Integer.toString(s.optionalElements())},
                        {"Required elements", Integer.toString(s.requiredElements())},
                        {"Unbounded elements", Integer.toString(s.unboundedElements())}}),
                group("Documentation", new String[][]{
                        {"Coverage", String.format(Locale.ROOT, "%.1f%%", s.documentationCoveragePercent())},
                        {"Documented nodes", Integer.toString(s.nodesWithDocumentation())},
                        {"Nodes with appinfo", Integer.toString(s.nodesWithAppInfo())},
                        {"Languages", s.documentationLanguages() == null || s.documentationLanguages().isEmpty()
                                ? "(none)" : String.join(", ", s.documentationLanguages().stream().sorted().toList())}}));
        if (s.nodeCountsByFile() != null && s.nodeCountsByFile().size() > 1) {
            groups.getChildren().add(fileGroup(s.nodeCountsByFile()));
        }

        topTypes.getItems().setAll(s.topUsedTypes() == null ? List.of() : s.topUsedTypes());
        List<String> unused = s.unusedTypes() == null ? List.of() : s.unusedTypes().stream().sorted().toList();
        unusedTypes.getItems().setAll(unused);
        unusedTitle.setText("UNUSED TYPES (" + unused.size() + ")");
        topTypesTitle.setText("MOST USED TYPES (" + topTypes.getItems().size() + ")");
        status.setText("");
    }

    private static VBox group(String title, String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(3);
        for (int i = 0; i < rows.length; i++) {
            Label key = new Label(rows[i][0]);
            key.getStyleClass().add("fxt-analysis-key");
            Label value = new Label(rows[i][1]);
            value.getStyleClass().add("fxt-analysis-value");
            grid.add(key, 0, i);
            grid.add(value, 1, i);
        }
        VBox box = new VBox(6, AnalysisSupport.groupTitle(title), grid);
        box.getStyleClass().add("fxt-analysis-group");
        return box;
    }

    private static VBox fileGroup(Map<Path, Map<XsdNodeType, Integer>> countsByFile) {
        String[][] rows = countsByFile.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new String[]{
                        e.getKey().getFileName() != null ? e.getKey().getFileName().toString() : e.getKey().toString(),
                        AnalysisSupport.plural(e.getValue().values().stream().mapToInt(Integer::intValue).sum(), "node")})
                .toArray(String[][]::new);
        return group("Nodes per file", rows);
    }

    private void export(AnalysisSupport.ExportFormat format, Path target) throws Exception {
        XsdStatisticsExporter exporter = new XsdStatisticsExporter();
        switch (format) {
            case CSV -> exporter.exportToCsv(statistics, target);
            case JSON -> exporter.exportToJson(statistics, target);
            case HTML -> exporter.exportToHtml(statistics, target);
            case PDF -> exporter.exportToPdf(statistics, target);
            case EXCEL -> exporter.exportToExcel(statistics, target);
        }
    }
}
