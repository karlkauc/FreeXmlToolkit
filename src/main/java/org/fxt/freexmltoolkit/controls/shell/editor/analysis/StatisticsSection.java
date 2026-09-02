package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsExporter;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * "Statistics" sub-tab: a KPI row with the declaration counts, detail cards (schema, constraints,
 * cardinality with an optional/required bar, documentation with a coverage bar) in an even grid,
 * the most used named types with usage bars, and the list of unused named types. Selecting a type
 * reveals it in the Tree view. Exports via {@link XsdStatisticsExporter}.
 */
final class StatisticsSection extends VBox {

    /** Detail cards per grid row. */
    private static final int CARD_COLUMNS = 3;

    private final Label status = new Label();
    private final HBox kpis = new HBox(12);
    private final GridPane cards = new GridPane();
    private final TableView<XsdStatistics.TypeUsageEntry> topTypes = new TableView<>();
    private final ListView<String> unusedTypes = new ListView<>();
    private final Label unusedTitle = AnalysisSupport.groupTitle("Unused types");
    private final Label topTypesTitle = AnalysisSupport.groupTitle("Most used types");
    private final Set<String> simpleTypeNames = new HashSet<>();
    private XsdStatistics statistics;
    private int maxUsage = 1;

    StatisticsSection(EditorHost editorHost) {
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        status.getStyleClass().add("fxt-placeholder-text");
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        HBox toolbar = new HBox(8, AnalysisSupport.exportMenu("Schema Statistics", status, this::export), status);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        kpis.setId("analysis-kpis");
        kpis.setAlignment(Pos.CENTER_LEFT);

        cards.setId("analysis-cards");
        cards.setHgap(12);
        cards.setVgap(12);
        for (int i = 0; i < CARD_COLUMNS; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / CARD_COLUMNS);
            cc.setFillWidth(true);
            cards.getColumnConstraints().add(cc);
        }

        topTypes.setId("analysis-top-types");
        topTypes.getStyleClass().add("fxt-analysis-table");
        TableColumn<XsdStatistics.TypeUsageEntry, String> nameColumn =
                AnalysisSupport.column("Type", XsdStatistics.TypeUsageEntry::typeName, 260);
        topTypes.getColumns().add(nameColumn);
        topTypes.getColumns().add(usageColumn());
        topTypes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        topTypes.setPlaceholder(AnalysisSupport.emptyLabel("No user-defined types are referenced."));
        topTypes.setMinHeight(180);
        topTypes.setPrefHeight(220);
        topTypes.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && editorHost != null) {
                editorHost.revealTypeByName(newV.typeName());
            }
        });

        unusedTypes.setId("analysis-unused-types");
        unusedTypes.getStyleClass().add("fxt-analysis-list");
        unusedTypes.setPlaceholder(AnalysisSupport.emptyLabel("All named types are used."));
        unusedTypes.setMinHeight(180);
        unusedTypes.setPrefHeight(220);
        unusedTypes.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(AnalysisSupport.icon(simpleTypeNames.contains(item) ? "bi-type" : "bi-diagram-3", 14));
                }
            }
        });
        unusedTypes.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && editorHost != null) {
                editorHost.revealTypeByName(newV);
            }
        });

        VBox topBox = new VBox(6, topTypesTitle, topTypes);
        VBox unusedBox = new VBox(6, unusedTitle, unusedTypes);
        VBox.setVgrow(topTypes, Priority.ALWAYS);
        VBox.setVgrow(unusedTypes, Priority.ALWAYS);
        HBox.setHgrow(topBox, Priority.ALWAYS);
        HBox.setHgrow(unusedBox, Priority.ALWAYS);
        HBox typeRow = new HBox(12, topBox, unusedBox);
        VBox.setVgrow(typeRow, Priority.ALWAYS);

        VBox content = new VBox(16, kpis, cards, typeRow);
        content.setPadding(new Insets(4, 4, 12, 4));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        // Let the type tables fill the remaining height instead of leaving dead space below them.
        content.minHeightProperty().bind(scroll.heightProperty().subtract(2));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(toolbar, scroll);
    }

    void setData(SchemaAnalysisData data) {
        statistics = data.statistics();
        XsdStatistics s = statistics;
        simpleTypeNames.clear();
        if (data.schema() != null) {
            for (XsdNode child : data.schema().getChildren()) {
                if (child.getNodeType() == XsdNodeType.SIMPLE_TYPE && child.getName() != null) {
                    simpleTypeNames.add(child.getName());
                }
            }
        }

        kpis.getChildren().setAll(
                kpi("Elements", s.getElementCount(), "bi-box"),
                kpi("Attributes", s.getAttributeCount(), "bi-at"),
                kpi("Complex types", s.getComplexTypeCount(), "bi-diagram-3"),
                kpi("Simple types", s.getSimpleTypeCount(), "bi-braces"),
                kpi("Groups", s.getGroupCount(), "bi-collection"),
                kpi("Attribute groups", s.getAttributeGroupCount(), "bi-tags"));

        List<Node> cardList = new ArrayList<>();
        cardList.add(card("Schema", "bi-file-earmark-code", null, rows(
                row("XSD version", s.isXsd11() ? "1.1" : "1.0"),
                namespaceRow(s.targetNamespace()),
                row("elementFormDefault", s.elementFormDefault()),
                row("attributeFormDefault", s.attributeFormDefault()),
                row("Namespaces", s.namespaceCount()),
                row("Total nodes", s.totalNodeCount()))));
        cardList.add(filesCard(s));
        cardList.add(card("Constraints", "bi-key", null, rows(
                row("Keys", s.getNodeCount(XsdNodeType.KEY)),
                row("Key references", s.getNodeCount(XsdNodeType.KEYREF)),
                row("Unique", s.getNodeCount(XsdNodeType.UNIQUE)),
                row("Assertions", s.getNodeCount(XsdNodeType.ASSERT)))));
        cardList.add(card("Documentation", "bi-bookmark",
                coverageBar(s.documentationCoveragePercent()), rows(
                        row("Documented nodes", s.nodesWithDocumentation()),
                        row("Nodes with appinfo", s.nodesWithAppInfo()),
                        row("Languages", s.documentationLanguages() == null || s.documentationLanguages().isEmpty()
                                ? "(none)" : String.join(", ", s.documentationLanguages().stream().sorted().toList())))));
        cardList.add(card("Cardinality", "bi-layers",
                cardinalityBar(s.optionalElements(), s.requiredElements()), rows(
                        row("Optional elements", s.optionalElements()),
                        row("Required elements", s.requiredElements()),
                        row("Unbounded elements", s.unboundedElements()))));
        layoutCards(cardList);

        List<XsdStatistics.TypeUsageEntry> top = s.topUsedTypes() == null ? List.of() : s.topUsedTypes();
        maxUsage = Math.max(1, top.stream().mapToInt(XsdStatistics.TypeUsageEntry::usageCount).max().orElse(1));
        topTypes.getItems().setAll(top);
        List<String> unused = s.unusedTypes() == null ? List.of() : s.unusedTypes().stream().sorted().toList();
        unusedTypes.getItems().setAll(unused);
        unusedTitle.setText("UNUSED TYPES (" + unused.size() + ")");
        topTypesTitle.setText("MOST USED TYPES (" + top.size() + ")");
        status.setText("");
    }

    // ---------------------------------------------------------------- KPI tiles

    private static VBox kpi(String label, int value, String icon) {
        Label number = new Label(Integer.toString(value));
        number.getStyleClass().add("fxt-analysis-stat-number");
        Label caption = new Label(label, AnalysisSupport.icon(icon, 14));
        caption.setGraphicTextGap(6);
        caption.getStyleClass().add("fxt-analysis-stat-label");
        VBox tile = new VBox(4, number, caption);
        tile.getStyleClass().add("fxt-analysis-stat");
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    // ---------------------------------------------------------------- detail cards

    /** One key/value line of a card: {@code key} left, {@code value} right-aligned. */
    private record Row(Label key, Label value) {
    }

    private static Row row(String key, int value) {
        return row(key, Integer.toString(value));
    }

    private static Row row(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("fxt-analysis-key");
        Label v = new Label(value);
        v.getStyleClass().add("fxt-analysis-value");
        v.setMinWidth(0);
        v.setMaxWidth(Double.MAX_VALUE);
        return new Row(k, v);
    }

    private static Row namespaceRow(String namespace) {
        boolean none = namespace == null || namespace.isBlank();
        Row r = row("Target namespace", none ? "(none)" : namespace);
        if (!none) {
            r.value().getStyleClass().add("fxt-analysis-value-mono");
            r.value().setTooltip(new Tooltip(namespace));
        }
        return r;
    }

    private static List<Row> rows(Row... rows) {
        return List.of(rows);
    }

    private static VBox card(String title, String icon, Node visual, List<Row> rows) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        ColumnConstraints keyCol = new ColumnConstraints();
        keyCol.setHgrow(Priority.NEVER);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        valueCol.setHalignment(HPos.RIGHT);
        valueCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(keyCol, valueCol);
        for (int i = 0; i < rows.size(); i++) {
            grid.add(rows.get(i).key(), 0, i);
            grid.add(rows.get(i).value(), 1, i);
        }
        Label heading = AnalysisSupport.groupTitle(title);
        heading.setGraphic(AnalysisSupport.icon(icon, 13));
        heading.setGraphicTextGap(6);
        VBox box = new VBox(8, heading);
        if (visual != null) {
            box.getChildren().add(visual);
        }
        box.getChildren().add(grid);
        box.getStyleClass().add("fxt-analysis-group");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setMaxHeight(Double.MAX_VALUE);
        return box;
    }

    /** Schema files, includes / imports, unresolved references and (for multi-file schemas) nodes per file. */
    private static VBox filesCard(XsdStatistics s) {
        List<Row> rows = new ArrayList<>(rows(
                row("Schema files", s.fileCount()),
                row("Includes / imports", s.getIncludeCount() + " / " + s.getImportCount()),
                row("Unresolved references", s.unresolvedReferencesCount())));
        Map<Path, Map<XsdNodeType, Integer>> countsByFile = s.nodeCountsByFile();
        if (countsByFile != null && countsByFile.size() > 1) {
            countsByFile.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> row(
                            e.getKey().getFileName() != null ? e.getKey().getFileName().toString() : e.getKey().toString(),
                            AnalysisSupport.plural(e.getValue().values().stream().mapToInt(Integer::intValue).sum(), "node")))
                    .forEach(rows::add);
            rows.get(3).key().getStyleClass().add("fxt-analysis-key-section");
            rows.get(3).value().getStyleClass().add("fxt-analysis-key-section");
        }
        return card("Files", "bi-files", null, rows);
    }

    private void layoutCards(List<Node> cardList) {
        cards.getChildren().clear();
        cards.getRowConstraints().clear();
        int rowCount = (cardList.size() + CARD_COLUMNS - 1) / CARD_COLUMNS;
        for (int r = 0; r < rowCount; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setFillHeight(true);
            rc.setVgrow(Priority.NEVER);
            cards.getRowConstraints().add(rc);
        }
        for (int i = 0; i < cardList.size(); i++) {
            int column = i % CARD_COLUMNS;
            boolean last = i == cardList.size() - 1;
            int span = last ? CARD_COLUMNS - column : 1;
            cards.add(cardList.get(i), column, i / CARD_COLUMNS, span, 1);
        }
    }

    // ---------------------------------------------------------------- bars

    /** Coverage band used to tint the documentation bar: "good" ≥ 75 %, "fair" ≥ 40 %, else "poor". */
    static String coverageBand(double percent) {
        return percent >= 75 ? "good" : percent >= 40 ? "fair" : "poor";
    }

    private static Node coverageBar(double percent) {
        double clamped = Math.max(0, Math.min(100, percent));
        ProgressBar bar = new ProgressBar(clamped / 100.0);
        bar.setId("analysis-coverage-bar");
        bar.getStyleClass().addAll("fxt-analysis-bar", "fxt-analysis-bar-" + coverageBand(clamped));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(8);
        HBox.setHgrow(bar, Priority.ALWAYS);
        Label value = new Label(String.format(Locale.ROOT, "%.1f%%", clamped));
        value.getStyleClass().add("fxt-analysis-bar-value");
        Label caption = new Label("Coverage");
        caption.getStyleClass().add("fxt-analysis-key");
        HBox head = new HBox(8, caption, spacer(), value);
        head.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, head, bar);
    }

    /** Two-segment bar: optional vs. required elements (hidden when there are no elements). */
    private static Node cardinalityBar(int optional, int required) {
        int total = optional + required;
        if (total <= 0) {
            return null;
        }
        Region optionalSeg = segment("fxt-analysis-seg-optional");
        Region requiredSeg = segment("fxt-analysis-seg-required");
        HBox track = new HBox(optionalSeg, requiredSeg);
        track.getStyleClass().add("fxt-analysis-seg-track");
        track.setPrefHeight(8);
        track.setMaxWidth(Double.MAX_VALUE);
        optionalSeg.prefWidthProperty().bind(track.widthProperty().multiply((double) optional / total));
        requiredSeg.prefWidthProperty().bind(track.widthProperty().multiply((double) required / total));
        Label legendOptional = legend("Optional " + percent(optional, total), "fxt-analysis-seg-optional");
        Label legendRequired = legend("Required " + percent(required, total), "fxt-analysis-seg-required");
        HBox legend = new HBox(12, legendOptional, legendRequired);
        legend.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, legend, track);
    }

    private static Region segment(String styleClass) {
        Region seg = new Region();
        seg.getStyleClass().addAll("fxt-analysis-seg", styleClass);
        seg.setMinWidth(0);
        return seg;
    }

    private static Label legend(String text, String swatchClass) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("fxt-analysis-seg-swatch", swatchClass);
        swatch.setMinSize(8, 8);
        swatch.setPrefSize(8, 8);
        swatch.setMaxSize(8, 8);
        Label label = new Label(text, swatch);
        label.setGraphicTextGap(5);
        label.getStyleClass().add("fxt-analysis-key");
        return label;
    }

    private static String percent(int part, int total) {
        return String.format(Locale.ROOT, "%.0f%%", 100.0 * part / total);
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ---------------------------------------------------------------- usage column

    /** "Usages" column: a bar proportional to the most used type plus the count. */
    private TableColumn<XsdStatistics.TypeUsageEntry, Number> usageColumn() {
        TableColumn<XsdStatistics.TypeUsageEntry, Number> column = new TableColumn<>("Usages");
        column.setPrefWidth(160);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().usageCount()));
        column.setCellFactory(col -> new TableCell<>() {
            private final Region fill = new Region();
            private final StackPane track = new StackPane(fill);
            private final Label count = new Label();
            private final HBox box = new HBox(8, track, count);

            {
                fill.getStyleClass().add("fxt-analysis-usage-fill");
                track.getStyleClass().add("fxt-analysis-usage-track");
                track.setAlignment(Pos.CENTER_LEFT);
                track.setPrefHeight(8);
                track.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(track, Priority.ALWAYS);
                count.getStyleClass().add("fxt-analysis-value");
                count.setMinWidth(32);
                count.setAlignment(Pos.CENTER_RIGHT);
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                double fraction = Math.min(1.0, item.doubleValue() / maxUsage);
                fill.prefWidthProperty().unbind();
                fill.prefWidthProperty().bind(track.widthProperty().multiply(fraction));
                fill.setMaxWidth(Region.USE_PREF_SIZE);
                count.setText(Integer.toString(item.intValue()));
                setGraphic(box);
            }
        });
        return column;
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
