package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmValue;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.debugger.HoverExpressions;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;

/**
 * Value tooltips in a stylesheet editor while the debugger is paused: resting the mouse on a
 * variable reference ({@code $total}) — or inside a selected XPath — evaluates it in the
 * selected frame's scope and shows the value in a small popup, with a link that opens it in the
 * value viewer. Installed on every debug module's editor for the session and removed with it.
 */
public final class DebugHover {

    /** Longest value shown in the popup (the viewer shows the rest). */
    static final int MAX_POPUP_CHARS = 300;

    private final CodeArea area;
    private final Supplier<PausedSnapshot> scope;
    private final Supplier<WatchEvaluator> evaluator;
    private final Consumer<ValueSource> openViewer;
    private final Popup popup = new Popup();
    private final VBox box = new VBox(2);
    private final Label title = new Label();
    private final Label value = new Label();
    private final Hyperlink open = new Hyperlink("Open in value viewer");
    private final PauseTransition hideDelay = new PauseTransition(javafx.util.Duration.millis(500));
    private final EventHandler<MouseOverTextEvent> onBegin = this::onBegin;
    private final EventHandler<MouseOverTextEvent> onEnd = e -> hideDelay.playFromStart();
    private ValueSource shown;
    private int generation;

    /**
     * @param scope      the scope to evaluate in (the selected frame), {@code null} while not paused
     * @param evaluator  the evaluator of the debugged run
     * @param openViewer opens a value in the viewer window
     */
    public DebugHover(CodeArea area, Supplier<PausedSnapshot> scope, Supplier<WatchEvaluator> evaluator,
                      Consumer<ValueSource> openViewer) {
        this.area = area;
        this.scope = scope;
        this.evaluator = evaluator;
        this.openViewer = openViewer;
        title.getStyleClass().add("fxt-debug-hover-title");
        value.getStyleClass().add("fxt-debug-hover-value");
        value.setWrapText(true);
        value.setMaxWidth(520);
        open.setFocusTraversable(false);
        open.setOnAction(e -> {
            popup.hide();
            if (shown != null) {
                this.openViewer.accept(shown);
            }
        });
        box.getChildren().addAll(title, value, open);
        box.getStyleClass().addAll("root", "fxt-debug-hover");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        box.setOnMouseEntered(e -> hideDelay.stop());
        box.setOnMouseExited(e -> hideDelay.playFromStart());
        hideDelay.setOnFinished(e -> popup.hide());
    }

    /** Starts listening to the editor (mouse rests 500 ms on text). */
    public DebugHover install() {
        area.setMouseOverTextDelay(Duration.ofMillis(500));
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, onBegin);
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, onEnd);
        return this;
    }

    /** Stops listening and hides the popup (session end). */
    public void uninstall() {
        area.removeEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, onBegin);
        area.removeEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, onEnd);
        area.setMouseOverTextDelay(null);
        hideDelay.stop();
        popup.hide();
    }

    private void onBegin(MouseOverTextEvent e) {
        String expression = expressionAt(e.getCharacterIndex());
        if (expression == null) {
            return;
        }
        hideDelay.stop();
        evaluate(expression, text -> show(e.getScreenPosition().getX(), e.getScreenPosition().getY() + 14));
    }

    /**
     * @return the expression under {@code index}: the selection when it contains the index,
     *         else a {@code $variable} there; {@code null} when there is nothing to evaluate or
     *         the debugger is not paused
     */
    String expressionAt(int index) {
        if (scope.get() == null) {
            return null;
        }
        var selection = area.getSelection();
        if (selection.getLength() > 0 && index >= selection.getStart() && index < selection.getEnd()) {
            String selected = area.getSelectedText().strip();
            return selected.isEmpty() || selected.length() > 2000 ? null : selected;
        }
        return HoverExpressions.variableAt(area.getText(), index).orElse(null);
    }

    /**
     * Evaluates {@code expression} off the FX thread and fills the popup content; {@code then}
     * runs on the FX thread with the shown text.
     */
    void evaluate(String expression, Consumer<String> then) {
        PausedSnapshot snapshot = scope.get();
        WatchEvaluator eval = evaluator.get();
        if (snapshot == null || eval == null) {
            return;
        }
        int gen = ++generation;
        FxtGui.executorService.submit(() -> {
            ValueSource source;
            String text;
            try {
                XdmValue result = eval.evaluateValue(expression, snapshot, null);
                String type = WatchEvaluator.describeType(result);
                source = new ValueSource(expression, type, "HOVER", result, null, snapshot);
                text = WatchEvaluator.render(result, MAX_POPUP_CHARS) + "\n" + type;
            } catch (SaxonApiException | RuntimeException ex) {
                String message = "ERROR: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                source = new ValueSource(expression, "", "HOVER", null, message, snapshot);
                text = message;
            }
            final ValueSource result = source;
            final String shownText = text;
            Platform.runLater(() -> {
                if (gen != generation) {
                    return;
                }
                shown = result;
                title.setText(expression);
                value.setText(shownText);
                open.setVisible(result.hasValue());
                open.setManaged(result.hasValue());
                then.accept(shownText);
            });
        });
    }

    private void show(double x, double y) {
        box.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        box.getStyleClass().add(ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (area.getScene() != null && area.getScene().getWindow() != null) {
            if (box.getStylesheets().isEmpty()) {
                for (String sheet : new String[]{"/css/design-tokens.css", "/css/unified-shell.css"}) {
                    var url = DebugHover.class.getResource(sheet);
                    if (url != null) {
                        box.getStylesheets().add(url.toExternalForm());
                    }
                }
            }
            popup.show(area.getScene().getWindow(), x, y);
        }
    }

    /** Shows the popup at the given screen position (tests use it after {@link #evaluate}). */
    void showAt(double x, double y) {
        show(x, y);
    }

    /** The popup's content box (tests). */
    VBox popupContent() {
        return box;
    }

    /** The popup's value text (tests). */
    String shownValue() {
        return value.getText();
    }
}
