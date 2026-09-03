package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
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
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.ConstraintType;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer.IdentityConstraintInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.XPathSource;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdXPathValidator.XPathValidationIssue;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * "XPath Validation" sub-tab: one row per XPath expression used by the schema's identity
 * constraints and assertions (selector, each field, each assert test), joined with the
 * validator's findings — expressions without a finding are listed as valid. Clickable status
 * chips filter the table; a details pane shows the selected expression. Selecting a row reveals
 * the owning constraint in the Tree view.
 */
final class XPathSection extends VBox {

    static final String ALL = "All";
    private static final String STATIC_NOTE =
            "Expressions are checked statically against the schema; evaluation against a sample XML is not part of this report.";

    /** Outcome of one expression: {@code VALID} or the most severe finding's severity. */
    enum Status {
        VALID, ERROR, WARNING, INFO
    }

    /**
     * One expression of the report.
     *
     * @param constraint the owning constraint ({@code null} for a finding that matched no expression)
     * @param constraintName the owning constraint's name (for display and matching)
     * @param source where the expression comes from (selector / field / assert test)
     * @param xpath the expression text
     * @param issues the validator's findings for it (empty when valid)
     * @param sourceNode the schema node to reveal
     */
    record Row(IdentityConstraintInfo constraint, String constraintName, XPathSource source, String xpath,
               List<XPathValidationIssue> issues, XsdNode sourceNode) {

        Status status() {
            Status worst = Status.VALID;
            for (XPathValidationIssue issue : issues) {
                Status s = switch (issue.severity()) {
                    case ERROR -> Status.ERROR;
                    case WARNING -> Status.WARNING;
                    case INFO -> Status.INFO;
                };
                // ERROR < WARNING < INFO in declaration order; VALID only without findings.
                if (worst == Status.VALID || s.ordinal() < worst.ordinal()) {
                    worst = s;
                }
            }
            return worst;
        }

        /** The findings' messages joined; empty for a valid expression (the Status column says so). */
        String message() {
            if (issues.isEmpty()) {
                return "";
            }
            Set<String> texts = new LinkedHashSet<>();
            for (XPathValidationIssue issue : issues) {
                if (issue.message() != null && !issue.message().isBlank()) {
                    texts.add(issue.message());
                }
            }
            return String.join("; ", texts);
        }

        ConstraintType constraintType() {
            if (constraint != null) {
                return constraint.type();
            }
            return switch (source) {
                case KEY_SELECTOR, KEY_FIELD -> ConstraintType.KEY;
                case KEYREF_SELECTOR, KEYREF_FIELD -> ConstraintType.KEYREF;
                case UNIQUE_SELECTOR, UNIQUE_FIELD -> ConstraintType.UNIQUE;
                case ASSERT_TEST -> ConstraintType.ASSERT;
            };
        }
    }

    private final EditorHost editorHost;
    private final FlowPane chips = new FlowPane(6, 6);
    private final StringProperty statusFilter = new SimpleStringProperty(ALL);
    private final Label count = new Label();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> filtered = new FilteredList<>(rows);
    private final TableView<Row> table = new TableView<>();
    private final VBox details = new VBox(4);

    XPathSection(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        chips.setId("analysis-xpath-chips");
        chips.setAlignment(Pos.CENTER_LEFT);
        count.setId("analysis-xpath-count");
        count.getStyleClass().add("fxt-analysis-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, chips, spacer, count);
        header.setAlignment(Pos.CENTER_LEFT);
        statusFilter.addListener((obs, o, n) -> applyFilter());

        table.setId("analysis-xpath-table");
        table.getStyleClass().add("fxt-analysis-table");
        table.getColumns().add(statusColumn());
        table.getColumns().add(constraintColumn());
        table.getColumns().add(AnalysisSupport.column("Source", r -> sourceLabel(r.source()), 90));
        table.getColumns().add(xpathColumn());
        table.getColumns().add(AnalysisSupport.column("Message", Row::message, -1));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label empty = AnalysisSupport.emptyLabel("The schema declares no constraint XPath expressions.");
        empty.setGraphic(AnalysisSupport.icon("bi-check2-circle", 28));
        empty.setContentDisplay(ContentDisplay.TOP);
        empty.setGraphicTextGap(8);
        table.setPlaceholder(empty);
        SortedList<Row> sorted = new SortedList<>(filtered);
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
        statusFilter.set(ALL);
        rows.setAll(buildRows(data));

        Map<Status, Long> counts = new java.util.EnumMap<>(Status.class);
        for (Row row : rows) {
            counts.merge(row.status(), 1L, Long::sum);
        }
        chips.getChildren().clear();
        addChip(Status.VALID, counts.getOrDefault(Status.VALID, 0L), "valid", "ok");
        addChip(Status.ERROR, counts.getOrDefault(Status.ERROR, 0L), "error", "error");
        addChip(Status.WARNING, counts.getOrDefault(Status.WARNING, 0L), "warning", "warning");
        addChip(Status.INFO, counts.getOrDefault(Status.INFO, 0L), "note", "info");
        if (chips.getChildren().isEmpty()) {
            chips.getChildren().add(AnalysisSupport.chip("No expressions", "neutral"));
        }
        showDetails(null);
        applyFilter();
    }

    /**
     * One row per expression of every constraint, each joined with the findings that name the
     * same constraint, source and expression; findings that match no expression (e.g. an empty
     * assert test) become rows of their own so nothing is lost.
     */
    static List<Row> buildRows(SchemaAnalysisData data) {
        Map<String, List<XPathValidationIssue>> byKey = new HashMap<>();
        for (XPathValidationIssue issue : data.xpath().issues()) {
            byKey.computeIfAbsent(key(issue.constraintName(), issue.source(), issue.xpath()), k -> new ArrayList<>())
                    .add(issue);
        }
        List<Row> result = new ArrayList<>();
        for (IdentityConstraintInfo c : data.constraints().getAllConstraints()) {
            if (c.isAssert()) {
                result.add(row(c, XPathSource.ASSERT_TEST, c.testExpression(), byKey));
                continue;
            }
            XPathSource selector = switch (c.type()) {
                case KEYREF -> XPathSource.KEYREF_SELECTOR;
                case UNIQUE -> XPathSource.UNIQUE_SELECTOR;
                default -> XPathSource.KEY_SELECTOR;
            };
            XPathSource field = switch (c.type()) {
                case KEYREF -> XPathSource.KEYREF_FIELD;
                case UNIQUE -> XPathSource.UNIQUE_FIELD;
                default -> XPathSource.KEY_FIELD;
            };
            result.add(row(c, selector, c.selectorXPath(), byKey));
            if (c.fieldXPaths() != null) {
                for (String f : c.fieldXPaths()) {
                    result.add(row(c, field, f, byKey));
                }
            }
        }
        // Findings that did not match any expression above.
        for (List<XPathValidationIssue> orphans : byKey.values()) {
            for (XPathValidationIssue issue : orphans) {
                result.add(new Row(null, issue.constraintName(), issue.source(), issue.xpath(),
                        List.of(issue), issue.sourceNode()));
            }
        }
        return result;
    }

    private static Row row(IdentityConstraintInfo c, XPathSource source, String xpath,
                           Map<String, List<XPathValidationIssue>> byKey) {
        List<XPathValidationIssue> issues = byKey.remove(key(c.name(), source, xpath));
        return new Row(c, c.name(), source, xpath, issues == null ? List.of() : List.copyOf(issues), c.sourceNode());
    }

    private static String key(String constraintName, XPathSource source, String xpath) {
        return AnalysisSupport.nullSafe(constraintName) + " " + source + " "
                + AnalysisSupport.nullSafe(xpath).trim();
    }

    private void addChip(Status status, long n, String singular, String tone) {
        if (n <= 0) {
            return;
        }
        Label chip = AnalysisSupport.chip(status == Status.VALID
                ? n + " valid" : AnalysisSupport.plural(n, singular), tone);
        chip.setGraphic(statusIcon(status, 12));
        chip.setGraphicTextGap(5);
        chip.getStyleClass().add("fxt-analysis-sev-" + tone);
        AnalysisSupport.toggleChip(chip, statusFilter, status.name(), ALL);
        chips.getChildren().add(chip);
    }

    private void applyFilter() {
        String status = statusFilter.get();
        filtered.setPredicate(r -> ALL.equals(status) || r.status().name().equals(status));
        count.setText(AnalysisSupport.countText(filtered.size(), rows.size(), "expression"));
    }

    // ---------------------------------------------------------------- columns

    private static TableColumn<Row, Status> statusColumn() {
        TableColumn<Row, Status> column = new TableColumn<>("Status");
        column.setPrefWidth(105);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Status item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeIf(c -> c.startsWith("fxt-analysis-sev-"));
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(AnalysisSupport.titleCase(item));
                setGraphic(statusIcon(item, 14));
                setGraphicTextGap(6);
                getStyleClass().add("fxt-analysis-sev-" + tone(item));
            }
        });
        return column;
    }

    private static TableColumn<Row, Row> constraintColumn() {
        TableColumn<Row, Row> column = new TableColumn<>("Constraint");
        column.setPrefWidth(190);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setComparator((a, b) -> AnalysisSupport.nullSafe(a.constraintName())
                .compareToIgnoreCase(AnalysisSupport.nullSafe(b.constraintName())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Row item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(AnalysisSupport.nullSafe(item.constraintName()));
                setGraphic(AnalysisSupport.icon(AnalysisSupport.constraintIcon(item.constraintType()), 14));
                setGraphicTextGap(6);
            }
        });
        return column;
    }

    private static TableColumn<Row, String> xpathColumn() {
        TableColumn<Row, String> column = AnalysisSupport.column("XPath", Row::xpath, 260);
        column.getStyleClass().add("fxt-analysis-cell-mono");
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(empty ? null : "(empty)");
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

    private void showDetails(Row row) {
        details.getChildren().clear();
        if (row == null) {
            details.getChildren().add(AnalysisSupport.emptyLabel("Select an expression to see its findings."));
            Label note = AnalysisSupport.emptyLabel(STATIC_NOTE);
            note.setWrapText(true);
            details.getChildren().add(note);
            return;
        }
        Label title = new Label(AnalysisSupport.nullSafe(row.constraintName()) + " · " + sourceLabel(row.source()),
                AnalysisSupport.icon(AnalysisSupport.constraintIcon(row.constraintType()), 14));
        title.setGraphicTextGap(6);
        title.getStyleClass().add("fxt-analysis-value");
        details.getChildren().add(title);

        Label status = new Label(AnalysisSupport.titleCase(row.status())
                + (row.issues().isEmpty() ? "" : " – " + row.message()), statusIcon(row.status(), 13));
        status.setGraphicTextGap(6);
        status.setWrapText(true);
        status.getStyleClass().addAll("fxt-analysis-key", "fxt-analysis-sev-" + tone(row.status()));
        details.getChildren().add(status);

        Label xpath = new Label(row.xpath() == null || row.xpath().isBlank() ? "(empty)" : row.xpath());
        xpath.setWrapText(true);
        xpath.getStyleClass().add("fxt-analysis-location");
        details.getChildren().add(detailRow("XPath", xpath));

        String parent = row.constraint() == null ? null : row.constraint().parentElementName();
        if (parent != null && !parent.isBlank()) {
            Hyperlink link = new Hyperlink(parent);
            XsdNode parentNode = row.sourceNode() == null ? null : row.sourceNode().getParent();
            link.setOnAction(e -> {
                if (editorHost != null && parentNode != null) {
                    editorHost.revealSchemaNodeByXPath(parentNode.getXPath());
                }
            });
            details.getChildren().add(detailRow("Parent element", link));
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

    // ---------------------------------------------------------------- helpers

    /** "Selector", "Field" or "Test" — the constraint kind is already shown in the Constraint column. */
    static String sourceLabel(XPathSource source) {
        if (source == null) {
            return "";
        }
        return switch (source) {
            case KEY_SELECTOR, KEYREF_SELECTOR, UNIQUE_SELECTOR -> "Selector";
            case KEY_FIELD, KEYREF_FIELD, UNIQUE_FIELD -> "Field";
            case ASSERT_TEST -> "Test";
        };
    }

    private static Node statusIcon(Status status, int size) {
        String literal = switch (status) {
            case VALID -> "bi-check-circle-fill";
            case ERROR -> "bi-x-circle-fill";
            case WARNING -> "bi-exclamation-triangle-fill";
            case INFO -> "bi-info-circle-fill";
        };
        var icon = AnalysisSupport.icon(literal, size);
        icon.getStyleClass().add("fxt-analysis-sev-icon");
        return icon;
    }

    private static String tone(Status status) {
        return switch (status) {
            case VALID -> "ok";
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }
}
