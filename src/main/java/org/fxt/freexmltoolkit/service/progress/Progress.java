package org.fxt.freexmltoolkit.service.progress;

/**
 * What a long-running job reports while it runs: what it is doing now, how far it is, and whether the user wants it
 * to stop. UI-free and thread-safe; the panels show it through {@code PanelJobs} / {@code PanelProgress}, the command
 * line on stderr, everything else passes {@link #NONE}.
 *
 * <p>A job asks {@link #checkCancelled()} between its steps (a request, a record, a phase) and lets the
 * {@link CancelledException} end it; what it finished before stays usable.
 */
public interface Progress {

    /** Reports nothing and is never cancelled. */
    Progress NONE = new Progress() {
        @Override
        public void message(String text) {
        }

        @Override
        public void progress(long done, long total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /** A remark the UI turns into an action (a link), not just text. */
    enum Hint {
        /** OpenFIGI without an API key will take long: a key makes it about ten times faster. */
        OPENFIGI_API_KEY
    }

    /** @param text what runs now, in the user's language (one line) */
    void message(String text);

    /**
     * @param done  steps finished
     * @param total all steps, {@code <= 0} when unknown (indeterminate)
     */
    void progress(long done, long total);

    /** @return whether the user asked the job to stop */
    boolean isCancelled();

    /** Shows a hint once (default: ignored). */
    default void hint(Hint hint) {
    }

    /**
     * Says whether the current step can be stopped (default: ignored). A step that cannot - one XSLT over a whole
     * document - turns it off, so the UI hides Cancel instead of offering a button that does nothing.
     */
    default void setCancellable(boolean cancellable) {
    }

    /**
     * Runs {@code action} when the job is cancelled (at once when it already is) - for work that cannot ask, such as
     * an external process. Default: never.
     */
    default void onCancel(Runnable action) {
    }

    /** @throws CancelledException when the user asked the job to stop */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new CancelledException();
        }
    }

    /**
     * Waits, but ends the wait as soon as the job is cancelled.
     *
     * @throws CancelledException when cancelled before or during the wait
     */
    default void sleep(long millis) {
        long end = System.currentTimeMillis() + millis;
        long left;
        while ((left = end - System.currentTimeMillis()) > 0) {
            checkCancelled();
            try {
                Thread.sleep(Math.min(left, 100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException(e);
            }
        }
        checkCancelled();
    }
}
