package org.fxt.freexmltoolkit.debugger.ui;

import java.util.function.Consumer;
import java.util.function.IntFunction;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.debugger.BreakpointTarget;

/**
 * Paragraph graphic factory contributing breakpoint markers and the
 * current-execution-line arrow to the editor gutter.
 *
 * <p>Each line gets a fixed-width slot containing either an icon or an
 * invisible spacer so that line widths remain aligned. Pressing the slot
 * toggles a breakpoint at that line; a right-click opens a menu to add, edit or remove a
 * condition (a conditional breakpoint shows a question-mark marker).</p>
 *
 * <p>The toggle happens on {@code MOUSE_PRESSED}, not on {@code MOUSE_CLICKED}: a press
 * that reached the {@code CodeArea} would move the caret to that line, the editor would
 * re-style the paragraph, RichTextFX would rebuild the line's cell — and the slot that
 * received the press would be gone before the release, so no click ever arrived. Consuming
 * the press keeps the caret where it is, as IDE gutters do.</p>
 */
public class BreakpointGutterFactory implements IntFunction<Node> {

    /** Style class of every slot (tests and CSS look the slot up by it). */
    public static final String SLOT_STYLE_CLASS = "breakpoint-gutter-slot";
    /** Added to the slot of a breakpoint that has a condition. */
    public static final String CONDITIONAL_STYLE_CLASS = "breakpoint-conditional";

    private static final double SLOT_SIZE = 14;

    private final BreakpointTarget session;
    private final Consumer<Void> refreshCallback;
    private volatile String filePath = "";
    private volatile int currentExecutionLine = -1;
    private BreakpointConditionDialog.Prompt conditionPrompt = BreakpointConditionDialog.DEFAULT;

    /**
     * @param session         where breakpoints live: the running {@code DebugSession}, or a
     *                        file's persisted breakpoints while no session runs
     * @param refreshCallback invoked after a toggle so the host can re-render the gutter
     */
    public BreakpointGutterFactory(BreakpointTarget session, Consumer<Void> refreshCallback) {
        this.session = session;
        this.refreshCallback = refreshCallback;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath == null ? "" : filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    /** Replaces the condition prompt (tests). */
    public void setConditionPrompt(BreakpointConditionDialog.Prompt prompt) {
        this.conditionPrompt = prompt == null ? BreakpointConditionDialog.DEFAULT : prompt;
    }

    /**
     * Asks for the condition of the breakpoint on a 1-based line (creating the breakpoint if
     * the line has none) and applies it — the gutter's "Add Conditional Breakpoint…" /
     * "Edit Condition…".
     *
     * @return whether a condition was applied
     */
    public boolean editCondition(int line1Based, javafx.stage.Window owner) {
        if (session == null || line1Based < 1) {
            return false;
        }
        String current = session.conditionAt(filePath, line1Based);
        String file = filePath.isEmpty() ? "" : org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(filePath) + ", ";
        java.util.Optional<String> answer = conditionPrompt.ask(owner, file + "line " + line1Based,
                current == null ? "" : current);
        if (answer.isEmpty()) {
            return false;
        }
        boolean applied = session.setCondition(filePath, line1Based, answer.get());
        if (applied && refreshCallback != null) {
            refreshCallback.accept(null);
        }
        return applied;
    }

    /** Sets the 1-based line number that should display the execution arrow, or -1 to clear. */
    public void setCurrentExecutionLine(int line1Based) {
        this.currentExecutionLine = line1Based;
    }

    /**
     * Toggles the breakpoint on a 1-based line programmatically (F9 / Tools → Toggle
     * Breakpoint) — the same as pressing the line's slot.
     *
     * @return the new state (true = set), or {@code false} without a target
     */
    public boolean toggle(int line1Based) {
        if (session == null || line1Based < 1) {
            return false;
        }
        boolean set = session.toggleBreakpoint(filePath, line1Based);
        if (refreshCallback != null) {
            refreshCallback.accept(null);
        }
        return set;
    }

    @Override
    public Node apply(int lineIndex) {
        int line = lineIndex + 1;
        HBox box = new HBox();
        box.getStyleClass().add(SLOT_STYLE_CLASS);
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(SLOT_SIZE);
        box.setPrefWidth(SLOT_SIZE);
        box.setMaxWidth(SLOT_SIZE);
        box.setCursor(Cursor.HAND);

        boolean hasBp = session != null && session.hasBreakpoint(filePath, line);
        String condition = hasBp ? session.conditionAt(filePath, line) : null;
        boolean conditional = condition != null && !condition.isEmpty();
        if (conditional) {
            box.getStyleClass().add(CONDITIONAL_STYLE_CLASS);
        }
        boolean isCurrent = line == currentExecutionLine;

        Node icon;
        if (isCurrent && hasBp) {
            // Both — show a yellow arrow on top of the breakpoint
            IconifyIcon fi = new IconifyIcon("bi-arrow-right-circle-fill");
            fi.setIconSize(12);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.WARNING);
            icon = fi;
        } else if (isCurrent) {
            IconifyIcon fi = new IconifyIcon("bi-arrow-right-circle-fill");
            fi.setIconSize(12);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.SUCCESS);
            icon = fi;
        } else if (conditional) {
            IconifyIcon fi = new IconifyIcon("bi-question-circle-fill");
            fi.setIconSize(11);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.DANGER);
            Tooltip.install(fi, new Tooltip("Condition: " + condition
                    + "\nClick to remove the breakpoint, right-click to edit the condition"));
            icon = fi;
        } else if (hasBp) {
            IconifyIcon fi = new IconifyIcon("bi-circle-fill");
            fi.setIconSize(10);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.DANGER);
            Tooltip.install(fi, new Tooltip("Click to remove breakpoint, right-click to add a condition"));
            icon = fi;
        } else {
            Region spacer = new Region();
            spacer.setPrefSize(SLOT_SIZE, SLOT_SIZE);
            spacer.setMinSize(SLOT_SIZE, SLOT_SIZE);
            spacer.setMaxSize(SLOT_SIZE, SLOT_SIZE);
            if (session != null) {
                Tooltip.install(box, new Tooltip("Click to set a breakpoint"));
            }
            icon = spacer;
        }
        box.getChildren().add(icon);

        box.addEventHandler(MouseEvent.MOUSE_PRESSED, evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && session != null) {
                session.toggleBreakpoint(filePath, line);
                if (refreshCallback != null) refreshCallback.accept(null);
                evt.consume();
            } else if (evt.getButton() == MouseButton.SECONDARY && session != null) {
                // Consumed like the primary press: the editor's own context menu stays closed.
                if (box.getScene() != null && box.getScene().getWindow() != null) {
                    menuFor(line, hasBp, conditional).show(box, evt.getScreenX(), evt.getScreenY());
                }
                evt.consume();
            }
        });
        box.addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                javafx.event.Event::consume);
        return box;
    }

    /** The slot's context menu: add/edit/remove the condition, remove the breakpoint. */
    ContextMenu menuFor(int line, boolean hasBp, boolean conditional) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem(hasBp ? (conditional ? "Edit Condition…" : "Add Condition…")
                : "Add Conditional Breakpoint…", menuIcon("bi-funnel", DesignTokens.ColorToken.PRIMARY));
        edit.setOnAction(e -> editCondition(line, menu.getOwnerWindow()));
        menu.getItems().add(edit);
        if (conditional) {
            MenuItem clear = new MenuItem("Remove Condition", menuIcon("bi-x-circle", DesignTokens.ColorToken.NEUTRAL));
            clear.setOnAction(e -> {
                session.setCondition(filePath, line, "");
                if (refreshCallback != null) refreshCallback.accept(null);
            });
            menu.getItems().add(clear);
        }
        if (hasBp) {
            MenuItem remove = new MenuItem("Remove Breakpoint", menuIcon("bi-trash", DesignTokens.ColorToken.DANGER));
            remove.setOnAction(e -> {
                session.toggleBreakpoint(filePath, line);
                if (refreshCallback != null) refreshCallback.accept(null);
            });
            menu.getItems().addAll(new SeparatorMenuItem(), remove);
        }
        return menu;
    }

    private static IconifyIcon menuIcon(String literal, DesignTokens.ColorToken color) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return SemanticIcon.paint(icon, color);
    }
}
