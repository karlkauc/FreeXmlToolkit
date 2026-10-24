package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmValue;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.VariableScope;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * The DEBUG pane of the editor's bottom dock: run controls and status in the header, the
 * debugger panels underneath as two tab groups in a split — Variables | Watch on the left
 * (the state you read on every pause), Call Stack | Messages | Breakpoints on the right
 * (navigation and log). The divider is persisted ({@value #DIVIDER_KEY}); Messages and
 * Breakpoints carry a count badge, Messages is marked when it receives output while another
 * tab is in front and comes to the front when the run fails.
 * Subscribes to the session state (pauses), the controller's live messages and the run's
 * completion; the {@code EditorHost} supplies the callbacks that touch the editor
 * (execution-line marker, jumps, result display, ending the session).
 */
public final class XsltDebugPane extends VBox {

    /** Preference key of the split divider (ratio of the left group, {@value #MIN_DIVIDER}–{@value #MAX_DIVIDER}). */
    static final String DIVIDER_KEY = "editor.debugPane.divider";
    static final double DEFAULT_DIVIDER = 0.5;
    static final double MIN_DIVIDER = 0.2;
    static final double MAX_DIVIDER = 0.8;
    /** Same marker the dock strip uses for a panel that received content while hidden. */
    static final String UPDATED_CLASS = "fxt-dock-updated";
    /** Status style while paused because a breakpoint condition failed to evaluate. */
    static final String CONDITION_ERROR_CLASS = "fxt-debug-condition-error";

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
    @FXML private TabPane leftTabs;
    @FXML private TabPane rightTabs;
    @FXML private Tab variablesTab;
    @FXML private Tab watchTab;
    @FXML private Tab callStackTab;
    @FXML private Tab messagesTab;
    @FXML private Tab breakpointsTab;

    private final Label messagesBadge = badge();
    private final Label breakpointsBadge = badge();
    /** The divider ratio as last restored or saved; a drag that ends elsewhere is persisted. */
    private double knownDivider = DEFAULT_DIVIDER;
    /**
     * The scope the Variables table and the watches show: the pause itself (innermost frame) or a
     * snapshot derived for the selected outer frame; {@code null} while not paused.
     */
    private PausedSnapshot scope;
    /** XPath scopes derived per call stack frame of the current pause (identity of the frame). */
    private final Map<DebugStackFrame, PausedSnapshot> frameSnapshots = new java.util.IdentityHashMap<>();
    /** Open value viewer windows; closed when the pane is disposed (session end). */
    private final List<ValueViewerWindow> viewers = new ArrayList<>();

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

        variablesTab.setContent(variablesView);
        watchTab.setContent(watchView);
        callStackTab.setContent(callStackView);
        messagesTab.setContent(messagesView);
        breakpointsTab.setContent(breakpointsView);
        decorateWithBadge(messagesTab, messagesBadge);
        decorateWithBadge(breakpointsTab, breakpointsBadge);
        messagesView.setOnCountChanged(this::onMessageCountChanged);
        breakpointsView.setOnCountChanged(() -> updateBadge(breakpointsBadge, breakpointsView.getRowCount()));
        updateBadge(breakpointsBadge, breakpointsView.getRowCount());
        rightTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> {
            if (tab == messagesTab) {
                messagesTab.getStyleClass().remove(UPDATED_CLASS);
            }
        });
        installDividerPersistence();
        variablesView.setOnOpenValue(b -> openViewer(resolveVariable(b, currentScope())));
        variablesView.setOnCopyValue(b -> copyValue(resolveVariable(b, currentScope())));
        callStackView.setOnFrameSelected(this::showFrame);
        callStackView.setOnOpenValue(f -> openViewer(resolveFrame(f, controller.getSession().getPausedSnapshot())));
        callStackView.setOnCopyValue(f -> copyValue(resolveFrame(f, controller.getSession().getPausedSnapshot())));
        watchView.setOnOpenValue(row -> resolveWatch(row, this::openViewer));
        watchView.setOnCopyValue(row -> resolveWatch(row, this::copyValue));

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
        closeViewers();
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
                frameSnapshots.clear();
                PausedSnapshot snap = controller.getSession().getPausedSnapshot();
                scope = snap;
                if (snap != null) {
                    // Selecting the innermost frame (setFrames) fills Variables and the watches.
                    watchView.setEvaluator(controller.getWatchEvaluator());
                    variablesView.setVariables(snap.variables());
                    callStackView.setFrames(snap.callStack());
                    if (snap.callStack().isEmpty()) {
                        watchView.evaluateAll(snap);
                    }
                    String file = snap.hasFile() ? org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(snap.systemId()) + ":" : "line ";
                    String ctx = snap.contextItem().isEmpty() ? "" : "  ·  " + snap.contextItem();
                    String note = controller.getSession().getPauseNote();
                    status.setText("Paused at " + file + snap.lineNumber() + ctx
                            + (note == null || note.isEmpty() ? "" : "  ·  " + note));
                    status.getStyleClass().remove(CONDITION_ERROR_CLASS);
                    if (note != null && note.startsWith("condition failed")) {
                        status.getStyleClass().add(CONDITION_ERROR_CLASS);
                    }
                    onPaused.accept(snap);
                }
            }
            case RUNNING -> {
                scope = null;
                status.getStyleClass().remove(CONDITION_ERROR_CLASS);
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
                scope = null;
                variablesView.clear();
                callStackView.clear();
                watchView.evaluateAll(null);
                status.setText("Stopped.");
                onPaused.accept(null);
            }
            case IDLE -> {
                scope = null;
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
            rightTabs.getSelectionModel().select(messagesTab);
        }
        applyButtonState(controller.getSession().getState());
        onResult.accept(result);
    }

    // ----- value viewer ------------------------------------------------------------------------

    /**
     * Maps a Variables row to its full value in {@code snap}: {@code .} is the context item,
     * {@code $name} / {@code $p:name} the variable of that display name (locals first, globals
     * first for a GLOBAL row). Rows without a value of their own ({@code position()},
     * {@code <unbound>}, display-only snapshots) fall back to the row text.
     */
    static ValueSource resolveVariable(VariableBinding binding, PausedSnapshot snap) {
        String scope = binding.scope() == null ? "" : binding.scope().name();
        XdmValue value = null;
        if (snap != null) {
            String name = binding.name();
            if (".".equals(name)) {
                value = snap.contextXdm();
            } else if (name != null && name.startsWith("$")) {
                String display = name.substring(1);
                boolean global = binding.scope() == VariableScope.GLOBAL;
                value = byDisplayName(global ? snap.globalVariables() : snap.localVariables(), display);
                if (value == null) {
                    value = byDisplayName(global ? snap.localVariables() : snap.globalVariables(), display);
                }
            }
        }
        return new ValueSource(binding.name(), binding.type(), scope, value, binding.value(), snap);
    }

    /**
     * Maps a call stack frame to the viewer: its context item is the value, and XPath runs in
     * the frame's scope — its own locals plus the globals of the pause — through a snapshot
     * derived from {@code snap} (memoized per frame so a second open reuses the window).
     * Frames without captured values (beyond the capture limit, display-only snapshots) show
     * their description and location as text, without XPath.
     */
    ValueSource resolveFrame(DebugStackFrame frame, PausedSnapshot snap) {
        String where = where(frame);
        PausedSnapshot frameScope = frameScope(frame, snap);
        if (frameScope == null) {
            return new ValueSource(frame.description(), frame.kind().name(), "FRAME", null,
                    frame.description() + "\n" + where + "\n\n(No context item or variables were captured for this frame.)",
                    null, "frame at " + where);
        }
        int vars = frame.localVariables().size();
        String type = frame.contextXdm() == null ? "no context item" : WatchEvaluator.describeType(frame.contextXdm());
        return new ValueSource(frame.description(), type,
                "FRAME · " + vars + (vars == 1 ? " variable" : " variables"),
                frame.contextXdm(), frame.contextXdm() == null ? "<no context item>" : null, frameScope,
                "frame at " + where);
    }

    private static String where(DebugStackFrame frame) {
        return (frame.fileName().isEmpty() ? "line " : frame.fileName() + ":") + frame.lineNumber();
    }

    /**
     * The scope of {@code frame} at pause {@code snap}: the pause itself for the innermost
     * frame, otherwise a snapshot with the frame's context item and locals plus the pause's
     * globals, whose rows are {@code .}, the frame's locals and the globals (memoized per frame).
     *
     * @return {@code null} when the frame carries no captured values
     */
    PausedSnapshot frameScope(DebugStackFrame frame, PausedSnapshot snap) {
        if (snap == null) {
            return null;
        }
        if (!snap.callStack().isEmpty() && snap.callStack().get(0) == frame) {
            return snap;
        }
        if (!frame.hasValues()) {
            return null;
        }
        return frameSnapshots.computeIfAbsent(frame, f -> {
            List<VariableBinding> rows = new ArrayList<>();
            if (f.contextXdm() != null) {
                rows.add(new VariableBinding(".", WatchEvaluator.render(f.contextXdm(), VariableBinding.MAX_VALUE_LENGTH),
                        WatchEvaluator.describeType(f.contextXdm()), VariableScope.LOCAL));
            }
            rows.addAll(f.variables());
            snap.variables().stream().filter(v -> v.scope() == VariableScope.GLOBAL).forEach(rows::add);
            return new PausedSnapshot(f.systemId(), f.lineNumber(), snap.callStack(), rows, "",
                    f.contextXdm(), f.localVariables(), snap.globalVariables(), -1, -1);
        });
    }

    /**
     * Switches the Variables table and the watches to {@code frame} of the current pause:
     * the innermost frame shows the pause as before; an outer frame shows its context item,
     * its own locals and the globals under a banner naming the frame.
     */
    private void showFrame(DebugStackFrame frame) {
        PausedSnapshot snap = controller.getSession().getPausedSnapshot();
        if (snap == null || frame == null) {
            return;
        }
        PausedSnapshot frameScope = frameScope(frame, snap);
        int index = snap.callStack().indexOf(frame);
        if (frameScope == snap) {
            scope = snap;
            variablesView.setFrameInfo(null);
            variablesView.setVariables(snap.variables());
            watchView.evaluateAll(snap);
            return;
        }
        String banner = "Frame " + (index + 1) + " of " + snap.callStack().size() + ":  "
                + frame.description() + "  ·  " + where(frame);
        variablesView.setFrameInfo(banner);
        if (frameScope == null) {
            scope = null;
            variablesView.setVariables(List.of());
            variablesView.setPlaceholderText("No variables were captured for this frame "
                    + "(only the innermost frames carry values).");
            watchView.markUnavailable("<no values for this frame>");
            return;
        }
        scope = frameScope;
        variablesView.setVariables(frameScope.variables());
        watchView.evaluateAll(frameScope);
    }

    /** The scope for opening values: the selected frame's, or the pause's. */
    private PausedSnapshot currentScope() {
        return scope != null ? scope : controller.getSession().getPausedSnapshot();
    }

    private static XdmValue byDisplayName(Map<QName, XdmValue> variables, String display) {
        if (variables == null) {
            return null;
        }
        for (Map.Entry<QName, XdmValue> e : variables.entrySet()) {
            QName q = e.getKey();
            String shown = q.getPrefix() == null || q.getPrefix().isEmpty()
                    ? q.getLocalName() : q.getPrefix() + ":" + q.getLocalName();
            if (shown.equals(display)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Re-evaluates a watch to its full value (the table keeps only the rendered, capped text)
     * off the FX thread and hands the result to {@code then} on the FX thread.
     */
    private void resolveWatch(WatchView.WatchRow row, Consumer<ValueSource> then) {
        PausedSnapshot snap = currentScope();
        WatchEvaluator evaluator = snap == null ? null : controller.getWatchEvaluator();
        if (evaluator == null) {
            then.accept(new ValueSource(row.getExpression(), "", "WATCH", null, row.getValue(), snap));
            return;
        }
        FxtGui.executorService.submit(() -> {
            ValueSource source;
            try {
                XdmValue value = evaluator.evaluateValue(row.getExpression(), snap, null);
                source = new ValueSource(row.getExpression(), WatchEvaluator.describeType(value), "WATCH",
                        value, null, snap);
            } catch (SaxonApiException | RuntimeException e) {
                source = new ValueSource(row.getExpression(), "", "WATCH", null,
                        "ERROR: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), snap);
            }
            final ValueSource resolved = source;
            Platform.runLater(() -> then.accept(resolved));
        });
    }

    /** Opens a viewer for {@code source}, or brings an open one for the same row and pause to front. */
    void openViewer(ValueSource source) {
        for (ValueViewerWindow open : viewers) {
            if (open.getSource().title().equals(source.title())
                    && open.getSource().snapshot() == source.snapshot()) {
                open.toFront();
                open.requestFocus();
                return;
            }
        }
        WatchEvaluator evaluator = source.snapshot() == null ? null : controller.getWatchEvaluator();
        ValueViewerWindow window = new ValueViewerWindow(
                getScene() == null ? null : getScene().getWindow(), source, evaluator);
        viewers.add(window);
        window.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> viewers.remove(window));
        window.show();
    }

    /** Copies the full value (pretty-printed) of {@code source} to the clipboard. */
    private void copyValue(ValueSource source) {
        WatchEvaluator evaluator = source.snapshot() == null ? null : controller.getWatchEvaluator();
        if (!source.hasValue() || evaluator == null) {
            putOnClipboard(source.hasValue()
                    ? WatchEvaluator.render(source.value(), WatchEvaluator.MAX_VIEWER_CHARS)
                    : source.fallbackText());
            return;
        }
        FxtGui.executorService.submit(() -> {
            String text = evaluator.serialize(source.value(), true);
            Platform.runLater(() -> putOnClipboard(text));
        });
    }

    private static void putOnClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Closes every open value viewer. */
    void closeViewers() {
        for (ValueViewerWindow window : new ArrayList<>(viewers)) {
            window.close();
        }
        viewers.clear();
    }

    /** The open value viewers (tests). */
    List<ValueViewerWindow> openViewers() {
        return List.copyOf(viewers);
    }

    // ----- tab groups: badges, attention, divider -------------------------------------------

    private static Label badge() {
        Label label = new Label();
        label.getStyleClass().add("fxt-output-badge");
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    /** Wraps the tab's FXML icon together with its count badge into one graphic. */
    private static void decorateWithBadge(Tab tab, Label badge) {
        Node icon = tab.getGraphic();
        HBox graphic = icon == null ? new HBox(badge) : new HBox(icon, badge);
        graphic.setSpacing(4);
        graphic.setAlignment(Pos.CENTER_LEFT);
        tab.setGraphic(graphic);
    }

    private static void updateBadge(Label badge, int count) {
        badge.setText(Integer.toString(count));
        badge.setVisible(count > 0);
        badge.setManaged(count > 0);
    }

    private void onMessageCountChanged() {
        int count = messagesView.getRowCount();
        updateBadge(messagesBadge, count);
        boolean inFront = rightTabs.getSelectionModel().getSelectedItem() == messagesTab;
        if (count > 0 && !inFront) {
            if (!messagesTab.getStyleClass().contains(UPDATED_CLASS)) {
                messagesTab.getStyleClass().add(UPDATED_CLASS);
            }
        } else {
            messagesTab.getStyleClass().remove(UPDATED_CLASS);
        }
    }

    /**
     * Restores the persisted divider once the split has a width (positions set before the
     * skin has laid out are clamped away) and saves it after a drag — on mouse release, so
     * the clamps the layout applies on its own (minimum widths, resizes) never write.
     */
    private void installDividerPersistence() {
        panels.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> saveDividerIfMoved());
        restoreDivider();
    }

    private void restoreDivider() {
        Platform.runLater(() -> {
            if (panels.getWidth() <= 0) {
                panels.widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
                    @Override
                    public void changed(javafx.beans.value.ObservableValue<? extends Number> obs,
                                        Number oldValue, Number newValue) {
                        panels.widthProperty().removeListener(this);
                        restoreDivider();
                    }
                });
                return;
            }
            applyDivider(storedDividerOrDefault());
        });
    }

    /** Positions the divider at {@code ratio} (clamped) without triggering a save. */
    void applyDivider(double ratio) {
        knownDivider = clampDivider(ratio);
        panels.setDividerPositions(knownDivider);
    }

    /** @return {@code ratio} within [{@value #MIN_DIVIDER}, {@value #MAX_DIVIDER}]; the default for NaN. */
    static double clampDivider(double ratio) {
        if (Double.isNaN(ratio)) {
            return DEFAULT_DIVIDER;
        }
        return Math.max(MIN_DIVIDER, Math.min(MAX_DIVIDER, ratio));
    }

    /** @return the persisted divider ratio (clamped), or the default when none / unreadable. */
    static double storedDividerOrDefault() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(DIVIDER_KEY);
            if (value != null && !value.isBlank()) {
                return clampDivider(Double.parseDouble(value.trim()));
            }
        } catch (Throwable ignored) {
            // properties service unavailable or value unreadable — use the default
        }
        return DEFAULT_DIVIDER;
    }

    private void saveDividerIfMoved() {
        double[] positions = panels.getDividerPositions();
        if (positions.length == 0 || panels.getWidth() <= 0) {
            return;
        }
        double ratio = clampDivider(positions[0]);
        if (Math.abs(ratio - knownDivider) < 0.005) {
            return;
        }
        knownDivider = ratio;
        try {
            ServiceRegistry.get(PropertiesService.class)
                    .set(DIVIDER_KEY, String.format(Locale.ROOT, "%.3f", ratio));
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
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
    /** Left tab group: Variables | Watch. */
    public TabPane getLeftTabs() { return leftTabs; }
    /** Right tab group: Call Stack | Messages | Breakpoints. */
    public TabPane getRightTabs() { return rightTabs; }
    public Tab getMessagesTab() { return messagesTab; }
    public Tab getBreakpointsTab() { return breakpointsTab; }
    /** The split holding the two tab groups. */
    public SplitPane getPanels() { return panels; }
    /** @return the text of the Messages tab's count badge, or {@code null} while it is hidden. */
    public String getMessagesBadgeText() { return messagesBadge.isVisible() ? messagesBadge.getText() : null; }
    /** @return the text of the Breakpoints tab's count badge, or {@code null} while it is hidden. */
    public String getBreakpointsBadgeText() { return breakpointsBadge.isVisible() ? breakpointsBadge.getText() : null; }
    public String getStatusText() { return status.getText(); }
    public boolean isPauseOnEntrySelected() { return pauseOnEntry.isSelected(); }
}
