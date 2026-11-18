package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.xslt.XsltDiagnostic;

/**
 * The check while typing of an XSLT tab: a pause in typing compiles the stylesheet (with the open modules' texts) off
 * the FX thread, marks the static errors and warnings of the stylesheet itself in the text and lists all of them —
 * those of included or imported modules with their module — in the PROBLEMS panel (source {@value #SOURCE}).
 */
final class XsltLiveCheck {

    /** Problem source of the check's findings. */
    static final String SOURCE = "XSLT";
    /** Setting: check stylesheets while typing (default on). */
    static final String SETTING = "xslt.liveCheck";
    /** Pause in typing before a check. */
    static final Duration DELAY = Duration.millis(800);
    /** Larger stylesheets are not checked while typing. */
    static final int MAX_CHARS = 2 * 1024 * 1024;

    /** One worker for all tabs: a check waits for the one before. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xslt-live-check");
        t.setDaemon(true);
        return t;
    });

    private final EditorHost host;
    private final EditorTab tab;
    private final Supplier<Map<String, String>> moduleTexts;
    private final PauseTransition pause = new PauseTransition(DELAY);
    private int generation;
    private List<XsltDiagnostic> last = List.of();

    private XsltLiveCheck(EditorHost host, EditorTab tab, Supplier<Map<String, String>> moduleTexts) {
        this.host = host;
        this.tab = tab;
        this.moduleTexts = moduleTexts;
        pause.setOnFinished(e -> check());
        tab.view.getCodeArea().textProperty().addListener((obs, oldText, newText) -> pause.playFromStart());
        tab.selectedProperty().addListener((obs, was, now) -> {
            if (now) {
                // after the host replaced the panel's rows with the newly active document's kept result
                Platform.runLater(this::publish);
            }
        });
        pause.playFromStart();
    }

    /**
     * Checks the tab while typing when it holds an XSLT stylesheet.
     *
     * @param moduleTexts the texts of the open stylesheets by normalized path (FX thread)
     */
    static void install(EditorHost host, EditorTab tab, Supplier<Map<String, String>> moduleTexts) {
        if (tab.document.getFileType() == EditorFileType.XSLT) {
            new XsltLiveCheck(host, tab, moduleTexts);
        }
    }

    /** @return whether stylesheets are checked while typing */
    static boolean enabled() {
        try {
            return !"false".equalsIgnoreCase(ServiceRegistry.get(PropertiesService.class).get(SETTING));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void check() {
        String text = tab.view.getText();
        if (!enabled() || text == null || text.isBlank() || text.length() > MAX_CHARS) {
            show(List.of(), ++generation);
            return;
        }
        int gen = ++generation;
        String systemId = tab.document.getPath() != null ? tab.document.getPath().toUri().toString() : null;
        Map<String, String> modules = new HashMap<>(moduleTexts.get());
        WORKER.submit(() -> {
            List<XsltDiagnostic> found;
            try {
                found = XsltTransformationEngine.getInstance().checkStylesheet(text, systemId, modules::get);
            } catch (RuntimeException e) {
                found = List.of();
            }
            List<XsltDiagnostic> result = found;
            Platform.runLater(() -> show(result, gen));
        });
    }

    private void show(List<XsltDiagnostic> found, int gen) {
        if (gen != generation) {
            return; // a newer check is on its way
        }
        String now = tab.view.getText();
        Map<Integer, Integer> marks = new HashMap<>();
        for (XsltDiagnostic d : found) {
            if (d.inMain() && d.offset() >= 0 && now != null && d.offset() + d.length() <= now.length()) {
                marks.put(d.offset(), Math.max(1, d.length()));
            }
        }
        tab.view.setCheckMarks(marks);
        last = found;
        if (tab.isSelected()) {
            publish();
        }
    }

    /** Replaces this check's entries in the PROBLEMS panel (other sources stay). */
    private void publish() {
        List<ValidationProblem> problems = new ArrayList<>();
        for (ValidationProblem p : host.getActiveProblems()) {
            if (!SOURCE.equals(p.source())) {
                problems.add(p);
            }
        }
        for (XsltDiagnostic d : last) {
            problems.add(problem(d));
        }
        if (!problems.equals(host.getActiveProblems())) {
            host.setActiveProblems(problems);
        }
    }

    /** A diagnostic as a PROBLEMS row: a module's finding names the module and has no line in this document. */
    static ValidationProblem problem(XsltDiagnostic d) {
        String message = d.inMain() ? d.message() : d.message() + " (" + d.positionLabel() + ")";
        if (d.hint() != null) {
            message = message + "\n" + d.hint();
        }
        return new ValidationProblem(SOURCE, d.warning() ? "warning" : "error", d.inMain() ? d.line() : 0, message,
                d.code(), null, List.of(), d.inMain() ? Math.max(0, d.column()) : 0);
    }
}
