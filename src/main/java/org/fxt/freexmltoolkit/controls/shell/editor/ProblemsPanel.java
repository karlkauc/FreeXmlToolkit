package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixMenuFactory;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;

/**
 * The PROBLEMS panel below the editor (Figma "Redesign · Unified — Validation",
 * node 42:3): a header bar with severity counters and a collapse toggle, plus
 * one row per {@link ValidationProblem} of the active document
 * ({@link EditorHost#getActiveProblems()}). Clicking a row jumps to its line; right-click
 * (or Ctrl+C) copies a problem's text, which the row may cut off.
 * The panel hides itself entirely while there are no problems and re-appears
 * automatically when validation reports some.
 */
public class ProblemsPanel extends VBox {

    private static final KeyCombination COPY = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> items = FXCollections.observableArrayList();
    @FXML private ListView<ValidationProblem> list;
    @FXML private Label errorChip;
    @FXML private Label warningChip;
    @FXML private IconifyIcon collapseIcon;
    private boolean collapsed;

    /** Loads the layout from {@code /pages/panels/ProblemsPanel.fxml} and binds the list to the host's problems. */
    public ProblemsPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/ProblemsPanel.fxml");

        list.setItems(items);
        list.setCellFactory(lv -> new ProblemRowCell());
        // Ctrl+C / Cmd+C copies the selected problem like the row menu's "Copy Problem"
        list.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (COPY.match(e) && list.getSelectionModel().getSelectedItem() != null) {
                copyToClipboard(describe(list.getSelectionModel().getSelectedItem(), documentName()));
                e.consume();
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.line() > 0) {
                editorHost.goToLine(newV.line(), newV.column());
            }
        });

        editorHost.getActiveProblems().addListener(
                (ListChangeListener<ValidationProblem>) c -> refresh());
        editorHost.problemsRevealRequestsProperty().addListener((obs, oldV, newV) -> {
            setCollapsed(false);
            list.requestFocus();
            if (!items.isEmpty() && list.getSelectionModel().isEmpty()) {
                list.getFocusModel().focus(0);
            }
        });
        refresh();
    }

    @FXML
    public void onToggleCollapsed() {
        setCollapsed(!collapsed);
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

    /**
     * The row's right-click menu: the quick fixes (if any), then Copy Message, Copy Problem
     * (message with severity, source, location, rule and context) and Copy All Problems.
     */
    ContextMenu contextMenu(ValidationProblem item) {
        ContextMenu menu = new ContextMenu();
        if (item.hasFixes()) {
            menu.getItems().addAll(
                    QuickFixMenuFactory.buildQuickFixMenu(item, fix -> editorHost.getQuickFixController().applyFix(item, fix)),
                    new SeparatorMenuItem());
        }
        menu.getItems().addAll(
                menuItem("problems-ctx-copy-message", "Copy Message", "bi-clipboard",
                        () -> copyToClipboard(item.message() == null ? "" : item.message())),
                menuItem("problems-ctx-copy", "Copy Problem", "bi-clipboard-plus",
                        () -> copyToClipboard(describe(item, documentName()))),
                menuItem("problems-ctx-copy-all", "Copy All Problems", "bi-clipboard-data",
                        () -> copyToClipboard(describeAll(items, documentName()))));
        return menu;
    }

    /**
     * One problem as plain text: {@code [Error] XSD: message (file : line:column)}, followed by
     * the Schematron rule and context lines when present.
     */
    static String describe(ValidationProblem item, String documentName) {
        StringBuilder text = new StringBuilder()
                .append('[').append(isWarning(item) ? "Warning" : "Error").append("] ");
        if (item.source() != null && !item.source().isBlank()) {
            text.append(item.source()).append(": ");
        }
        text.append(item.message() == null ? "" : item.message());
        String name = documentName == null ? "" : documentName;
        if (item.line() > 0) {
            text.append(" (").append(name.isEmpty() ? "line " : name + " : ").append(item.line());
            if (item.column() > 0) {
                text.append(':').append(item.column());
            }
            text.append(')');
        } else if (!name.isEmpty()) {
            text.append(" (").append(name).append(')');
        }
        if (item.ruleId() != null && !item.ruleId().isBlank()) {
            text.append("\n    Rule / Test: ").append(item.ruleId());
        }
        if (item.context() != null && !item.context().isBlank()) {
            text.append("\n    Context: ").append(item.context());
        }
        return text.toString();
    }

    /** All problems, one {@link #describe described} problem per line. */
    static String describeAll(List<ValidationProblem> problems, String documentName) {
        return problems.stream().map(p -> describe(p, documentName)).collect(Collectors.joining("\n"));
    }

    private String documentName() {
        return editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse("");
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static MenuItem menuItem(String id, String text, String iconLiteral, Runnable action) {
        MenuItem item = new MenuItem(text, SemanticIcon.bind(icon(iconLiteral, 16), ActionColor.NEUTRAL));
        item.setId(id);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static boolean isWarning(ValidationProblem p) {
        return "warning".equalsIgnoreCase(p.severity());
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * A tooltip with the finding's full message plus — for Schematron problems —
     * the failed rule/test expression and the failing node's XPath context.
     */
    static javafx.scene.control.Tooltip detailTooltip(ValidationProblem item) {
        StringBuilder text = new StringBuilder(item.message() == null ? "" : item.message());
        if (item.ruleId() != null && !item.ruleId().isBlank()) {
            text.append("\n\nRule / Test: ").append(item.ruleId());
        }
        if (item.context() != null && !item.context().isBlank()) {
            text.append("\nContext: ").append(item.context());
        }
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(text.toString());
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(640);
        return tooltip;
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
                setContextMenu(null);
                return;
            }
            boolean warning = isWarning(item);
            IconifyIcon severityIcon = icon(warning
                    ? "bi-exclamation-triangle-fill" : "bi-x-circle", 15);
            severityIcon.getStyleClass().add(warning ? "sev-warning" : "sev-error");
            setContextMenu(contextMenu(item));

            // Source badge (XSD / Schematron / Well-formed / …) so mixed result
            // lists stay attributable at a glance.
            Label source = new Label(item.source());
            source.getStyleClass().add("fxt-problems-source");

            Label message = new Label(item.message());
            message.getStyleClass().add("fxt-problems-msg");
            message.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(message, Priority.ALWAYS);

            String name = documentName();
            Label location = new Label(item.line() > 0 ? name + " : " + item.line() : name);
            location.getStyleClass().add("fxt-problems-loc");

            HBox row = new HBox(10, severityIcon, source, message, location);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
            setTooltip(detailTooltip(item));
        }
    }
}
