package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.ComponentInfo.UsageRef;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKind;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * "Types" sub-tab: every global type, group and attribute group with its base, documentation,
 * usage count and "used in" locations (the old Type Library). Chips filter by kind, "Unused"
 * (never referenced), "Unreachable" (removable) and "Single use"; a text field searches name,
 * base and documentation. Selecting a row reveals the component in the Tree view, selecting a
 * usage reveals the referring node. "Remove unused…" deletes every unreachable component of
 * the analyzed document as one undo step.
 */
final class TypesSection extends VBox {

    static final String ALL = "All";
    static final String UNUSED = "Unused";
    static final String UNREACHABLE = "Unreachable";
    static final String SINGLE_USE = "Single use";

    private final EditorHost editorHost;
    private final Label status = new Label();
    private final StringProperty filter = new SimpleStringProperty(ALL);
    private final FlowPane chips = new FlowPane(6, 6);
    private final TextField search = new TextField();
    private final Label count = new Label();
    private final Button removeUnused = new Button("Remove unused…", AnalysisSupport.icon("bi-trash", 14));
    private final ObservableList<ComponentInfo> components = FXCollections.observableArrayList();
    private final FilteredList<ComponentInfo> filtered = new FilteredList<>(components);
    private final TableView<ComponentInfo> table = new TableView<>();
    private final ListView<UsageRef> usages = new ListView<>();
    private final Label usagesTitle = AnalysisSupport.groupTitle("Used in");
    private final TextArea documentation = new TextArea();
    private SchemaAnalysisData data;
    private OpenDocument document;
    private Runnable afterRemoval = () -> { };

    TypesSection(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        status.setId("analysis-types-status");
        status.getStyleClass().add("fxt-placeholder-text");
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        removeUnused.setId("analysis-types-remove-unused");
        removeUnused.getStyleClass().add("fxt-tool-button");
        removeUnused.setTooltip(new Tooltip("Delete every type, group and attribute group that no global element or attribute reaches (one undo step)"));
        removeUnused.setDisable(true);
        removeUnused.setOnAction(e -> confirmAndRemove());
        HBox toolbar = new HBox(8, AnalysisSupport.reportMenu("Schema Types", status,
                () -> data == null ? null : SchemaAnalysisReport.types(data)), removeUnused, status);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        chips.setId("analysis-types-chips");
        chips.setAlignment(Pos.CENTER_LEFT);
        search.setId("analysis-types-search");
        search.setPromptText("Search types…");
        search.setPrefWidth(220);
        search.textProperty().addListener((obs, o, n) -> applyFilter());
        filter.addListener((obs, o, n) -> applyFilter());
        count.setId("analysis-types-count");
        count.getStyleClass().add("fxt-analysis-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox filterBar = new HBox(8, new Label(null, AnalysisSupport.icon("bi-funnel", 14)), chips, search, spacer, count);
        filterBar.getStyleClass().add("fxt-analysis-filter");
        filterBar.setAlignment(Pos.CENTER_LEFT);

        table.setId("analysis-types-table");
        table.getStyleClass().add("fxt-analysis-table");
        table.getColumns().add(kindColumn());
        table.getColumns().add(AnalysisSupport.column("Name", ComponentInfo::name, 220));
        table.getColumns().add(AnalysisSupport.column("Base", ComponentInfo::baseType, 180));
        table.getColumns().add(AnalysisSupport.column("Documentation", c -> firstLine(c.documentation()), 260));
        table.getColumns().add(usageColumn());
        table.getColumns().add(fileColumn());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(AnalysisSupport.emptyLabel("No global types, groups or attribute groups."));
        SortedList<ComponentInfo> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            showDetails(n);
            if (n != null && editorHost != null) {
                if (!editorHost.revealSchemaNodeByXPath(n.xpath())) {
                    editorHost.revealTypeByName(n.name());
                }
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        usages.setId("analysis-types-usages");
        usages.getStyleClass().add("fxt-analysis-list");
        usages.setPlaceholder(AnalysisSupport.emptyLabel("Select a component to see where it is used."));
        usages.setPrefHeight(150);
        usages.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UsageRef item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                String origin = item.fromImport() ? "  [import]" : "(main)".equals(item.sourceFileName()) ? "" : "  [" + item.sourceFileName() + "]";
                setText(item.referrerDisplay() + origin);
                setGraphic(AnalysisSupport.icon("bi-arrow-return-right", 13));
                setTooltip(new Tooltip(item.referrerXPath()));
            }
        });
        usages.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && editorHost != null) {
                editorHost.revealSchemaNodeByXPath(n.referrerXPath());
            }
        });
        documentation.setId("analysis-types-documentation");
        documentation.setEditable(false);
        documentation.setWrapText(true);
        documentation.setPrefRowCount(4);
        documentation.getStyleClass().add("fxt-analysis-details");
        VBox usageBox = new VBox(6, usagesTitle, usages);
        VBox docBox = new VBox(6, AnalysisSupport.groupTitle("Documentation"), documentation);
        HBox.setHgrow(usageBox, Priority.ALWAYS);
        HBox.setHgrow(docBox, Priority.ALWAYS);
        usages.setMaxWidth(Double.MAX_VALUE);
        documentation.setMaxWidth(Double.MAX_VALUE);
        HBox details = new HBox(12, usageBox, docBox);

        getChildren().addAll(toolbar, filterBar, table, details);
        applyFilter();
    }

    void setData(SchemaAnalysisData data) {
        this.data = data;
        components.setAll(data.components());
        rebuildChips();
        showDetails(null);
        applyFilter();
        status.setText("");
        updateRemoveButton();
    }

    /** The document the shown analysis belongs to (target of "Remove unused…"), and what to run afterwards. */
    void setDocument(OpenDocument document, Runnable afterRemoval) {
        this.document = document;
        this.afterRemoval = afterRemoval != null ? afterRemoval : () -> { };
        updateRemoveButton();
    }

    private void updateRemoveButton() {
        boolean enabled = editorHost != null && document != null && data != null && !data.unreachableComponents().isEmpty();
        removeUnused.setDisable(!enabled);
        int n = data == null ? 0 : data.unreachableComponents().size();
        removeUnused.setText(n > 0 ? "Remove unused (" + n + ")…" : "Remove unused…");
    }

    private void rebuildChips() {
        chips.getChildren().clear();
        chip(ALL, components.size(), "neutral");
        for (ComponentKind kind : List.of(ComponentKind.COMPLEX_TYPE, ComponentKind.SIMPLE_TYPE, ComponentKind.GROUP, ComponentKind.ATTRIBUTE_GROUP)) {
            long n = components.stream().filter(c -> c.kind() == kind).count();
            if (n > 0) {
                chip(kind.getDisplayName(), n, "neutral");
            }
        }
        long unused = components.stream().filter(ComponentInfo::isUnused).count();
        long unreachable = components.stream().filter(ComponentInfo::unreachable).count();
        long single = components.stream().filter(ComponentInfo::isSingleUse).count();
        chip(UNUSED, unused, unused > 0 ? "error" : "ok");
        if (unreachable > unused) {
            chip(UNREACHABLE, unreachable, "warning");
        }
        if (single > 0) {
            chip(SINGLE_USE, single, "suggestion");
        }
    }

    private void chip(String value, long n, String tone) {
        Label chip = AnalysisSupport.chip(value + " " + n, tone);
        AnalysisSupport.toggleChip(chip, filter, value, ALL);
        chips.getChildren().add(chip);
    }

    private void applyFilter() {
        String selected = filter.get();
        String text = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(c -> {
            if (selected != null && !ALL.equals(selected)) {
                boolean match = switch (selected) {
                    case UNUSED -> c.isUnused();
                    case UNREACHABLE -> c.unreachable();
                    case SINGLE_USE -> c.isSingleUse();
                    default -> c.kind().getDisplayName().equals(selected);
                };
                if (!match) {
                    return false;
                }
            }
            if (text.isEmpty()) {
                return true;
            }
            return contains(c.name(), text) || contains(c.baseType(), text) || contains(c.documentation(), text);
        });
        count.setText(AnalysisSupport.countText(filtered.size(), components.size(), "component"));
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String line = text.strip();
        int nl = line.indexOf('\n');
        if (nl >= 0) {
            line = line.substring(0, nl).strip() + " …";
        }
        return line;
    }

    private void showDetails(ComponentInfo component) {
        if (component == null) {
            usages.getItems().clear();
            usagesTitle.setText("USED IN");
            documentation.setText("");
            return;
        }
        usages.getItems().setAll(component.usages());
        usagesTitle.setText("USED IN (" + component.usageCount() + ")");
        documentation.setText(component.documentation().isBlank() ? "(no documentation)" : component.documentation());
    }

    // ---------------------------------------------------------------- columns

    private static TableColumn<ComponentInfo, ComponentKind> kindColumn() {
        TableColumn<ComponentInfo, ComponentKind> column = new TableColumn<>("Kind");
        column.setPrefWidth(130);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().kind()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ComponentKind item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.getDisplayName());
                setGraphic(AnalysisSupport.icon(kindIcon(item), 14));
                setGraphicTextGap(6);
            }
        });
        return column;
    }

    static String kindIcon(ComponentKind kind) {
        return switch (kind) {
            case COMPLEX_TYPE -> "bi-diagram-3";
            case SIMPLE_TYPE -> "bi-type";
            case GROUP -> "bi-collection";
            case ATTRIBUTE_GROUP -> "bi-tags";
            case ELEMENT -> "bi-box";
            case ATTRIBUTE -> "bi-at";
        };
    }

    /** Usage count as a chip: 0 = error, ≤ 3 = warning, else ok; unreachable adds a tooltip. */
    private static TableColumn<ComponentInfo, ComponentInfo> usageColumn() {
        TableColumn<ComponentInfo, ComponentInfo> column = new TableColumn<>("Usages");
        column.setPrefWidth(110);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setComparator((a, b) -> Integer.compare(a.usageCount(), b.usageCount()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ComponentInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                String tone = item.usageCount() == 0 ? "error" : item.usageCount() <= 3 ? "warning" : "ok";
                Label chip = AnalysisSupport.chip(Integer.toString(item.usageCount()), tone);
                if (item.unreachable()) {
                    chip.setTooltip(new Tooltip(item.isUnused() ? "Never referenced"
                            : "Only referenced from components that are themselves unused"));
                }
                setGraphic(chip);
            }
        });
        return column;
    }

    private static TableColumn<ComponentInfo, String> fileColumn() {
        TableColumn<ComponentInfo, String> column = AnalysisSupport.column("File",
                c -> c.fromInclude() ? c.sourceFileName() : "", 110);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item);
                setGraphic(AnalysisSupport.icon("bi-file-earmark-arrow-down", 12));
                setGraphicTextGap(4);
            }
        });
        return column;
    }

    // ---------------------------------------------------------------- remove unused

    private void confirmAndRemove() {
        if (editorHost == null || document == null || data == null) {
            return;
        }
        List<ComponentInfo> unreachable = data.unreachableComponents();
        List<ComponentKey> keys = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int skipped = 0;
        for (ComponentInfo c : unreachable) {
            if (c.fromInclude()) {
                skipped++;
                lines.add("• " + c.kind().getDisplayName() + " '" + c.name() + "' — in " + c.sourceFileName() + ", will be skipped");
            } else {
                keys.add(c.key());
                lines.add("• " + c.kind().getDisplayName() + " '" + c.name() + "'");
            }
        }
        if (keys.isEmpty()) {
            status.setText("Nothing to remove: all unreachable components live in included files.");
            return;
        }
        Alert alert = DialogHelper.createStyledAlert(Alert.AlertType.CONFIRMATION, "Remove unused components",
                "Remove " + AnalysisSupport.plural(keys.size(), "unreachable component") + " from " + document.getDisplayName() + "?",
                "No global element or attribute reaches these types, groups and attribute groups. "
                        + "They are deleted from the schema text as one undo step (Ctrl+Z restores them)."
                        + (skipped > 0 ? "\n" + skipped + " component(s) declared in included files are skipped." : ""));
        TextArea list = new TextArea(String.join("\n", lines));
        list.setEditable(false);
        list.setWrapText(false);
        list.setPrefRowCount(Math.min(12, lines.size() + 1));
        alert.getDialogPane().setExpandableContent(list);
        alert.getDialogPane().setExpanded(true);
        ButtonType remove = new ButtonType("Remove", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(remove, ButtonType.CANCEL);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == remove) {
            remove(keys, skipped);
        }
    }

    /** Runs the removal for {@code keys} and reports the outcome. */
    private void remove(List<ComponentKey> keys, int skippedFromInclude) {
        Optional<EditorHost.ComponentRemoval> outcome = editorHost.removeUnusedSchemaComponents(document, keys);
        if (outcome.isEmpty()) {
            status.setText("Nothing removed: the document could not be parsed, or it uses xs:redefine/xs:override.");
            return;
        }
        EditorHost.ComponentRemoval result = outcome.get();
        StringBuilder text = new StringBuilder("Removed " + AnalysisSupport.plural(result.removed(), "component"));
        int skipped = skippedFromInclude + result.skippedFromInclude().size();
        if (skipped > 0) {
            text.append(" (").append(skipped).append(" skipped: from included files)");
        }
        if (!result.notFound().isEmpty()) {
            text.append(" (").append(result.notFound().size()).append(" not found — re-run the analysis)");
        }
        text.append(" — undo with Ctrl+Z in the editor.");
        status.setText(text.toString());
        Platform.runLater(afterRemoval);
    }

    /** @return the filter currently applied (for tests). */
    String filterForTest() {
        return filter.get();
    }
}
