package org.fxt.freexmltoolkit.debugger.ui;

import java.util.function.Consumer;
import java.util.function.IntFunction;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Paragraph graphic factory contributing breakpoint markers and the
 * current-execution-line arrow to the editor gutter.
 *
 * <p>Each line gets a fixed-width slot containing either an icon or an
 * invisible spacer so that line widths remain aligned. Clicking the slot
 * toggles a breakpoint at that line.</p>
 */
public class BreakpointGutterFactory implements IntFunction<Node> {

    private static final double SLOT_SIZE = 14;

    private final DebugSession session;
    private final Consumer<Void> refreshCallback;
    private volatile String filePath = "";
    private volatile int currentExecutionLine = -1;

    public BreakpointGutterFactory(DebugSession session, Consumer<Void> refreshCallback) {
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
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(SLOT_SIZE);
        box.setPrefWidth(SLOT_SIZE);
        box.setMaxWidth(SLOT_SIZE);

        boolean hasBp = session != null && session.hasBreakpoint(filePath, line);
        boolean isCurrent = line == currentExecutionLine;

        Node icon;
        if (isCurrent && hasBp) {
            // Both — show a yellow arrow on top of the breakpoint
            FontIcon fi = new FontIcon("bi-arrow-right-circle-fill");
            fi.setIconSize(12);
            fi.setIconColor(javafx.scene.paint.Color.web("#ffc107"));
            icon = fi;
        } else if (isCurrent) {
            FontIcon fi = new FontIcon("bi-arrow-right-circle-fill");
            fi.setIconSize(12);
            fi.setIconColor(javafx.scene.paint.Color.web("#28a745"));
            icon = fi;
        } else if (hasBp) {
            FontIcon fi = new FontIcon("bi-circle-fill");
            fi.setIconSize(10);
            fi.setIconColor(javafx.scene.paint.Color.web("#dc3545"));
            Tooltip.install(fi, new Tooltip("Click to remove breakpoint"));
            icon = fi;
        } else {
            Region spacer = new Region();
            spacer.setPrefSize(SLOT_SIZE, SLOT_SIZE);
            spacer.setMinSize(SLOT_SIZE, SLOT_SIZE);
            spacer.setMaxSize(SLOT_SIZE, SLOT_SIZE);
            icon = spacer;
        }
        box.getChildren().add(icon);

        box.setOnMouseClicked(evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && session != null) {
                session.toggleBreakpoint(filePath, line);
                if (refreshCallback != null) refreshCallback.accept(null);
                evt.consume();
            }
        });
        return box;
    }
}
