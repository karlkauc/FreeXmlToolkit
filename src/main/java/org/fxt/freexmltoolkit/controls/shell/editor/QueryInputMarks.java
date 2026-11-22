package org.fxt.freexmltoolkit.controls.shell.editor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shared.XPathSyntaxHighlighter;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.service.query.QueryError;
import org.fxt.freexmltoolkit.service.query.QueryErrors;
import org.fxt.freexmltoolkit.service.query.QueryLanguage;
import org.fxt.freexmltoolkit.service.query.QueryWarning;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;

/**
 * Syntax highlighting plus error marks for an XPath / XQuery input: while the user types, the query is
 * compiled (not run) 400 ms after the last change off the FX thread, and its static error (red wavy
 * underline) and compiler warnings (amber) are marked; a failed run's error is marked the same way until the
 * next edit. Resting the mouse on a mark shows its message. The first mark is reported to a status sink.
 */
final class QueryInputMarks {

    /** Delay between the last keystroke and the check. */
    static final javafx.util.Duration CHECK_DELAY = javafx.util.Duration.millis(400);

    /** One marked range of the query. */
    record Mark(int start, int end, boolean error, String title, String text) {
    }

    /** What the status line under the input shows: a severity and a one-line text, or {@link #NONE}. */
    record Status(Severity severity, String text) {
        static final Status NONE = new Status(Severity.NONE, "");
    }

    enum Severity { NONE, ERROR, WARNING, INFO }

    private final CodeArea area;
    private final Supplier<QueryLanguage> language;
    private final Supplier<XPathVersion> version;
    private final Supplier<String> xml;
    private final Consumer<Status> status;
    private final PauseTransition debounce = new PauseTransition(CHECK_DELAY);
    private final Popup popup = new Popup();
    private final Label popupTitle = new Label();
    private final Label popupText = new Label();
    private final VBox popupBox = new VBox(2, popupTitle, popupText);
    private final List<Mark> marks = new ArrayList<>();
    private boolean enabled = true;
    /** Runs a query that checked clean and says what it found (off the FX thread), or {@code null} for none. */
    private java.util.function.Function<String, Supplier<String>> preview;
    /** One preview at a time: a slow one (an XPath cannot be stopped) is never queued behind. */
    private final java.util.concurrent.atomic.AtomicBoolean previewRunning = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.ExecutorService PREVIEWS = java.util.concurrent.Executors
            .newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "query-preview");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
    /** The XQuery version of a run ({@code null} = 3.1): below 3.1 its compatibility check runs first. */
    private Supplier<org.fxt.freexmltoolkit.service.standards.XQueryVersion> xqueryVersion = () -> null;
    private int checkGeneration;

    /**
     * @param area     the query input
     * @param language the query language (XQuery area or XPath field / file type)
     * @param version  the XPath version of a run ({@code null} = 3.1)
     * @param xml      the document the query will run on (namespace suggestions); may return {@code null}
     * @param status   receives the status line text; nullable
     */
    QueryInputMarks(CodeArea area, Supplier<QueryLanguage> language, Supplier<XPathVersion> version,
                    Supplier<String> xml, Consumer<Status> status) {
        this.area = area;
        this.language = language;
        this.version = version;
        this.xml = xml;
        this.status = status == null ? s -> { } : status;
        area.textProperty().addListener((obs, oldText, newText) -> {
            marks.clear();
            restyle();
            this.status.accept(Status.NONE);
            if (enabled) {
                debounce.playFromStart();
            }
        });
        debounce.setOnFinished(e -> check());

        popupTitle.getStyleClass().add("fxt-debug-hover-title");
        popupText.getStyleClass().add("fxt-debug-hover-value");
        popupText.setWrapText(true);
        popupText.setMaxWidth(520);
        popupBox.getStyleClass().addAll("root", "fxt-debug-hover");
        popup.getContent().add(popupBox);
        popup.setAutoHide(true);
        area.setMouseOverTextDelay(Duration.ofMillis(400));
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, this::onHover);
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> popup.hide());
    }

    /**
     * Sets the preview: after a check without findings it is called on the FX thread with the query and returns the
     * work (or {@code null} for none), which runs off the FX thread; its text (e.g. "12 hits") is shown as
     * information, a {@code null} text shows nothing.
     */
    void setPreview(java.util.function.Function<String, Supplier<String>> preview) {
        this.preview = preview;
    }

    /** Sets where the XQuery version of a run comes from (XQuery inputs). */
    void setXQueryVersion(Supplier<org.fxt.freexmltoolkit.service.standards.XQueryVersion> version) {
        xqueryVersion = version == null ? () -> null : version;
    }

    /** Turns the check while typing on or off (off for JSONPath); off also removes the marks. */
    void setEnabled(boolean on) {
        enabled = on;
        debounce.stop();
        checkGeneration++;
        if (!on) {
            marks.clear();
            restyle();
            status.accept(Status.NONE);
        }
    }

    /** Checks the current text now (after a mode switch or a document change). */
    void recheck() {
        if (enabled) {
            debounce.stop();
            check();
        }
    }

    /** Marks a failed run's error until the next edit. */
    void showError(QueryError error) {
        checkGeneration++; // an older check must not replace it
        debounce.stop();
        marks.clear();
        if (error != null) {
            Mark mark = mark(error);
            if (mark != null) {
                marks.add(mark);
            }
        }
        restyle();
        status.accept(Status.NONE);
    }

    /** Shows an information line under the input (e.g. why a query matched nothing). */
    void showInfo(String text) {
        status.accept(text == null || text.isBlank() ? Status.NONE : new Status(Severity.INFO, text));
    }

    /** @return the current marks (tests) */
    List<Mark> marks() {
        return List.copyOf(marks);
    }

    private void check() {
        String text = area.getText();
        int gen = ++checkGeneration;
        if (text == null || text.isBlank()) {
            return;
        }
        QueryLanguage lang = language.get();
        XPathVersion v = version.get();
        var xq = xqueryVersion.get();
        String document = xml.get();
        FxtGui.executorService.submit(() -> {
            QueryErrors.Check result = QueryErrors.check(text, lang, v, xq, document);
            Platform.runLater(() -> apply(gen, text, result));
        });
    }

    private void apply(int gen, String text, QueryErrors.Check result) {
        if (gen != checkGeneration || !text.equals(area.getText()) || !enabled) {
            return;
        }
        marks.clear();
        Status first = Status.NONE;
        if (result.error() != null) {
            Mark mark = mark(result.error());
            if (mark != null) {
                marks.add(mark);
            }
            first = new Status(Severity.ERROR, line(result.error()));
        }
        for (QueryWarning w : result.warnings()) {
            int offset = w.offset(text);
            if (offset >= 0) {
                int[] span = span(offset, w.length());
                marks.add(new Mark(span[0], span[1], false, w.code() == null ? "" : w.code(), w.message()));
            }
            if (first == Status.NONE) {
                String where = w.hasPosition() ? positionPrefix(text, w.line(), w.column()) : "";
                first = new Status(Severity.WARNING, where + w.message());
            }
        }
        restyle();
        if (first != Status.NONE) {
            // without findings the line keeps what it shows (cleared on every edit; a run's information stays)
            status.accept(first);
        } else {
            startPreview(gen, text);
        }
    }

    private void startPreview(int gen, String text) {
        Supplier<String> work = preview == null ? null : preview.apply(text);
        if (work == null || !previewRunning.compareAndSet(false, true)) {
            return;
        }
        PREVIEWS.submit(() -> {
            String found;
            try {
                found = work.get();
            } catch (RuntimeException e) {
                found = null;
            } finally {
                previewRunning.set(false);
            }
            String shown = found;
            Platform.runLater(() -> {
                if (shown != null && gen == checkGeneration && text.equals(area.getText()) && enabled) {
                    status.accept(new Status(Severity.INFO, shown));
                }
            });
        });
    }

    private static String line(QueryError e) {
        StringBuilder b = new StringBuilder();
        if (e.code() != null) {
            b.append(e.code()).append(" · ");
        }
        if (e.hasPosition()) {
            b.append(e.positionLabel()).append(": ");
        }
        return b.append(e.message()).toString();
    }

    private static String positionPrefix(String text, int line, int column) {
        return (text.indexOf('\n') < 0
                ? org.fxt.freexmltoolkit.i18n.I18n.t("QueryError.column", column)
                : org.fxt.freexmltoolkit.i18n.I18n.t("QueryError.line-column", line, column)) + ": ";
    }

    private Mark mark(QueryError e) {
        int offset = e.offset();
        if (offset < 0 || !e.query().equals(area.getText())) {
            return null;
        }
        int[] span = span(offset, e.length());
        String title = (e.code() == null ? "" : e.code() + " · ") + e.kind().label();
        String text = e.hint() == null ? e.message() : e.message() + "\n\n" + e.hint();
        return new Mark(span[0], span[1], true, title, text);
    }

    /** A non-empty range inside the text (an error at the very end marks the last character). */
    private int[] span(int offset, int length) {
        int n = area.getLength();
        if (n == 0) {
            return new int[] {0, 0};
        }
        int start = Math.min(offset, n - 1);
        int end = Math.min(n, Math.max(start + 1, offset + length));
        return new int[] {start, end};
    }

    private void restyle() {
        String text = area.getText();
        area.setStyleSpans(0, XPathSyntaxHighlighter.computeHighlighting(text));
        for (Mark m : marks) {
            if (m.start() >= m.end() || m.end() > area.getLength()) {
                continue;
            }
            String cls = m.error() ? "fxt-query-error" : "fxt-query-warning";
            StyleSpans<Collection<String>> spans = area.getStyleSpans(m.start(), m.end());
            area.setStyleSpans(m.start(), spans.mapStyles(styles -> {
                List<String> merged = new ArrayList<>(styles);
                merged.add(cls);
                return merged;
            }));
        }
    }

    private void onHover(MouseOverTextEvent e) {
        int index = e.getCharacterIndex();
        for (Mark m : marks) {
            if (index >= m.start() && index < m.end()) {
                popupTitle.setText(m.title());
                popupText.setText(m.text());
                showPopup(e.getScreenPosition().getX(), e.getScreenPosition().getY() + 14);
                return;
            }
        }
    }

    private void showPopup(double x, double y) {
        if (area.getScene() == null || area.getScene().getWindow() == null) {
            return;
        }
        popupBox.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        popupBox.getStyleClass().add(ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (popupBox.getStylesheets().isEmpty()) {
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                var url = QueryInputMarks.class.getResource(sheet);
                if (url != null) {
                    popupBox.getStylesheets().add(url.toExternalForm());
                }
            }
        }
        ThemeManager.syncAppearanceSheet(popupBox);
        popup.show(area.getScene().getWindow(), x, y);
    }
}
