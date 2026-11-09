package org.fxt.freexmltoolkit.controls.diff;

import java.util.List;
import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Difference;

/**
 * The list of structural differences below the two panes of the Compare view in XML mode: a summary
 * line and one row per {@link Difference}. Selecting a row reports it (the view moves both panes to
 * its lines).
 */
final class XmlDiffPanel extends VBox {

    private final Label summary = new Label();
    private final TableView<Difference> table = new TableView<>();
    /** Leaves out what XMLUnit rates "similar" (moves, CDATA vs. text, schema locations). */
    private final CheckBox hideSimilar = new CheckBox(I18n.t("XmlDiffPanel.hide-similar"));
    private List<Difference> all = List.of();

    XmlDiffPanel(Consumer<Difference> onSelect) {
        getStyleClass().add("diff-xml-panel");
        setId("diff-xml-panel");
        summary.getStyleClass().add("diff-xml-summary");
        summary.setId("diff-xml-summary");
        summary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summary, Priority.ALWAYS);
        hideSimilar.setId("diff-xml-hide-similar");
        hideSimilar.setTooltip(new Tooltip(I18n.t("XmlDiffPanel.hide-similar-tooltip")));
        hideSimilar.setOnAction(e -> showRows());
        table.setId("diff-xml-table");
        table.setPlaceholder(new Label(I18n.t("XmlDiffPanel.no-differences")));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Difference item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("diff-xml-similar");
                if (!empty && item != null && item.similar()) {
                    getStyleClass().add("diff-xml-similar");
                }
            }
        });
        table.getColumns().add(column("Change", 80, d -> XmlStructuralDiff.label(d.change())));
        table.getColumns().add(column("Node", 150, XmlDiffPanel::nodeText));
        table.getColumns().add(column("Left", 220, Difference::leftValue));
        table.getColumns().add(column("Right", 220, Difference::rightValue));
        table.getColumns().add(column("Location", 260, d -> d.leftXPath().isEmpty() ? d.rightXPath() : d.leftXPath()));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                onSelect.accept(n);
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox header = new HBox(12, summary, hideSimilar);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new javafx.geometry.Insets(0, 10, 0, 0));
        getChildren().addAll(header, table);
    }

    private static TableColumn<Difference, String> column(String title, double width,
                                                          java.util.function.Function<Difference, String> value) {
        TableColumn<Difference, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null || item.length() < 40 ? null : new Tooltip(item));
            }
        });
        return column;
    }

    static String nodeText(Difference d) {
        return switch (d.kind()) {
            case ELEMENT -> "<" + d.name() + ">";
            case ATTRIBUTE -> "@" + d.name();
            case TEXT -> "text";
            case COMMENT -> "comment";
            case PROCESSING_INSTRUCTION -> "<?" + d.name() + "?>";
            case NAMESPACE -> "namespace of <" + d.name() + ">";
            case DOCUMENT -> d.name();
        };
    }

    /** Shows a comparison result; keeps nothing selected. */
    void setResult(XmlStructuralDiff.Result result) {
        summary.getStyleClass().remove("diff-xml-error");
        if (!result.compared()) {
            summary.getStyleClass().add("diff-xml-error");
            StringBuilder text = new StringBuilder("Cannot compare as XML. ");
            if (result.leftError() != null) {
                text.append("Left: ").append(result.leftError()).append(' ');
            }
            if (result.rightError() != null) {
                text.append("Right: ").append(result.rightError());
            }
            summary.setText(text.toString().strip());
            all = List.of();
            table.getItems().clear();
            return;
        }
        List<Difference> differences = result.differences();
        long similar = result.similarCount();
        summary.setText((differences.isEmpty() ? "The documents are equivalent as XML."
                : differences.size() == 1 ? "1 difference" : differences.size() + " differences")
                + (similar > 0 ? " (" + similar + " similar)" : ""));
        all = differences;
        showRows();
    }

    private void showRows() {
        table.getSelectionModel().clearSelection();
        table.getItems().setAll(hideSimilar.isSelected()
                ? all.stream().filter(d -> !d.similar()).toList() : all);
    }

    /** Hides or shows the "similar" differences (tests). */
    void setHideSimilar(boolean hide) {
        hideSimilar.setSelected(hide);
        showRows();
    }

    /** Shows that a comparison is running. */
    void setComputing() {
        summary.getStyleClass().remove("diff-xml-error");
        summary.setText(I18n.t("XmlDiffPanel.comparing"));
    }

    /** Selects the next ({@code delta > 0}) or previous difference, wrapping around. */
    void select(int delta) {
        int size = table.getItems().size();
        if (size == 0) {
            return;
        }
        int index = table.getSelectionModel().getSelectedIndex();
        int next = index < 0 ? (delta > 0 ? 0 : size - 1) : Math.floorMod(index + delta, size);
        table.getSelectionModel().clearAndSelect(next);
        table.scrollTo(next);
    }

    List<Difference> differences() {
        return List.copyOf(table.getItems());
    }

    String summaryText() {
        return summary.getText();
    }
}
