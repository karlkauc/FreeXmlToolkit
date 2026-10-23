package org.fxt.freexmltoolkit.debugger.ui;

import java.util.function.Consumer;
import java.util.function.IntFunction;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
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
 * toggles a breakpoint at that line.</p>
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

    private static final double SLOT_SIZE = 14;

    private final BreakpointTarget session;
    private final Consumer<Void> refreshCallback;
    private volatile String filePath = "";
    private volatile int currentExecutionLine = -1;

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

    /** Sets the 1-based line number that should display the execution arrow, or -1 to clear. */
    public void setCurrentExecutionLine(int line1Based) {
        this.currentExecutionLine = line1Based;
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
        } else if (hasBp) {
            IconifyIcon fi = new IconifyIcon("bi-circle-fill");
            fi.setIconSize(10);
            SemanticIcon.paint(fi, DesignTokens.ColorToken.DANGER);
            Tooltip.install(fi, new Tooltip("Click to remove breakpoint"));
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
            }
        });
        return box;
    }
}
