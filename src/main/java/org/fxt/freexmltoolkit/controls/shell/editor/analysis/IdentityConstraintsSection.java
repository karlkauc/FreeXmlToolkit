package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.AnalysisResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.ConstraintType;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.IdentityConstraintInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.ValidationStatus;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * "Identity Constraints" sub-tab: every {@code xs:key}, {@code xs:keyref}, {@code xs:unique}
 * and {@code xs:assert} with its selector/fields and validation status. Clickable count chips
 * filter by kind and status; a details pane shows the selected constraint with links to its
 * parent element and (for a keyref) the referenced key. Selecting a row reveals the constraint
 * in the Tree view.
 */
final class IdentityConstraintsSection extends VBox {

    static final String ALL = "All";

    private final EditorHost editorHost;
    private final FlowPane typeChips = new FlowPane(6, 6);
    private final FlowPane statusChips = new FlowPane(6, 6);
    private final StringProperty typeFilter = new SimpleStringProperty(ALL);
    private final StringProperty statusFilter = new SimpleStringProperty(ALL);
    private final Label count = new Label();
    private final ObservableList<IdentityConstraintInfo> constraints = FXCollections.observableArrayList();
    private final FilteredList<IdentityConstraintInfo> filtered = new FilteredList<>(constraints);
    private final TableView<IdentityConstraintInfo> table = new TableView<>();
    private final VBox details = new VBox(4);

    IdentityConstraintsSection(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        typeChips.setId("analysis-constraints-type-chips");
        typeChips.setAlignment(Pos.CENTER_LEFT);
        statusChips.setId("analysis-constraints-status-chips");
        statusChips.setAlignment(Pos.CENTER_LEFT);
        count.setId("analysis-constraints-count");
        count.getStyleClass().add("fxt-analysis-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label divider = new Label("·");
        divider.getStyleClass().add("fxt-analysis-key");
        HBox header = new HBox(10, typeChips, divider, statusChips, spacer, count);
        header.setAlignment(Pos.CENTER_LEFT);
        typeFilter.addListener((obs, o, n) -> applyFilter());
        statusFilter.addListener((obs, o, n) -> applyFilter());

        table.setId("analysis-constraints-table");
        table.getStyleClass().add("fxt-analysis-table");
        table.getColumns().add(typeColumn());
        table.getColumns().add(AnalysisSupport.column("Name", IdentityConstraintInfo::name, 170));
        table.getColumns().add(AnalysisSupport.column("Parent element", IdentityConstraintInfo::parentElementName, 150));
        table.getColumns().add(monoColumn("Selector", IdentityConstraintInfo::selectorXPath, 220));
        table.getColumns().add(monoColumn("Fields",
                i -> i.fieldXPaths() == null ? "" : String.join(", ", i.fieldXPaths()), 170));
        table.getColumns().add(monoColumn("Refers to / test",
                i -> i.isAssert() ? i.testExpression() : i.referTo(), 170));
        table.getColumns().add(statusColumn());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label empty = AnalysisSupport.emptyLabel("The schema declares no identity constraints or assertions.");
        empty.setGraphic(AnalysisSupport.icon("bi-key", 28));
        empty.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        empty.setGraphicTextGap(8);
        table.setPlaceholder(empty);
        SortedList<IdentityConstraintInfo> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            showDetails(n);
            if (n != null && n.sourceNode() != null && editorHost != null) {
                editorHost.revealSchemaNodeByXPath(n.sourceNode().getXPath());
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        details.getStyleClass().add("fxt-analysis-details");
        details.setMinHeight(90);

        getChildren().addAll(header, table, details);
        applyFilter();
        showDetails(null);
    }

    void setData(SchemaAnalysisData data) {
        AnalysisResult r = data.constraints();
        typeFilter.set(ALL);
        statusFilter.set(ALL);

        typeChips.getChildren().clear();
        addTypeChip(ConstraintType.KEY, r.keys().size(), "key");
        addTypeChip(ConstraintType.KEYREF, r.keyRefs().size(), "keyref");
        addTypeChip(ConstraintType.UNIQUE, r.uniques().size(), "unique");
        addTypeChip(ConstraintType.ASSERT, r.asserts().size(), "assert");
        if (typeChips.getChildren().isEmpty()) {
            typeChips.getChildren().add(AnalysisSupport.chip("No constraints", "neutral"));
        }

        statusChips.getChildren().clear();
        if (r.errorCount() > 0) {
            addStatusChip(ValidationStatus.ERROR, r.errorCount(), "error");
        }
        if (r.warningCount() > 0) {
            addStatusChip(ValidationStatus.WARNING, r.warningCount(), "warning");
        }
        if (r.errorCount() == 0 && r.warningCount() == 0 && r.totalCount() > 0) {
            Label ok = AnalysisSupport.chip("All valid", "ok");
            ok.setGraphic(AnalysisSupport.statusIcon(ValidationStatus.VALID, 12));
            ok.setGraphicTextGap(5);
            statusChips.getChildren().add(ok);
        }

        constraints.setAll(r.getAllConstraints());
        showDetails(null);
        applyFilter();
    }

    private void addTypeChip(ConstraintType type, int n, String singular) {
        if (n <= 0) {
            return;
        }
        Label chip = AnalysisSupport.chip(AnalysisSupport.plural(n, singular), "info");
        chip.setGraphic(AnalysisSupport.icon(AnalysisSupport.constraintIcon(type), 12));
        chip.setGraphicTextGap(5);
        AnalysisSupport.toggleChip(chip, typeFilter, type.name(), ALL);
        typeChips.getChildren().add(chip);
    }

    private void addStatusChip(ValidationStatus status, int n, String tone) {
        Label chip = AnalysisSupport.chip(AnalysisSupport.plural(n, tone), tone);
        chip.setGraphic(AnalysisSupport.statusIcon(status, 12));
        chip.setGraphicTextGap(5);
        chip.getStyleClass().add("fxt-analysis-sev-" + tone);
        AnalysisSupport.toggleChip(chip, statusFilter, status.name(), ALL);
        statusChips.getChildren().add(chip);
    }

    private void applyFilter() {
        String type = typeFilter.get();
        String status = statusFilter.get();
        filtered.setPredicate(c -> (ALL.equals(type) || c.type().name().equals(type))
                && (ALL.equals(status) || c.status().name().equals(status)));
        count.setText(AnalysisSupport.countText(filtered.size(), constraints.size(), "constraint"));
    }

    // ---------------------------------------------------------------- columns

    private static TableColumn<IdentityConstraintInfo, ConstraintType> typeColumn() {
        TableColumn<IdentityConstraintInfo, ConstraintType> column = new TableColumn<>("Type");
        column.setPrefWidth(95);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().type()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ConstraintType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(typeName(item));
                setGraphic(AnalysisSupport.icon(AnalysisSupport.constraintIcon(item), 14));
                setGraphicTextGap(6);
            }
        });
        return column;
    }

    private static TableColumn<IdentityConstraintInfo, ValidationStatus> statusColumn() {
        TableColumn<IdentityConstraintInfo, ValidationStatus> column = new TableColumn<>("Status");
        column.setPrefWidth(110);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ValidationStatus item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeIf(c -> c.startsWith("fxt-analysis-sev-"));
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                setText(AnalysisSupport.titleCase(item));
                setGraphic(AnalysisSupport.statusIcon(item, 14));
                setGraphicTextGap(6);
                getStyleClass().add("fxt-analysis-sev-" + statusTone(item));
                IdentityConstraintInfo info = getTableRow() == null ? null : getTableRow().getItem();
                String message = info == null ? null : info.statusMessage();
                setTooltip(message == null || message.isBlank() ? null : new Tooltip(message));
            }
        });
        return column;
    }

    /** A monospace text column whose full value is available as a tooltip. */
    private static TableColumn<IdentityConstraintInfo, String> monoColumn(
            String title, Function<IdentityConstraintInfo, String> value, double prefWidth) {
        TableColumn<IdentityConstraintInfo, String> column = AnalysisSupport.column(title, value, prefWidth);
        column.getStyleClass().add("fxt-analysis-cell-mono");
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                setTooltip(new Tooltip(item));
            }
        });
        return column;
    }

    // ---------------------------------------------------------------- details

    private void showDetails(IdentityConstraintInfo info) {
        details.getChildren().clear();
        if (info == null) {
            details.getChildren().add(AnalysisSupport.emptyLabel(
                    "Select a constraint to see its selector, fields and validation message."));
            return;
        }
        Label title = new Label(typeName(info.type()) + " " + AnalysisSupport.nullSafe(info.name()),
                AnalysisSupport.icon(AnalysisSupport.constraintIcon(info.type()), 14));
        title.setGraphicTextGap(6);
        title.getStyleClass().add("fxt-analysis-value");
        details.getChildren().add(title);

        String statusWord = AnalysisSupport.titleCase(info.status());
        String message = info.statusMessage() == null ? "" : info.statusMessage().trim();
        boolean redundant = message.isEmpty() || message.equalsIgnoreCase(statusWord);
        Label status = new Label(redundant ? statusWord : statusWord + " – " + message,
                AnalysisSupport.statusIcon(info.status(), 13));
        status.setGraphicTextGap(6);
        status.setWrapText(true);
        status.getStyleClass().addAll("fxt-analysis-key", "fxt-analysis-sev-" + statusTone(info.status()));
        details.getChildren().add(status);

        if (info.parentElementName() != null && !info.parentElementName().isBlank()) {
            Hyperlink parent = new Hyperlink(info.parentElementName());
            XsdNode parentNode = info.sourceNode() == null ? null : info.sourceNode().getParent();
            parent.setOnAction(e -> {
                if (editorHost != null && parentNode != null) {
                    editorHost.revealSchemaNodeByXPath(parentNode.getXPath());
                }
            });
            details.getChildren().add(detailRow("Parent element", parent));
        }
        if (info.isAssert()) {
            details.getChildren().add(detailRow("Test", mono(info.testExpression())));
        } else {
            details.getChildren().add(detailRow("Selector", mono(info.selectorXPath())));
            List<String> fields = info.fieldXPaths() == null ? List.of() : info.fieldXPaths();
            for (int i = 0; i < fields.size(); i++) {
                details.getChildren().add(detailRow(i == 0 ? "Fields" : "", mono(fields.get(i))));
            }
            if (info.type() == ConstraintType.KEYREF && info.referTo() != null && !info.referTo().isBlank()) {
                IdentityConstraintInfo target = constraints.stream()
                        .filter(c -> c.type() != ConstraintType.KEYREF && c.type() != ConstraintType.ASSERT
                                && info.referTo().equals(c.name()))
                        .findFirst().orElse(null);
                if (target != null) {
                    Hyperlink link = new Hyperlink(info.referTo());
                    link.setTooltip(new Tooltip("Select the referenced " + typeName(target.type()).toLowerCase(Locale.ROOT)));
                    link.setOnAction(e -> {
                        typeFilter.set(ALL);
                        statusFilter.set(ALL);
                        table.getSelectionModel().select(target);
                        table.scrollTo(target);
                    });
                    details.getChildren().add(detailRow("Refers to", link));
                } else {
                    Label missing = mono(info.referTo());
                    missing.getStyleClass().add("fxt-analysis-sev-error");
                    details.getChildren().add(detailRow("Refers to", missing));
                }
            }
        }
    }

    private static HBox detailRow(String key, Node value) {
        Label k = new Label(key);
        k.getStyleClass().add("fxt-analysis-key");
        k.setMinWidth(105);
        HBox row = new HBox(8, k, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label mono(String text) {
        Label label = new Label(AnalysisSupport.nullSafe(text));
        label.getStyleClass().add("fxt-analysis-location");
        label.setWrapText(true);
        return label;
    }

    private static String typeName(ConstraintType type) {
        return switch (type) {
            case KEY -> "Key";
            case KEYREF -> "KeyRef";
            case UNIQUE -> "Unique";
            case ASSERT -> "Assert";
        };
    }

    /** Maps the analyzer status onto the {@code fxt-analysis-sev-*} tone names. */
    private static String statusTone(ValidationStatus status) {
        return switch (status) {
            case VALID -> "ok";
            case WARNING -> "warning";
            case ERROR -> "error";
        };
    }
}
