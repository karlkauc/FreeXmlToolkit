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
 * toggles a breakpoint at that line; a right-click opens a menu for its properties (XPath
 * condition, hit-count rule). A breakpoint with such rules shows a question-mark marker.</p>
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
    /** Added to the slot of a breakpoint that has a condition or a hit-count rule. */
    public static final String CONDITIONAL_STYLE_CLASS = "breakpoint-conditional";
    /** Added to the slot of a breakpoint the last completed run never reached. */
    public static final String UNREACHED_STYLE_CLASS = "breakpoint-unreached";
    /** Coverage: the line ran in the last run (green bar). */
    public static final String COVERAGE_EXECUTED_STYLE_CLASS = "coverage-executed";
    /** Coverage: the line is in a template / function the last run never entered (red bar). */
    public static final String COVERAGE_DEAD_STYLE_CLASS = "coverage-dead";
    private static final String NOT_REACHED =
            "Not reached in the last run (no traced instruction on this line, or the code did not run)";
    /** Added to the slot of a disabled breakpoint, or of any breakpoint while all are muted. */
    public static final String INACTIVE_STYLE_CLASS = "breakpoint-inactive";

    private static final double SLOT_SIZE = 14;

    private final BreakpointTarget session;
    private final Consumer<Void> refreshCallback;
    private volatile String filePath = "";
    private volatile int currentExecutionLine = -1;
    private BreakpointPropertiesDialog.Prompt propertiesPrompt = BreakpointPropertiesDialog.DEFAULT;

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

    /** @return where this gutter's breakpoints live (a debug session or the file's own store). */
    public BreakpointTarget getTarget() {
        return session;
    }

    /** Replaces the properties prompt (tests). */
    public void setPropertiesPrompt(BreakpointPropertiesDialog.Prompt prompt) {
        this.propertiesPrompt = prompt == null ? BreakpointPropertiesDialog.DEFAULT : prompt;
    }

    /**
     * Asks for the condition and hit-count rule of the breakpoint on a 1-based line (creating
     * the breakpoint if the line has none) and applies them — the gutter's "Add Conditional
     * Breakpoint…" / "Breakpoint Properties…".
     *
     * @return whether new settings were applied
     */
    public boolean editBreakpoint(int line1Based, javafx.stage.Window owner) {
        return editBreakpoint(line1Based, owner, false);
    }

    /**
     * Like {@link #editBreakpoint(int, javafx.stage.Window)}; {@code asLogpoint} starts a line
     * without a breakpoint as a logpoint that does not pause ("Add Logpoint…").
     */
    public boolean editBreakpoint(int line1Based, javafx.stage.Window owner, boolean asLogpoint) {
        if (session == null || line1Based < 1) {
            return false;
        }
        org.fxt.freexmltoolkit.debugger.Breakpoint bp = session.breakpointAt(filePath, line1Based);
        BreakpointPropertiesDialog.Settings current = bp == null && asLogpoint
                ? new BreakpointPropertiesDialog.Settings("", null, "line " + line1Based + " reached", false)
                : BreakpointPropertiesDialog.Settings.of(bp);
        String file = filePath.isEmpty() ? "" : org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(filePath) + ", ";
        java.util.Optional<BreakpointPropertiesDialog.Settings> answer = propertiesPrompt.ask(owner,
                file + "line " + line1Based, current, session.hitCount(filePath, line1Based));
        if (answer.isEmpty()) {
            return false;
        }
        BreakpointPropertiesDialog.Settings settings = answer.get();
        boolean applied = session.updateBreakpoint(filePath, line1Based, settings::applyTo);
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

        org.fxt.freexmltoolkit.debugger.Breakpoint bp = session == null ? null : session.breakpointAt(filePath, line);
        boolean hasBp = bp != null || (session != null && session.hasBreakpoint(filePath, line));
        boolean conditional = bp != null && bp.hasRules();
        boolean inactive = hasBp && ((bp != null && !bp.enabled()) || session.isMuted());
        if (conditional) {
            box.getStyleClass().add(CONDITIONAL_STYLE_CLASS);
        }
        if (inactive) {
            box.getStyleClass().add(INACTIVE_STYLE_CLASS);
        }
        boolean isCurrent = line == currentExecutionLine;
        String coverageTip = null;
        if (session != null) {
            int runs = session.coverageCount(filePath, line);
            if (runs > 0) {
                box.getStyleClass().add(COVERAGE_EXECUTED_STYLE_CLASS);
                coverageTip = "Ran " + runs + (runs == 1 ? " time" : " times") + " in the last run";
            } else if (session.coverageDead(filePath, line)) {
                box.getStyleClass().add(COVERAGE_DEAD_STYLE_CLASS);
                coverageTip = "Never ran in the last run: its template / function was not entered";
            }
        }

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
        } else if (inactive) {
            // Hollow marker: kept with its settings, but it does not pause.
            IconifyIcon fi = new IconifyIcon(conditional ? "bi-question-circle" : "bi-circle");
            fi.setIconSize(conditional ? 11 : 10);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.DANGER);
            String why = session.isMuted() && (bp == null || bp.enabled())
                    ? "All breakpoints are muted" : "Breakpoint disabled";
            Tooltip.install(fi, new Tooltip(why + (conditional ? "\n" + describe(bp, line) : "")
                    + "\nClick to remove, right-click to enable or edit"));
            icon = fi;
        } else if (bp != null && bp.isLogpoint()) {
            // Logpoint: a diamond — red when it also pauses, blue when it only logs.
            IconifyIcon fi = new IconifyIcon("bi-diamond-fill");
            fi.setIconSize(10);
            SemanticIcon.paint(fi, bp.suspend() ? DesignTokens.ColorToken.DANGER : DesignTokens.ColorToken.INFO);
            Tooltip.install(fi, new Tooltip(describe(bp, line)
                    + "\nClick to remove the logpoint, right-click to edit it"));
            icon = fi;
        } else if (conditional) {
            IconifyIcon fi = new IconifyIcon("bi-question-circle-fill");
            fi.setIconSize(11);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.DANGER);
            Tooltip.install(fi, new Tooltip(describe(bp, line)
                    + "\nClick to remove the breakpoint, right-click to edit it"));
            icon = fi;
        } else if (hasBp) {
            IconifyIcon fi = new IconifyIcon("bi-circle-fill");
            fi.setIconSize(10);
            int hits = session.hitCount(filePath, line);
            boolean unreached = session.notReachedInLastRun(filePath, line);
            // A breakpoint the last run never reached reads amber instead of red.
            SemanticIcon.paint(fi, unreached ? DesignTokens.ColorToken.WARNING : DesignTokens.ColorToken.DANGER);
            if (unreached) {
                box.getStyleClass().add(UNREACHED_STYLE_CLASS);
            }
            Tooltip.install(fi, new Tooltip((unreached ? NOT_REACHED + "\n" : "")
                    + (hits > 0 ? "Hits in this run: " + hits + "\n" : "")
                    + "Click to remove breakpoint, right-click for a condition or hit count"));
            icon = fi;
        } else {
            Region spacer = new Region();
            spacer.setPrefSize(SLOT_SIZE, SLOT_SIZE);
            spacer.setMinSize(SLOT_SIZE, SLOT_SIZE);
            spacer.setMaxSize(SLOT_SIZE, SLOT_SIZE);
            if (session != null) {
                Tooltip.install(box, new Tooltip((coverageTip == null ? "" : coverageTip + "\n")
                        + "Click to set a breakpoint"));
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
                    menuFor(line).show(box, evt.getScreenX(), evt.getScreenY());
                }
                evt.consume();
            }
        });
        box.addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                javafx.event.Event::consume);
        return box;
    }

    /** Tooltip text of a breakpoint with rules: condition, hit rule and the hits of this run. */
    private String describe(org.fxt.freexmltoolkit.debugger.Breakpoint bp, int line) {
        StringBuilder sb = new StringBuilder();
        if (bp.hasCondition()) {
            sb.append("Condition: ").append(bp.condition());
        }
        if (bp.hit().isSet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(bp.isLogpoint() ? "On " : "Pause on ").append(bp.hit().describe());
        }
        if (bp.isLogpoint()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("Log: ").append(bp.logMessage()).append(bp.suspend() ? "  (and pause)" : "  (no pause)");
        }
        int hits = session.hitCount(filePath, line);
        if (hits > 0) {
            sb.append("\nHits in this run: ").append(hits);
        }
        if (session.notReachedInLastRun(filePath, line)) {
            sb.append('\n').append(NOT_REACHED);
        }
        return sb.toString();
    }

    /** The slot's context menu: properties (condition, hit count), remove rules, remove the breakpoint. */
    ContextMenu menuFor(int line) {
        org.fxt.freexmltoolkit.debugger.Breakpoint bp = session.breakpointAt(filePath, line);
        boolean hasBp = bp != null || session.hasBreakpoint(filePath, line);
        ContextMenu menu = new ContextMenu();
        if (session.canRunTo()) {
            MenuItem runTo = new MenuItem("Run to Here", menuIcon("bi-skip-forward-fill", DesignTokens.ColorToken.SUCCESS));
            runTo.setOnAction(e -> session.runTo(filePath, line));
            menu.getItems().addAll(runTo, new SeparatorMenuItem());
        }
        MenuItem edit = new MenuItem(hasBp ? "Breakpoint Properties…" : "Add Conditional Breakpoint…",
                menuIcon("bi-sliders", DesignTokens.ColorToken.PRIMARY));
        edit.setOnAction(e -> editBreakpoint(line, menu.getOwnerWindow()));
        menu.getItems().add(edit);
        if (!hasBp) {
            MenuItem logpoint = new MenuItem("Add Logpoint…", menuIcon("bi-diamond-fill", DesignTokens.ColorToken.INFO));
            logpoint.setOnAction(e -> editBreakpoint(line, menu.getOwnerWindow(), true));
            menu.getItems().add(logpoint);
        }
        if (bp != null && bp.hasCondition()) {
            MenuItem clear = new MenuItem("Remove Condition", menuIcon("bi-x-circle", DesignTokens.ColorToken.NEUTRAL));
            clear.setOnAction(e -> {
                session.setCondition(filePath, line, "");
                if (refreshCallback != null) refreshCallback.accept(null);
            });
            menu.getItems().add(clear);
        }
        if (bp != null && bp.hit().isSet()) {
            MenuItem clearHit = new MenuItem("Remove Hit Count Rule", menuIcon("bi-hash", DesignTokens.ColorToken.NEUTRAL));
            clearHit.setOnAction(e -> {
                session.setHitCondition(filePath, line, null);
                if (refreshCallback != null) refreshCallback.accept(null);
            });
            menu.getItems().add(clearHit);
        }
        if (bp != null) {
            MenuItem toggle = new MenuItem(bp.enabled() ? "Disable Breakpoint" : "Enable Breakpoint",
                    menuIcon(bp.enabled() ? "bi-slash-circle" : "bi-circle-fill",
                            bp.enabled() ? DesignTokens.ColorToken.NEUTRAL : DesignTokens.ColorToken.DANGER));
            toggle.setOnAction(e -> {
                session.setEnabled(filePath, line, !bp.enabled());
                if (refreshCallback != null) refreshCallback.accept(null);
            });
            menu.getItems().add(toggle);
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
