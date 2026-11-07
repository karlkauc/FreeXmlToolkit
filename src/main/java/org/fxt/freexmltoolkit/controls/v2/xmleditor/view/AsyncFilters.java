package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javafx.application.Platform;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Evaluates grid filters over large contexts off the FX thread, like {@link AsyncFormulas}: a
 * filter on the root of a 150-MB document ran its XQuery over 4 million nodes on the FX thread on
 * every grid rebuild. A filter is now evaluated once per model state; while the worker runs the
 * grid keeps the previous result of the same filter (or shows the rows unfiltered) and marks the
 * filter line as pending.
 *
 * <p>The worker only reads the model while the FX thread may edit it; a result is kept only when
 * the command manager's revision and the document are unchanged once it is back on the FX thread.
 */
final class AsyncFilters {

    /**
     * The state of one filter.
     *
     * @param matches the matching nodes ({@code null}: none known yet, the rows stay unfiltered)
     * @param error   the evaluation error, or {@code null}
     * @param pending whether a newer result is being computed
     */
    record Outcome(Set<XmlNode> matches, String error, boolean pending) {
    }

    private record Cached(long revision, Object document, Set<XmlNode> matches, String error) {
    }

    /** Per context element (weak) and expression. */
    private final Map<XmlElement, Map<String, Cached>> results = new WeakHashMap<>();
    private final Map<XmlElement, Map<String, Long>> running = new WeakHashMap<>();
    private final LongSupplier revision;
    private final Supplier<Object> document;
    private final Executor executor;
    private Runnable onReady = () -> { };

    AsyncFilters(LongSupplier revision, Supplier<Object> document, Executor executor) {
        this.revision = revision;
        this.document = document;
        this.executor = executor;
    }

    /** @param onReady runs on the FX thread when a result arrived (the grid rebuilds its rows) */
    void setOnReady(Runnable onReady) {
        this.onReady = onReady != null ? onReady : () -> { };
    }

    /** @return the filter's current outcome; FX thread */
    Outcome evaluate(XmlElement context, String expression) {
        long rev = revision.getAsLong();
        Object doc = document.get();
        Cached cached = results.computeIfAbsent(context, c -> new HashMap<>()).get(expression);
        if (cached != null && cached.revision() == rev && cached.document() == doc) {
            return new Outcome(cached.matches(), cached.error(), false);
        }
        if (!AsyncFormulas.larger(context, AsyncFormulas.ASYNC_NODES)) {
            Cached now = compute(context, expression, rev, doc);
            results.get(context).put(expression, now);
            return new Outcome(now.matches(), now.error(), false);
        }
        Map<String, Long> runs = running.computeIfAbsent(context, c -> new HashMap<>());
        Long started = runs.get(expression);
        if (started == null || started != rev) {
            start(context, expression, rev, doc);
        }
        // The previous result of this filter (an edit since) is closer than no filter at all
        boolean stale = cached != null && cached.document() == doc && cached.error() == null;
        return new Outcome(stale ? cached.matches() : null, null, true);
    }

    private void start(XmlElement context, String expression, long rev, Object doc) {
        running.get(context).put(expression, rev);
        Runnable work = () -> {
            Cached value;
            try {
                value = compute(context, expression, rev, doc);
            } catch (RuntimeException | StackOverflowError e) {
                value = null; // an edit raced the read; the revision check below asks again
            }
            Cached result = value;
            Platform.runLater(() -> {
                Map<String, Long> runs = running.get(context);
                if (runs != null && runs.get(expression) != null && runs.get(expression) == rev) {
                    runs.remove(expression);
                }
                if (revision.getAsLong() == rev && document.get() == doc) {
                    // Failed without an edit meanwhile: not a race, so an error (else the rebuild
                    // below would start the same failing run again and again)
                    results.computeIfAbsent(context, c -> new HashMap<>()).put(expression, result != null
                            ? result : new Cached(rev, doc, null, "The filter could not be evaluated"));
                }
                onReady.run();
            });
        };
        try {
            executor.execute(work);
        } catch (RejectedExecutionException e) {
            running.get(context).remove(expression);
            results.get(context).put(expression, compute(context, expression, rev, doc));
        }
    }

    private static Cached compute(XmlElement context, String expression, long rev, Object doc) {
        try {
            return new Cached(rev, doc, XmlGridQuery.evaluate(context, expression).nodes(), null);
        } catch (IllegalArgumentException e) {
            return new Cached(rev, doc, null, e.getMessage());
        }
    }
}
