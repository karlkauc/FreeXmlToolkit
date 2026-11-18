package org.fxt.freexmltoolkit.service.progress;

import java.util.ArrayList;
import java.util.List;

/**
 * Parts of one job that run side by side (GLEIF and the ISIN services): each part reports to its own
 * {@link Progress}; the parent shows the parts' messages joined with " · " and the sum of their steps.
 * Cancelling the parent cancels every part.
 */
public final class ProgressParts {

    private final Progress parent;
    /** Guards the arrays; private, so no caller can hold it. */
    private final Object lock = new Object();
    private final String[] messages;
    private final long[] done;
    private final long[] total;

    /** @param count number of parts */
    public ProgressParts(Progress parent, int count) {
        this.parent = parent;
        this.messages = new String[count];
        this.done = new long[count];
        this.total = new long[count];
    }

    /** @return the progress of part {@code index} */
    public Progress part(int index) {
        return new Progress() {
            @Override
            public void message(String text) {
                synchronized (lock) {
                    messages[index] = text;
                }
                publishMessage();
            }

            @Override
            public void progress(long d, long t) {
                synchronized (lock) {
                    done[index] = d;
                    total[index] = t;
                }
                publishProgress();
            }

            @Override
            public boolean isCancelled() {
                return parent.isCancelled();
            }

            @Override
            public void hint(Hint hint) {
                parent.hint(hint);
            }

            @Override
            public void onCancel(Runnable action) {
                parent.onCancel(action);
            }

            @Override
            public void setCancellable(boolean cancellable) {
                parent.setCancellable(cancellable);
            }
        };
    }

    /** Clears a finished part's message, so the others are shown alone. */
    public void finished(int index) {
        synchronized (lock) {
            messages[index] = null;
            done[index] = total[index];
        }
        publishMessage();
    }

    private void publishMessage() {
        List<String> shown = new ArrayList<>();
        synchronized (lock) {
            for (String m : messages) {
                if (m != null && !m.isBlank()) {
                    shown.add(m);
                }
            }
        }
        if (!shown.isEmpty()) {
            parent.message(String.join(" · ", shown));
        }
    }

    private void publishProgress() {
        long d = 0;
        long t = 0;
        synchronized (lock) {
            for (int i = 0; i < done.length; i++) {
                d += Math.max(0, done[i]);
                t += Math.max(0, total[i]);
            }
        }
        parent.progress(d, t);
    }
}
