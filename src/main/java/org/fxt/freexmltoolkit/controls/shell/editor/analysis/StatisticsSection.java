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
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdSchemaReferenceInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKind;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * "Statistics" sub-tab: a KPI row with the declaration counts, detail cards (schema, files,
 * constraints, documentation with a coverage bar, cardinality with an optional/required bar,
 * complexity, facets) in an even grid, the schema-reference table (includes / imports with their
 * resolution status), the most used named types with usage bars, the unused named types, unused
 * groups / attribute groups and circular references. Selecting an entry reveals it in the Tree
 * view. Exports the statistics sections of {@link SchemaAnalysisReport}.
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
    private final Set<String> attributeGroupNames = new HashSet<>();
    private final TableView<XsdSchemaReferenceInfo> references = new TableView<>();
    private final VBox referencesBox;
    private final Label referencesTitle = AnalysisSupport.groupTitle("Schema references");
    private final ListView<String> unusedGroups = new ListView<>();
    private final Label unusedGroupsTitle = AnalysisSupport.groupTitle("Unused groups");
    private final ListView<String> cycles = new ListView<>();
    private final Label cyclesTitle = AnalysisSupport.groupTitle("Circular references");
    private final Map<String, String> xpathByComponent = new java.util.HashMap<>();
    private XsdStatistics statistics;
    private SchemaAnalysisData data;
    private int maxUsage = 1;

    StatisticsSection(EditorHost editorHost) {
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        status.getStyleClass().add("fxt-placeholder-text");
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        HBox toolbar = new HBox(8, AnalysisSupport.reportMenu("Schema Statistics", status,
                () -> data == null ? null : SchemaAnalysisReport.statistics(data)), status);
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

        unusedGroups.setId("analysis-unused-groups");
        unusedGroups.getStyleClass().add("fxt-analysis-list");
        unusedGroups.setPlaceholder(AnalysisSupport.emptyLabel("All groups and attribute groups are used."));
        unusedGroups.setMinHeight(120);
        unusedGroups.setPrefHeight(160);
        unusedGroups.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(AnalysisSupport.icon(attributeGroupNames.contains(item) ? "bi-tags" : "bi-collection", 14));
                }
            }
        });
        unusedGroups.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && editorHost != null) {
                String kind = attributeGroupNames.contains(newV) ? ComponentKind.ATTRIBUTE_GROUP.name() : ComponentKind.GROUP.name();
                String xpath = xpathByComponent.get(kind + ":" + newV);
                if (xpath == null || !editorHost.revealSchemaNodeByXPath(xpath)) {
                    editorHost.revealTypeByName(newV);
                }
            }
        });

        cycles.setId("analysis-cycles");
        cycles.getStyleClass().add("fxt-analysis-list");
        cycles.setPlaceholder(AnalysisSupport.emptyLabel("No circular references."));
        cycles.setMinHeight(120);
        cycles.setPrefHeight(160);
        cycles.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    boolean derivation = item.startsWith(DERIVATION_PREFIX);
                    setText(item.substring(item.indexOf(' ') + 1));
                    setGraphic(AnalysisSupport.icon(derivation ? "bi-x-circle-fill" : "bi-arrow-repeat", 14));
                    setTooltip(new Tooltip(derivation ? "Type derivation cycle — invalid schema" : "Recursive content model (legal)"));
                }
            }
        });
        cycles.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && editorHost != null) {
                String first = firstMember(newV);
                if (first != null) {
                    editorHost.revealTypeByName(first);
                }
            }
        });

        references.setId("analysis-references");
        references.getStyleClass().add("fxt-analysis-table");
        references.getColumns().add(AnalysisSupport.column("Type", XsdSchemaReferenceInfo::getTypeDisplayName, 80));
        references.getColumns().add(AnalysisSupport.column("Location", XsdSchemaReferenceInfo::schemaLocation, 260));
        references.getColumns().add(referenceStatusColumn());
        references.getColumns().add(AnalysisSupport.column("Namespace", r -> r.namespace() == null ? "" : r.namespace(), 220));
        references.getColumns().add(AnalysisSupport.column("Elements", r -> Integer.toString(r.elementCount()), 80));
        references.getColumns().add(AnalysisSupport.column("Types", r -> Integer.toString(r.typeCount()), 70));
        references.getColumns().add(AnalysisSupport.column("Groups", r -> Integer.toString(r.groupCount()), 70));
        references.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        references.setPrefHeight(150);
        references.setMinHeight(110);
        referencesBox = new VBox(6, referencesTitle, references);
        referencesBox.managedProperty().bind(referencesBox.visibleProperty());
        referencesBox.setVisible(false);

        VBox topBox = new VBox(6, topTypesTitle, topTypes);
        VBox unusedBox = new VBox(6, unusedTitle, unusedTypes);
        VBox.setVgrow(topTypes, Priority.ALWAYS);
        VBox.setVgrow(unusedTypes, Priority.ALWAYS);
        HBox.setHgrow(topBox, Priority.ALWAYS);
        HBox.setHgrow(unusedBox, Priority.ALWAYS);
        HBox typeRow = new HBox(12, topBox, unusedBox);
        VBox.setVgrow(typeRow, Priority.ALWAYS);

        VBox groupsBox = new VBox(6, unusedGroupsTitle, unusedGroups);
        VBox cyclesBox = new VBox(6, cyclesTitle, cycles);
        HBox.setHgrow(groupsBox, Priority.ALWAYS);
        HBox.setHgrow(cyclesBox, Priority.ALWAYS);
        unusedGroups.setMaxWidth(Double.MAX_VALUE);
        cycles.setMaxWidth(Double.MAX_VALUE);
        HBox groupRow = new HBox(12, groupsBox, cyclesBox);

        VBox content = new VBox(16, kpis, cards, referencesBox, typeRow, groupRow);
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
        this.data = data;
        statistics = data.statistics();
        XsdStatistics s = statistics;
        simpleTypeNames.clear();
        attributeGroupNames.clear();
        xpathByComponent.clear();
        if (data.schema() != null) {
            for (XsdNode child : data.schema().getChildren()) {
                if (child.getNodeType() == XsdNodeType.SIMPLE_TYPE && child.getName() != null) {
                    simpleTypeNames.add(child.getName());
                }
            }
        }
        for (ComponentInfo component : data.components()) {
            xpathByComponent.put(component.kind().name() + ":" + component.name(), component.xpath());
            if (component.kind() == ComponentKind.ATTRIBUTE_GROUP) {
                attributeGroupNames.add(component.name());
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
                coverageBar(s.documentationCoveragePercent()), documentationRows(s)));
        cardList.add(card("Cardinality", "bi-layers",
                cardinalityBar(s.optionalElements(), s.requiredElements()), rows(
                        row("Optional elements", s.optionalElements()),
                        row("Required elements", s.requiredElements()),
                        row("Unbounded elements", s.unboundedElements()))));
        cardList.add(complexityCard(s.complexity()));
        cardList.add(facetsCard(s.complexity()));
        layoutCards(cardList);

        List<XsdSchemaReferenceInfo> refs = s.schemaReferences() == null ? List.of() : s.schemaReferences();
        references.getItems().setAll(refs);
        referencesBox.setVisible(!refs.isEmpty());
        referencesTitle.setText("SCHEMA REFERENCES (" + refs.size() + ")"
                + (s.unresolvedReferencesCount() > 0 ? " · " + s.unresolvedReferencesCount() + " unresolved" : ""));

        XsdStatistics.ComponentUsage usage = s.componentUsage();
        List<String> groups = new ArrayList<>(usage.unusedGroups());
        groups.addAll(usage.unusedAttributeGroups());
        unusedGroups.getItems().setAll(groups);
        unusedGroupsTitle.setText("UNUSED GROUPS / ATTRIBUTE GROUPS (" + groups.size() + ")");
        List<String> cycleItems = new ArrayList<>();
        for (List<String> cycle : usage.derivationCycles()) {
            cycleItems.add(DERIVATION_PREFIX + chain(cycle));
        }
        for (List<String> cycle : usage.containmentCycles()) {
            cycleItems.add(CONTAINMENT_PREFIX + chain(cycle));
        }
        cycles.getItems().setAll(cycleItems);
        cyclesTitle.setText("CIRCULAR REFERENCES (" + cycleItems.size() + ")"
                + (usage.derivationCycles().isEmpty() ? "" : " · " + usage.derivationCycles().size() + " derivation error(s)"));

        List<XsdStatistics.TypeUsageEntry> top = s.topUsedTypes() == null ? List.of() : s.topUsedTypes();
        maxUsage = Math.max(1, top.stream().mapToInt(XsdStatistics.TypeUsageEntry::usageCount).max().orElse(1));
        topTypes.getItems().setAll(top);
        List<String> unused = s.unusedTypes() == null ? List.of() : s.unusedTypes().stream().sorted().toList();
        unusedTypes.getItems().setAll(unused);
        unusedTitle.setText("UNUSED TYPES (" + unused.size() + ")");
        topTypesTitle.setText("MOST USED TYPES (" + top.size() + ")");
        status.setText("");
    }

    private static final String DERIVATION_PREFIX = "derivation ";
    private static final String CONTAINMENT_PREFIX = "containment ";

    /** "Complex type 'A' → Simple type 'B' → …" shortened to the local names. */
    private static String chain(List<String> cycle) {
        List<String> names = cycle.stream().map(StatisticsSection::localName).toList();
        return String.join(" → ", names) + (names.isEmpty() ? "" : " → " + names.get(0));
    }

    /** The name inside the quotes of a "Kind 'name'" label, else the label itself. */
    static String localName(String label) {
        int open = label.indexOf('\'');
        int close = label.lastIndexOf('\'');
        return open >= 0 && close > open ? label.substring(open + 1, close) : label;
    }

    /** The first member name of a cycle list entry ("prefix A → B → A"). */
    static String firstMember(String item) {
        String chain = item.substring(item.indexOf(' ') + 1);
        int arrow = chain.indexOf(" → ");
        return arrow > 0 ? chain.substring(0, arrow) : chain.isBlank() ? null : chain;
    }

    private static List<Row> documentationRows(XsdStatistics s) {
        List<Row> rows = new ArrayList<>(rows(
                row("Documented nodes", s.nodesWithDocumentation()),
                row("Nodes with appinfo", s.nodesWithAppInfo()),
                row("Languages", s.documentationLanguages() == null || s.documentationLanguages().isEmpty()
                        ? "(none)" : String.join(", ", s.documentationLanguages().stream().sorted().toList()))));
        if (s.appInfoTagCounts() != null) {
            s.appInfoTagCounts().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(5)
                    .forEach(e -> rows.add(row(e.getKey() + " tags", e.getValue())));
        }
        return rows;
    }

    private static VBox complexityCard(XsdStatistics.ComplexityMetrics c) {
        Row depth = row("Max element nesting", c.maxElementNestingDepth());
        if (c.deepestElementXPath() != null) {
            depth.value().setTooltip(new Tooltip(c.deepestElementXPath()));
        }
        Row widest = row("Max declarations per type", c.maxChildrenPerComplexType());
        if (c.widestComplexType() != null) {
            widest.value().setTooltip(new Tooltip(c.widestComplexType()));
        }
        return card("Complexity", "bi-speedometer2", null, rows(
                depth,
                row("Max derivation depth", c.maxTypeDerivationDepth()),
                widest,
                row("Avg declarations per type", String.format(Locale.ROOT, "%.1f", c.avgChildrenPerComplexType())),
                row("Abstract types / elements", c.abstractTypes() + " / " + c.abstractElements()),
                row("Substitution heads / members", c.substitutionGroupHeads() + " / " + c.substitutionGroupMembers()),
                row("Anonymous complex / simple", c.anonymousComplexTypes() + " / " + c.anonymousSimpleTypes()),
                row("Mixed content types", c.mixedContentTypes()),
                row("Extensions / restrictions", c.extensions() + " / " + c.restrictions()),
                row("XSD 1.1 features", c.xsd11Features())));
    }

    private static VBox facetsCard(XsdStatistics.ComplexityMetrics c) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("Facets", c.totalFacets()));
        rows.add(row("Enumeration values", c.enumerationValues()));
        for (XsdFacetType type : XsdFacetType.values()) {
            int count = c.facetCounts().getOrDefault(type, 0);
            if (count > 0) {
                rows.add(row(AnalysisSupport.titleCase(type), count));
            }
        }
        return card("Facets", "bi-sliders", null, rows);
    }

    private static TableColumn<XsdSchemaReferenceInfo, String> referenceStatusColumn() {
        TableColumn<XsdSchemaReferenceInfo, String> column = AnalysisSupport.column("Status", XsdSchemaReferenceInfo::getStatusDisplayName, 90);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeIf(cls -> cls.startsWith("fxt-analysis-sev-"));
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                XsdSchemaReferenceInfo ref = getTableRow() != null ? getTableRow().getItem() : null;
                boolean ok = ref == null || ref.resolved();
                setText(item);
                setGraphic(AnalysisSupport.icon(ok ? "bi-check-circle-fill" : "bi-x-circle-fill", 13));
                setGraphicTextGap(5);
                getStyleClass().add(ok ? "fxt-analysis-sev-info" : "fxt-analysis-sev-error");
                setTooltip(ref != null && ref.errorMessage() != null ? new Tooltip(ref.errorMessage())
                        : ref != null && ref.resolvedPath() != null ? new Tooltip(ref.resolvedPath().toString()) : null);
            }
        });
        return column;
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
}
