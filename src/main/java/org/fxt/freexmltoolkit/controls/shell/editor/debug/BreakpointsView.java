package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;

/**
 * Lists the session's breakpoints with Remove / Clear all; double-click jumps to the line,
 * {@code Delete} removes the selected one. Shown as the "Breakpoints" tab of the DEBUG pane;
 * {@link #setOnCountChanged} feeds the tab's badge.
 */
public class BreakpointsView extends VBox {

    private final ListView<Breakpoint> list = new ListView<>();
    private final DebugSession session;
    private java.util.List<javafx.scene.control.CheckMenuItem> breakOnItems = java.util.List.of();
    private IntConsumer onJumpToLine = line -> { };
    private java.util.function.Consumer<Breakpoint> onJumpToBreakpoint = bp -> { };
    private Runnable onChanged = () -> { };
    private Runnable onCountChanged = () -> { };
    private org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Prompt propertiesPrompt =
            org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.DEFAULT;

    public BreakpointsView(DebugSession session) {
        this.session = session;
        getStyleClass().add("fxt-debug-panel");

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            private final javafx.scene.control.CheckBox enabled = new javafx.scene.control.CheckBox();

            {
                enabled.setTooltip(new javafx.scene.control.Tooltip("Enabled — untick to keep the breakpoint without pausing"));
                enabled.setOnAction(e -> {
                    Breakpoint bp = getItem();
                    if (bp != null) {
                        setEnabled(bp, enabled.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(Breakpoint bp, boolean empty) {
                super.updateItem(bp, empty);
                getStyleClass().removeAll("fxt-bp-disabled", "fxt-bp-unreached");
                if (empty || bp == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                enabled.setSelected(bp.enabled());
                setGraphic(enabled);
                if (!bp.enabled()) {
                    getStyleClass().add("fxt-bp-disabled");
                }
                int hits = session.hitCount(bp);
                boolean unreached = session.notReachedInLastRun(bp.filePath(), bp.lineNumber());
                if (unreached) {
                    getStyleClass().add("fxt-bp-unreached");
                }
                setText((unreached ? "⚠ " : "") + "line " + bp.lineNumber()
                        + (bp.filePath().isEmpty() ? "" : "  " + shortName(bp.filePath()))
                        + (bp.hasCondition() ? "   if  " + bp.condition() : "")
                        + (bp.hit().isSet() ? "   ·  " + bp.hit().describe() : "")
                        + (hits > 0 ? "   ·  " + hits + (hits == 1 ? " hit" : " hits") : "")
                        + (unreached ? "   ·  not reached in the last run" : ""));
                setTooltip(unreached ? new javafx.scene.control.Tooltip(
                        "The last run never reached this line: it has no instruction Saxon traces "
                                + "(e.g. a closing tag or a comment), or the code there did not run.") : null);
            }
        });
        list.setPlaceholder(new Label("No breakpoints."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Breakpoint bp = list.getSelectionModel().getSelectedItem();
                if (bp != null) {
                    onJumpToLine.accept(bp.lineNumber());
                    onJumpToBreakpoint.accept(bp);
                }
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
                e.consume();
            }
        });

        Button remove = DebugToolbar.action("Remove", "bi-dash-lg",
                "Remove the selected breakpoint (Delete)");
        remove.setOnAction(e -> removeSelected());
        Button condition = DebugToolbar.action("Edit…", "bi-sliders",
                "Condition and hit count of the selected breakpoint");
        condition.setOnAction(e -> editSelected());
        Button clear = DebugToolbar.action("Clear all", "bi-trash",
                "Remove every breakpoint of the session");
        clear.setOnAction(e -> {
            session.clearBreakpoints();
            refresh();
            onChanged.run();
        });

        javafx.scene.control.MenuButton breakOn = breakOnMenu();
        HBox toolbar = new HBox(remove, condition, clear, breakOn);
        toolbar.getStyleClass().add("fxt-debug-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(toolbar, list);
        refresh();
    }

    /** Preference keys of the "Break on" options (defaults: errors and terminate on). */
    static final String BREAK_ON_ERRORS_KEY = "debug.breakOn.errors";
    static final String BREAK_ON_TERMINATE_KEY = "debug.breakOn.terminate";
    static final String BREAK_ON_MESSAGES_KEY = "debug.breakOn.messages";

    /**
     * The toolbar's "Break on" menu: pause on dynamic errors (with the state at the failing
     * instruction), before {@code xsl:message terminate="yes"}, or before every
     * {@code xsl:message}. The choices are saved and applied to every session.
     */
    private javafx.scene.control.MenuButton breakOnMenu() {
        javafx.scene.control.CheckMenuItem errors = breakOnItem("Dynamic errors", BREAK_ON_ERRORS_KEY, true,
                session::setBreakOnErrors);
        javafx.scene.control.CheckMenuItem terminate = breakOnItem("xsl:message terminate=\"yes\"",
                BREAK_ON_TERMINATE_KEY, true, session::setBreakOnTerminate);
        javafx.scene.control.CheckMenuItem messages = breakOnItem("Every xsl:message", BREAK_ON_MESSAGES_KEY, false,
                session::setBreakOnMessages);
        org.fxt.freexmltoolkit.controls.icons.IconifyIcon icon =
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-exclamation-octagon");
        icon.setIconSize(14);
        javafx.scene.control.MenuButton menu = new javafx.scene.control.MenuButton("Break on", icon,
                errors, terminate, messages);
        menu.getStyleClass().addAll("fxt-sp-action", "fxt-output-action");
        menu.setTooltip(new javafx.scene.control.Tooltip(
                "Pause on errors or xsl:message, independent of line breakpoints (Mute silences these too)"));
        breakOnItems = java.util.List.of(errors, terminate, messages);
        return menu;
    }

    private static javafx.scene.control.CheckMenuItem breakOnItem(String text, String key, boolean defaultOn,
                                                                    java.util.function.Consumer<Boolean> apply) {
        boolean on = readFlag(key, defaultOn);
        apply.accept(on);
        javafx.scene.control.CheckMenuItem item = new javafx.scene.control.CheckMenuItem(text);
        item.setSelected(on);
        item.selectedProperty().addListener((obs, old, value) -> {
            apply.accept(value);
            try {
                org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                        .set(key, Boolean.toString(value));
            } catch (Throwable ignored) {
                // properties service unavailable — the choice holds for this session
            }
        });
        return item;
    }

    private static boolean readFlag(String key, boolean defaultValue) {
        try {
            String value = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(key);
            return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.strip());
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    /** The "Break on" items in menu order: errors, terminate, every message (tests). */
    java.util.List<javafx.scene.control.CheckMenuItem> breakOnItems() {
        return breakOnItems;
    }

    /**
     * Re-reads the breakpoints (and their hit counts) from the session into the list, keeping
     * the selected file:line selected.
     */
    public final void refresh() {
        Breakpoint selected = list.getSelectionModel().getSelectedItem();
        List<Breakpoint> sorted = new ArrayList<>(session.getBreakpoints());
        sorted.sort(java.util.Comparator.comparing(Breakpoint::filePath)
                .thenComparingInt(Breakpoint::lineNumber));
        list.getSelectionModel().clearSelection();
        list.getItems().setAll(sorted);
        if (selected != null) {
            for (int i = 0; i < sorted.size(); i++) {
                Breakpoint bp = sorted.get(i);
                if (bp.filePath().equals(selected.filePath()) && bp.lineNumber() == selected.lineNumber()) {
                    list.getSelectionModel().select(i);
                    break;
                }
            }
        }
        list.refresh(); // hit counts changed although the breakpoints are equal
        onCountChanged.run();
    }

    /** Enables or disables {@code bp} (row checkbox) and tells the host (gutter, persistence). */
    public void setEnabled(Breakpoint bp, boolean enabled) {
        session.setEnabled(bp.filePath(), bp.lineNumber(), enabled);
        refresh();
        onChanged.run();
    }

    /** @return whether the row at {@code index} is enabled (tests). */
    public boolean isEnabledAt(int index) {
        return list.getItems().get(index).enabled();
    }

    /** Removes the selected breakpoint from the session, if any (Remove button, Delete key). */
    public void removeSelected() {
        Breakpoint bp = list.getSelectionModel().getSelectedItem();
        if (bp != null) {
            session.removeBreakpoint(bp.filePath(), bp.lineNumber());
            refresh();
            onChanged.run();
        }
    }

    /** Replaces the properties prompt (tests). */
    public void setPropertiesPrompt(org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Prompt prompt) {
        this.propertiesPrompt = prompt == null
                ? org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.DEFAULT : prompt;
    }

    /** Edits condition and hit count of the selected breakpoint (toolbar Edit…). */
    public void editSelected() {
        Breakpoint bp = list.getSelectionModel().getSelectedItem();
        if (bp == null) {
            return;
        }
        String where = (bp.filePath().isEmpty() ? "" : shortName(bp.filePath()) + ", ") + "line " + bp.lineNumber();
        propertiesPrompt.ask(getScene() == null ? null : getScene().getWindow(), where,
                        new org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Settings(bp.condition(), bp.hit()),
                        session.getState() == DebugSession.State.IDLE ? -1 : session.hitCount(bp))
                .ifPresent(settings -> {
                    session.updateBreakpoint(bp.filePath(), bp.lineNumber(),
                            b -> b.withCondition(settings.condition()).withHit(settings.hit()));
                    refresh();
                    onChanged.run();
                });
    }

    /** @return whether the row at {@code index} is marked "not reached in the last run" (tests). */
    public boolean isUnreachedAt(int index) {
        Breakpoint bp = list.getItems().get(index);
        return session.notReachedInLastRun(bp.filePath(), bp.lineNumber());
    }

    /** @return the row text at {@code index} as shown (tests). */
    public String getRowText(int index) {
        Breakpoint bp = list.getItems().get(index);
        int hits = session.hitCount(bp);
        return bp.condition() + "|" + bp.hit().format() + "|" + hits;
    }

    /** @return the condition of the row at {@code index} (tests). */
    public String getConditionAt(int index) {
        return list.getItems().get(index).condition();
    }

    /** Selects the breakpoint at {@code index} (tests, keyboard flows). */
    public void select(int index) {
        list.getSelectionModel().clearAndSelect(index);
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }

    /** Breakpoint jump handler: receives the breakpoint so its own module can be opened. */
    public void setOnJumpToBreakpoint(java.util.function.Consumer<Breakpoint> handler) {
        this.onJumpToBreakpoint = handler == null ? bp -> { } : handler;
    }

    /** Invoked after the user removes/clears breakpoints (so the gutter can refresh). */
    public void setOnChanged(Runnable handler) {
        this.onChanged = handler == null ? () -> { } : handler;
    }

    /** Invoked after every change of the row count (tab badge). */
    public void setOnCountChanged(Runnable handler) {
        this.onCountChanged = handler == null ? () -> { } : handler;
    }

    /** The list, for tests that drive it with key events. */
    ListView<Breakpoint> list() {
        return list;
    }

    private static String shortName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
