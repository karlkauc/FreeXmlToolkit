package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;

/**
 * The Debug tool tab: a step-controls toolbar over the four debugger panels
 * (breakpoints / variables / call stack / watch). Subscribes to the session
 * state and refreshes on each pause.
 */
public class XsltDebugView extends VBox {

    private final XsltDebugController controller;
    private final BreakpointsView breakpointsView;
    private final VariablesView variablesView = new VariablesView();
    private final CallStackView callStackView = new CallStackView();
    private final WatchView watchView = new WatchView();
    private final Label status = new Label("Ready.");

    private final IntConsumer onCurrentLine;  // updates the gutter arrow (line, or -1 to clear)
    private final Runnable onShowInEditor;    // jumps the editor to the paused line

    public XsltDebugView(XsltDebugController controller,
            String xsltFilePath,
            Supplier<String> activeXmlSupplier,
            IntConsumer onCurrentLine,
            Runnable onShowInEditor) {
        this.controller = controller;
        this.onCurrentLine = onCurrentLine == null ? line -> { } : onCurrentLine;
        this.onShowInEditor = onShowInEditor == null ? () -> { } : onShowInEditor;
        this.breakpointsView = new BreakpointsView(controller.getSession());

        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().add("fxt-side-panel-content");

        watchView.setXmlSupplier(activeXmlSupplier);
        breakpointsView.setOnJumpToLine(line -> this.onShowInEditor.run());
        breakpointsView.setOnChanged(this.onShowInEditor::run);
        callStackView.setOnJumpToLine(line -> this.onShowInEditor.run());

        HBox toolbar = buildToolbar();
        status.getStyleClass().add("fxt-placeholder-text");

        SplitPane panels = new SplitPane(breakpointsView, variablesView, callStackView, watchView);
        panels.setDividerPositions(0.25, 0.5, 0.75);
        VBox.setVgrow(panels, Priority.ALWAYS);

        getChildren().addAll(toolbar, status, panels);

        controller.getSession().addPropertyChangeListener(evt -> {
            if (DebugSession.PROP_STATE.equals(evt.getPropertyName())) {
                Platform.runLater(() -> onStateChanged((DebugSession.State) evt.getNewValue()));
            }
        });
    }

    private HBox buildToolbar() {
        Button continueBtn = stepButton("Continue", "bi-play-fill", controller::continueRun);
        Button stepInto = stepButton("Step Into", "bi-box-arrow-in-down-right", controller::stepInto);
        Button stepOver = stepButton("Step Over", "bi-arrow-right-short", controller::stepOver);
        Button stepOut = stepButton("Step Out", "bi-box-arrow-up-right", controller::stepOut);
        Button stop = stepButton("Stop", "bi-stop-fill", controller::stop);
        Button showInEditor = stepButton("Show in editor", "bi-eye", onShowInEditor);
        return new HBox(6, continueBtn, stepInto, stepOver, stepOut, stop, showInEditor);
    }

    private Button stepButton(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private void onStateChanged(DebugSession.State state) {
        switch (state) {
            case PAUSED -> {
                PausedSnapshot snap = controller.getSession().getPausedSnapshot();
                if (snap != null) {
                    variablesView.setVariables(snap.variables());
                    callStackView.setFrames(snap.callStack());
                    watchView.evaluateAll();
                    onCurrentLine.accept(snap.lineNumber());
                    String ctx = snap.contextItem().isEmpty() ? "" : "  ·  " + snap.contextItem();
                    status.setText("Paused at line " + snap.lineNumber() + ctx);
                }
            }
            case RUNNING -> {
                onCurrentLine.accept(-1);
                status.setText("Running…");
            }
            case STOPPED -> {
                onCurrentLine.accept(-1);
                variablesView.clear();
                callStackView.clear();
                status.setText("Stopped.");
            }
            case IDLE -> {
                onCurrentLine.accept(-1);
                variablesView.clear();
                callStackView.clear();
                status.setText("Finished.");
            }
        }
    }

    public BreakpointsView getBreakpointsView() { return breakpointsView; }
    public VariablesView getVariablesView() { return variablesView; }
    public CallStackView getCallStackView() { return callStackView; }
    public WatchView getWatchView() { return watchView; }
    public String getStatusText() { return status.getText(); }
}
