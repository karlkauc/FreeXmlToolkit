package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javafx.application.Platform;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;

/**
 * Evaluates grid formulas over large contexts off the FX thread. A formula over a whole 150-MB
 * document (sum over 680,000 records) took 12 s inside the grid's layout, and again after every
 * rebuild; it now shows a placeholder until the worker is done and is computed once per model state.
 *
 * <p>The worker only reads the model while the FX thread may edit it. Edits are counted by the
 * command manager ({@code revision}); a result is kept only when the count and the document are
 * unchanged once it is back on the FX thread, otherwise the formula is evaluated again on the next
 * request. Submitting to the executor orders every earlier FX-thread write before the read.
 */
final class AsyncFormulas {

    /** Contexts with more nodes than this are evaluated off the FX thread. */
    static final int ASYNC_NODES = 20_000;

    /** Result shown while the evaluation runs. */
    static final String PENDING = "…";

    private record Cached(long revision, Object document, GridFormulas.Evaluated value) {
    }

    private final Map<XmlProcessingInstruction, Cached> results = new WeakHashMap<>();
    private final Map<XmlProcessingInstruction, Long> running = new WeakHashMap<>();
    private final LongSupplier revision;
    private final Supplier<Object> document;
    private final Executor executor;
    private Runnable onReady = () -> { };

    AsyncFormulas(LongSupplier revision, Supplier<Object> document, Executor executor) {
        this.revision = revision;
        this.document = document;
        this.executor = executor;
    }

    /** @param onReady runs on the FX thread when a result arrived (the grid re-lays out its formula rows) */
    void setOnReady(Runnable onReady) {
        this.onReady = onReady != null ? onReady : () -> { };
    }

    /**
     * @return the evaluated formula, a pending one (result {@link #PENDING}) while a worker computes
     *         it, or the synchronous result for a small context. FX thread.
     */
    GridFormulas.Evaluated evaluate(XmlProcessingInstruction pi) {
        long rev = revision.getAsLong();
        Object doc = document.get();
        Cached cached = results.get(pi);
        if (cached != null && cached.revision() == rev && cached.document() == doc) {
            return cached.value();
        }
        if (!(pi.getParent() instanceof XmlElement context) || !larger(context, ASYNC_NODES)) {
            GridFormulas.Evaluated value = GridFormulas.evaluate(pi);
            results.put(pi, new Cached(rev, doc, value));
            return value;
        }
        Long started = running.get(pi);
        if (started == null || started != rev) {
            start(pi, rev, doc);
        }
        return new GridFormulas.Evaluated(GridFormulas.parse(pi.getData()), PENDING, null);
    }

    private void start(XmlProcessingInstruction pi, long rev, Object doc) {
        running.put(pi, rev);
        Runnable work = () -> {
            GridFormulas.Evaluated value;
            try {
                value = GridFormulas.evaluate(pi);
            } catch (RuntimeException | StackOverflowError e) {
                value = null; // an edit raced the read; the revision check below asks again
            }
            GridFormulas.Evaluated result = value;
            Platform.runLater(() -> {
                if (running.get(pi) != null && running.get(pi) == rev) {
                    running.remove(pi);
                }
                if (result != null && revision.getAsLong() == rev && document.get() == doc) {
                    results.put(pi, new Cached(rev, doc, result));
                }
                onReady.run();
            });
        };
        try {
            executor.execute(work);
        } catch (RejectedExecutionException e) {
            running.remove(pi);
            results.put(pi, new Cached(rev, doc, GridFormulas.evaluate(pi)));
        }
    }

    /** @return whether the subtree of {@code root} has more than {@code limit} nodes (stops counting there) */
    static boolean larger(XmlNode root, int limit) {
        java.util.ArrayDeque<XmlNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        int count = 0;
        while (!stack.isEmpty()) {
            XmlNode node = stack.pop();
            if (++count > limit) {
                return true;
            }
            if (node instanceof XmlElement element) {
                for (XmlNode child : element.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return false;
    }
}
