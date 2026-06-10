package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The PROBLEMS panel below the editor (Figma "Redesign · Unified — Validation",
 * node 42:3): a header bar with severity counters and a collapse toggle, plus
 * one row per {@link ValidationProblem} of the active document
 * ({@link EditorHost#getActiveProblems()}). Clicking a row jumps to its line.
 * The panel hides itself entirely while there are no problems and re-appears
 * automatically when validation reports some.
 */
public class ProblemsPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> items = FXCollections.observableArrayList();
    private final ListView<ValidationProblem> list = new ListView<>(items);
    private final Label errorChip = new Label();
    private final Label warningChip = new Label();
    private final IconifyIcon collapseIcon = new IconifyIcon("bi-chevron-down");
    private boolean collapsed;

    public ProblemsPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-problems-panel");

        Label title = new Label("PROBLEMS");
        title.getStyleClass().add("fxt-problems-title");

        errorChip.getStyleClass().add("fxt-problems-chip-error");
        errorChip.setGraphic(icon("bi-x-circle", 13));
        warningChip.getStyleClass().add("fxt-problems-chip-warning");
        warningChip.setGraphic(icon("bi-exclamation-triangle-fill", 13));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        collapseIcon.setIconSize(13);
        Button collapseButton = new Button(null, collapseIcon);
        collapseButton.setId("problems-panel-collapse");
        collapseButton.getStyleClass().add("fxt-problems-collapse");
        collapseButton.setOnAction(e -> setCollapsed(!collapsed));

        HBox header = new HBox(10, title, errorChip, warningChip, spacer, collapseButton);
        header.getStyleClass().add("fxt-problems-header");
        header.setAlignment(Pos.CENTER_LEFT);

        list.setId("problems-panel-list");
        list.getStyleClass().add("fxt-problems-list");
        list.setPrefHeight(192);
        list.setCellFactory(lv -> new ProblemRowCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.line() > 0) {
                editorHost.goToLine(newV.line());
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(header, list);

        editorHost.getActiveProblems().addListener(
                (ListChangeListener<ValidationProblem>) c -> refresh());
        refresh();
    }

    /**
     * Mirrors the host's problems into the panel: clears the row selection first
     * (JavaFX ListViewBehavior can throw IndexOutOfBounds when items.setAll runs
     * while a row is selected), updates the severity chips and shows/hides the panel.
     */
    private void refresh() {
        var problems = editorHost.getActiveProblems();
        list.getSelectionModel().clearSelection();
        items.setAll(problems);
        long errors = problems.stream().filter(p -> !isWarning(p)).count();
        long warnings = problems.stream().filter(ProblemsPanel::isWarning).count();
        errorChip.setText(String.valueOf(errors));
        errorChip.setVisible(errors > 0);
        errorChip.setManaged(errors > 0);
        warningChip.setText(String.valueOf(warnings));
        warningChip.setVisible(warnings > 0);
        warningChip.setManaged(warnings > 0);
        boolean show = !problems.isEmpty();
        setVisible(show);
        setManaged(show);
    }

    /** @return {@code true} while the panel shows only its header bar. */
    public boolean isCollapsed() {
        return collapsed;
    }

    /** Collapses the panel to its header bar (or expands it back). */
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        list.setVisible(!collapsed);
        list.setManaged(!collapsed);
        collapseIcon.setIconLiteral(collapsed ? "bi-chevron-up" : "bi-chevron-down");
    }

    /** @return the number of problem rows currently shown (for tests/observers). */
    public int getProblemCount() {
        return items.size();
    }

    private static boolean isWarning(ValidationProblem p) {
        return "warning".equalsIgnoreCase(p.severity());
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /** A problem row: severity icon · message (grows, ellipsis) · "file : line" in mono. */
    private final class ProblemRowCell extends ListCell<ValidationProblem> {
        private ProblemRowCell() {
            // Follow the ListView width (ellipsize) instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ValidationProblem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean warning = isWarning(item);
            IconifyIcon severityIcon = icon(warning
                    ? "bi-exclamation-triangle-fill" : "bi-x-circle", 15);
            severityIcon.getStyleClass().add(warning ? "sev-warning" : "sev-error");

            Label message = new Label(item.message());
            message.getStyleClass().add("fxt-problems-msg");
            message.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(message, Priority.ALWAYS);

            String name = editorHost.getActiveDocument()
                    .map(OpenDocument::getDisplayName).orElse("");
            Label location = new Label(item.line() > 0 ? name + " : " + item.line() : name);
            location.getStyleClass().add("fxt-problems-loc");

            HBox row = new HBox(10, severityIcon, message, location);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
        }
    }
}
