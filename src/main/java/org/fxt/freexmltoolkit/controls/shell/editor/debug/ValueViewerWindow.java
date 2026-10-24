package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmValue;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shared.CodeAreaFontZoom;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.editor.ResultHighlighter;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.SourceLocations;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;

/**
 * Non-modal window of the XSLT debugger that shows one variable or watch value in full: the
 * complete serialization (pretty-printed by default) in a read-only, syntax-highlighted code area
 * with Copy and Wrap, and an XPath field that evaluates against that value — a single item is the
 * context item, {@code $value} is the whole value, and every variable of the pause is in scope
 * ({@link WatchEvaluator#evaluateValue}). Serialization and XPath run off the FX thread.
 * Opened, tracked and closed by {@link XsltDebugPane}.
 */
public final class ValueViewerWindow extends Stage {

    private static final String[] SHELL_SHEETS = {
            "/css/design-tokens.css", "/css/app-theme.css", "/css/fxt-theme.css", "/css/unified-shell.css"};
    private static final String ERROR_CLASS = "fxt-value-viewer-error";

    private final ValueSource source;
    private final WatchEvaluator evaluator;
    private final CodeArea valueArea = new CodeArea();
    private final CodeArea resultArea = new CodeArea();
    private final Consumer<DesignTokens.Theme> themeListener;
    /** Bumped per render / XPath run so a slow earlier pass cannot overwrite a newer one. */
    private int renderGeneration;
    private int xpathGeneration;

    @FXML private Label nameLabel;
    @FXML private Label typeLabel;
    @FXML private Label pauseLabel;
    @FXML private Label sizeLabel;
    @FXML private SplitPane split;
    @FXML private VBox valueBox;
    @FXML private VBox resultBox;
    @FXML private Button copyButton;
    @FXML private ToggleButton prettyToggle;
    @FXML private ToggleButton wrapToggle;
    @FXML private TextField xpathField;
    @FXML private Button runButton;
    @FXML private Button copyResultButton;
    @FXML private Label resultStatus;

    /**
     * @param owner     the main window (may be {@code null} in tests)
     * @param source    what to show
     * @param evaluator the evaluator bound to the debugged run's processor; {@code null}
     *                  disables Pretty print for nodes and the XPath field
     */
    public ValueViewerWindow(Window owner, ValueSource source, WatchEvaluator evaluator) {
        this.source = source;
        this.evaluator = evaluator;
        if (owner != null) {
            initOwner(owner);
        }
        initModality(Modality.NONE);
        setResizable(true);
        setTitle(source.title() + (source.type() == null || source.type().isBlank() ? "" : " — " + source.type()));

        Parent root = FxmlViews.loadContent(this, "/pages/dialogs/ValueViewerWindow.fxml");
        for (String sheet : SHELL_SHEETS) {
            var url = ValueViewerWindow.class.getResource(sheet);
            if (url != null) {
                root.getStylesheets().add(url.toExternalForm());
            }
        }
        Scene scene = new Scene(root, 760, 560);
        setScene(scene);
        ThemeManager.applyTo(scene, ThemeManager.currentIsDark());
        themeListener = theme -> Platform.runLater(
                () -> ThemeManager.applyTo(scene, theme == DesignTokens.Theme.DARK));
        ThemeManager.addThemeChangeListener(themeListener);
        setOnHidden(e -> ThemeManager.removeThemeChangeListener(themeListener));

        setUpArea(valueArea, valueBox, "Loading…");
        setUpArea(resultArea, resultBox, "Run an XPath to see its result here.");
        fillHeader();

        boolean canQuery = evaluator != null && source.snapshot() != null;
        xpathField.setDisable(!canQuery);
        runButton.setDisable(!canQuery);
        if (!canQuery) {
            resultStatus.setText("XPath is available while the debugger has a paused state.");
        }
        prettyToggle.setDisable(!source.hasValue() || evaluator == null);
        render();
    }

    private static void setUpArea(CodeArea area, VBox box, String placeholder) {
        area.setEditable(false);
        area.setWrapText(false);
        area.setPlaceholder(new Label(placeholder));
        area.getStyleClass().addAll("fxt-transform-output", "fxt-query-results");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        CodeAreaFontZoom.install(area);
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(area);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        box.getChildren().add(scroll);
    }

    private void fillHeader() {
        nameLabel.setText(source.title());
        String type = source.type() == null ? "" : source.type();
        String scope = source.scope() == null ? "" : source.scope();
        typeLabel.setText(type.isEmpty() ? scope : scope.isEmpty() ? type : type + "  ·  " + scope);
        PausedSnapshot snap = source.snapshot();
        if (source.location() != null) {
            pauseLabel.setText(source.location());
        } else if (snap != null) {
            String where = snap.hasFile() ? SourceLocations.fileName(snap.systemId()) + ":" : "line ";
            pauseLabel.setText("paused at " + where + snap.lineNumber());
        }
    }

    // ----- value ------------------------------------------------------------------------

    private void render() {
        final boolean pretty = prettyToggle.isSelected();
        final int gen = ++renderGeneration;
        if (!source.hasValue() || evaluator == null) {
            String text = source.hasValue()
                    ? WatchEvaluator.render(source.value(), WatchEvaluator.MAX_VIEWER_CHARS)
                    : source.fallbackText() == null ? "" : source.fallbackText();
            showValue(text, source.hasValue() ? source.value().size() : -1);
            return;
        }
        XdmValue value = source.value();
        FxtGui.executorService.submit(() -> {
            String text = evaluator.serialize(value, pretty);
            Platform.runLater(() -> {
                if (gen == renderGeneration) {
                    showValue(text, value.size());
                }
            });
        });
    }

    private void showValue(String text, int items) {
        valueArea.replaceText(text);
        highlight(valueArea, text);
        valueArea.moveTo(0);
        valueArea.requestFollowCaret();
        String chars = String.format(Locale.ROOT, "%,d chars", text.length());
        sizeLabel.setText(items > 1 ? items + " items  ·  " + chars : chars);
    }

    private static void highlight(CodeArea area, String text) {
        if (text.isEmpty() || text.length() > ResultHighlighter.HIGHLIGHT_LIMIT_CHARS) {
            return;
        }
        String trimmed = text.stripLeading();
        ResultHighlighter.highlight(area, text, trimmed.startsWith("<"),
                trimmed.startsWith("{") || trimmed.startsWith("["));
    }

    @FXML
    public void onCopy() {
        copyToClipboard(valueArea.getText());
    }

    @FXML
    public void onPrettyPrint() {
        render();
    }

    @FXML
    public void onWrap() {
        valueArea.setWrapText(wrapToggle.isSelected());
        resultArea.setWrapText(wrapToggle.isSelected());
    }

    // ----- XPath ------------------------------------------------------------------------

    @FXML
    public void onRunXPath() {
        runXPath(xpathField.getText());
    }

    /** Evaluates {@code expression} against the shown value (off the FX thread). */
    public void runXPath(String expression) {
        if (evaluator == null || source.snapshot() == null || expression == null || expression.isBlank()) {
            return;
        }
        final boolean pretty = prettyToggle.isSelected();
        final int gen = ++xpathGeneration;
        resultStatus.getStyleClass().remove(ERROR_CLASS);
        resultStatus.setText("Running…");
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String text;
            String status;
            boolean error = false;
            try {
                XdmValue result = evaluator.evaluateValue(expression, source.snapshot(), source.value());
                long ms = (System.nanoTime() - start) / 1_000_000;
                text = evaluator.serialize(result, pretty);
                int n = result.size();
                status = n + (n == 1 ? " item" : " items") + "  ·  " + ms + " ms";
            } catch (SaxonApiException | RuntimeException e) {
                text = "ERROR: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                status = "XPath error";
                error = true;
            }
            final String shown = text;
            final String shownStatus = status;
            final boolean failed = error;
            Platform.runLater(() -> {
                if (gen != xpathGeneration) {
                    return;
                }
                resultArea.replaceText(shown);
                if (!failed) {
                    highlight(resultArea, shown);
                }
                resultStatus.setText(shownStatus);
                if (failed) {
                    resultStatus.getStyleClass().add(ERROR_CLASS);
                }
            });
        });
    }

    @FXML
    public void onCopyResult() {
        copyToClipboard(resultArea.getText());
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    // ----- accessors (XsltDebugPane / tests) ----------------------------------------------

    public ValueSource getSource() { return source; }
    public String getValueText() { return valueArea.getText(); }
    public String getResultText() { return resultArea.getText(); }
    public String getResultStatus() { return resultStatus.getText(); }
    public String getSizeText() { return sizeLabel.getText(); }

    /** The style classes of the value area's spans (tests: highlighting arrived). */
    Set<String> valueStyleClassesForTest() {
        Set<String> classes = new LinkedHashSet<>();
        valueArea.getStyleSpans(0, valueArea.getLength())
                .forEach(span -> classes.addAll(span.getStyle()));
        return classes;
    }

    ToggleButton prettyToggle() { return prettyToggle; }
}
