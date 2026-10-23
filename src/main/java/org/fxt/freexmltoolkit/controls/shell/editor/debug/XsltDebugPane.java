package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.beans.PropertyChangeListener;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * The DEBUG pane of the editor's bottom dock: run controls and status in the header, the
 * debugger panels (Variables · Call Stack · Watch · Messages · Breakpoints) underneath.
 * Subscribes to the session state (pauses), the controller's live messages and the run's
 * completion; the {@code EditorHost} supplies the callbacks that touch the editor
 * (execution-line marker, jumps, result display, ending the session).
 */
public final class XsltDebugPane extends VBox {

    private final XsltDebugController controller;
    private final BreakpointsView breakpointsView;
    private final VariablesView variablesView = new VariablesView();
    private final CallStackView callStackView = new CallStackView();
    private final WatchView watchView = new WatchView();
    private final MessagesView messagesView = new MessagesView();

    @FXML private Label status;
    @FXML private Button continueButton;
    @FXML private Button stepIntoButton;
    @FXML private Button stepOverButton;
    @FXML private Button stepOutButton;
    @FXML private Button restartButton;
    @FXML private Button stopButton;
    @FXML private Button showButton;
    @FXML private Button closeButton;
    @FXML private CheckBox pauseOnEntry;
    @FXML private SplitPane panels;

    private Consumer<PausedSnapshot> onPaused = snapshot -> { };
    private Runnable onShowInEditor = () -> { };
    private Runnable onClose = () -> { };
    private Consumer<XsltTransformationResult> onResult = result -> { };
    private Consumer<Breakpoint> onJumpToBreakpoint = bp -> { };
    private Consumer<DebugStackFrame> onJumpToFrame = frame -> { };
    private Consumer<TransformationMessage> onJumpToMessage = msg -> { };
    private Runnable onBreakpointsChanged = () -> { };

    private final PropertyChangeListener stateListener;
    private final Consumer<TransformationMessage> messageListener;
    private final Consumer<XsltTransformationResult> completionListener;

    public XsltDebugPane(XsltDebugController controller) {
        this.controller = controller;
        this.breakpointsView = new BreakpointsView(controller.getSession());
        FxmlViews.load(this, "/pages/panels/XsltDebugPane.fxml");

        panels.getItems().addAll(variablesView, callStackView, watchView, messagesView, breakpointsView);
        panels.setDividerPositions(0.24, 0.44, 0.62, 0.84);

        pauseOnEntry.setSelected(controller.isPauseOnEntry());
        breakpointsView.setOnJumpToBreakpoint(bp -> onJumpToBreakpoint.accept(bp));
        breakpointsView.setOnChanged(() -> onBreakpointsChanged.run());
        callStackView.setOnJumpToFrame(frame -> onJumpToFrame.accept(frame));
        messagesView.setOnJumpTo(msg -> onJumpToMessage.accept(msg));

        stateListener = evt -> {
            if (DebugSession.PROP_STATE.equals(evt.getPropertyName())) {
                Platform.runLater(() -> onStateChanged((DebugSession.State) evt.getNewValue()));
            }
        };
        messageListener = msg -> Platform.runLater(() -> messagesView.append(msg));
        completionListener = result -> Platform.runLater(() -> onFinished(result));
        controller.getSession().addPropertyChangeListener(stateListener);
        controller.addMessageListener(messageListener);
        controller.addCompletionListener(completionListener);
        applyButtonState(controller.getSession().getState());
    }

    /** Detaches every listener; call when the pane leaves the dock. */
    public void dispose() {
        controller.getSession().removePropertyChangeListener(stateListener);
        controller.removeMessageListener(messageListener);
        controller.removeCompletionListener(completionListener);
    }

    // ----- callbacks wired by the EditorHost ---------------------------------------

    /** Receives the snapshot on every pause, and {@code null} whenever the run moves on. */
    public void setOnPaused(Consumer<PausedSnapshot> handler) {
        this.onPaused = handler == null ? snapshot -> { } : handler;
    }

    public void setOnShowInEditor(Runnable handler) {
        this.onShowInEditor = handler == null ? () -> { } : handler;
    }

    /** Invoked by the header ✕ — ends the session. */
    public void setOnClose(Runnable handler) {
        this.onClose = handler == null ? () -> { } : handler;
    }

    /** Receives the run's result (finished, stopped or failed) for the OUTPUT panel. */
    public void setOnResult(Consumer<XsltTransformationResult> handler) {
        this.onResult = handler == null ? result -> { } : handler;
    }

    public void setOnJumpToBreakpoint(Consumer<Breakpoint> handler) {
        this.onJumpToBreakpoint = handler == null ? bp -> { } : handler;
    }

    public void setOnJumpToFrame(Consumer<DebugStackFrame> handler) {
        this.onJumpToFrame = handler == null ? frame -> { } : handler;
    }

    public void setOnJumpToMessage(Consumer<TransformationMessage> handler) {
        this.onJumpToMessage = handler == null ? msg -> { } : handler;
    }

    /** Invoked after the Breakpoints panel removed/cleared breakpoints (gutter refresh, persistence). */
    public void setOnBreakpointsChanged(Runnable handler) {
        this.onBreakpointsChanged = handler == null ? () -> { } : handler;
    }

    // ----- header actions ---------------------------------------------------------------

    @FXML public void onContinue() { controller.continueRun(); }
    @FXML public void onStepInto() { controller.stepInto(); }
    @FXML public void onStepOver() { controller.stepOver(); }
    @FXML public void onStepOut() { controller.stepOut(); }
    @FXML public void onStop() { controller.stop(); }
    @FXML public void onShowInEditor() { onShowInEditor.run(); }
    @FXML public void onClose() { onClose.run(); }

    @FXML
    public void onRestart() {
        messagesView.clear();
        status.setText("Restarting…");
        controller.restart();
    }

    @FXML
    public void onPauseOnEntry() {
        controller.setPauseOnEntry(pauseOnEntry.isSelected());
    }

    /**
     * Debugger keys while a session is alive: F5 Continue, F7 Step Into, F8 Step Over,
     * Shift+F8 Step Out (the step keys only while paused, so F8 keeps validating otherwise).
     *
     * @return {@code true} if the key was handled
     */
    public boolean handleShortcut(KeyEvent event) {
        boolean paused = controller.getSession().getState() == DebugSession.State.PAUSED;
        if (!paused || event.isShortcutDown() || event.isAltDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.F5 && !event.isShiftDown()) {
            controller.continueRun();
            return true;
        }
        if (code == KeyCode.F7 && !event.isShiftDown()) {
            controller.stepInto();
            return true;
        }
        if (code == KeyCode.F8) {
            if (event.isShiftDown()) {
                controller.stepOut();
            } else {
                controller.stepOver();
            }
            return true;
        }
        return false;
    }

    // ----- session events ---------------------------------------------------------------

    private void onStateChanged(DebugSession.State state) {
        applyButtonState(state);
        switch (state) {
            case PAUSED -> {
                PausedSnapshot snap = controller.getSession().getPausedSnapshot();
                if (snap != null) {
                    variablesView.setVariables(snap.variables());
                    callStackView.setFrames(snap.callStack());
                    watchView.setEvaluator(controller.getWatchEvaluator());
                    watchView.evaluateAll(snap);
                    String file = snap.hasFile() ? org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(snap.systemId()) + ":" : "line ";
                    String ctx = snap.contextItem().isEmpty() ? "" : "  ·  " + snap.contextItem();
                    status.setText("Paused at " + file + snap.lineNumber() + ctx);
                    onPaused.accept(snap);
                }
            }
            case RUNNING -> {
                variablesView.clear();
                callStackView.clear();
                watchView.evaluateAll(null);
                if (!status.getText().startsWith("Restarting")) {
                    messagesView.clear();
                }
                status.setText("Running…");
                onPaused.accept(null);
            }
            case STOPPED -> {
                variablesView.clear();
                callStackView.clear();
                watchView.evaluateAll(null);
                status.setText("Stopped.");
                onPaused.accept(null);
            }
            case IDLE -> {
                variablesView.clear();
                callStackView.clear();
                watchView.evaluateAll(null);
                if (!status.getText().startsWith("Finished") && !status.getText().startsWith("Error")) {
                    status.setText("Finished.");
                }
                onPaused.accept(null);
            }
            default -> { /* no UI change for other states */ }
        }
    }

    private void onFinished(XsltTransformationResult result) {
        if (result == null) {
            return;
        }
        if (result.isSuccess()) {
            status.setText("Finished · " + result.getExecutionTime() + " ms · "
                    + (result.getOutputContent() == null ? 0 : result.getOutputContent().length())
                    + " chars → OUTPUT");
        } else if (result.getErrorMessage() != null && result.getErrorMessage().contains("stopped")) {
            status.setText("Stopped.");
        } else {
            status.setText("Error: " + (result.getErrorMessage() == null ? "transformation failed"
                    : result.getErrorMessage()));
        }
        applyButtonState(controller.getSession().getState());
        onResult.accept(result);
    }

    private void applyButtonState(DebugSession.State state) {
        boolean paused = state == DebugSession.State.PAUSED;
        boolean running = state == DebugSession.State.RUNNING;
        continueButton.setDisable(!paused);
        stepIntoButton.setDisable(!paused);
        stepOverButton.setDisable(!paused);
        stepOutButton.setDisable(!paused);
        stopButton.setDisable(!(paused || running));
        showButton.setDisable(!paused);
    }

    // ----- accessors (EditorHost / tests) -------------------------------------------------

    public XsltDebugController getController() { return controller; }
    public BreakpointsView getBreakpointsView() { return breakpointsView; }
    public VariablesView getVariablesView() { return variablesView; }
    public CallStackView getCallStackView() { return callStackView; }
    public WatchView getWatchView() { return watchView; }
    public MessagesView getMessagesView() { return messagesView; }
    public String getStatusText() { return status.getText(); }
    public boolean isPauseOnEntrySelected() { return pauseOnEntry.isSelected(); }
}
