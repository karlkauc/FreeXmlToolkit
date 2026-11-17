package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.progress.Progress;

/**
 * The long-running jobs of one side panel, shown in its {@link PanelProgress}: bar, what runs now, the time so far,
 * Cancel and hints. One job per id at a time ({@link #start} refuses a second). With several jobs the one started
 * last is shown, with "+N more"; when it ends the previous one is shown again.
 *
 * <p>Jobs report from any thread through their {@link Job} ({@link Progress}); the panel is redrawn from those
 * values four times a second on the FX thread, so a job may report as often as it likes. Create and use on the FX
 * thread.
 */
public final class PanelJobs {

    /** Refresh interval of the display. */
    static final Duration REFRESH = Duration.millis(250);

    /** Opens what a hint links to (set by the shell: the Settings page at the right card). */
    private static volatile Consumer<Progress.Hint> hintHandler;

    private final PanelProgress view;
    private final List<Job> jobs = new ArrayList<>();
    private final Timeline refresh = new Timeline(new KeyFrame(REFRESH, e -> render()));

    public PanelJobs(PanelProgress view) {
        this.view = view;
        refresh.setCycleCount(Timeline.INDEFINITE);
    }

    /** Sets what a hint's link opens (the shell sets it once). */
    public static void setHintHandler(Consumer<Progress.Hint> handler) {
        hintHandler = handler;
    }

    /**
     * Starts a job.
     *
     * @param id          what it is; a second job with the same id is refused while the first runs
     * @param label       the job's name, shown until it reports a message
     * @param cancellable whether the Cancel button is offered
     * @return the job, or {@code null} when one with this id is running
     */
    public Job start(String id, String label, boolean cancellable) {
        if (isRunning(id)) {
            return null;
        }
        Job job = new Job(id, label, cancellable);
        jobs.add(job);
        if (jobs.size() == 1) {
            refresh.play();
        }
        render();
        return job;
    }

    /** @return whether a job with this id runs */
    public boolean isRunning(String id) {
        return jobs.stream().anyMatch(j -> j.id.equals(id));
    }

    /** @return whether any job runs */
    public boolean isBusy() {
        return !jobs.isEmpty();
    }

    /** Cancels every running job (e.g. when the panel goes away). */
    public void cancelAll() {
        List.copyOf(jobs).forEach(Job::cancel);
    }

    private void end(Job job) {
        if (!jobs.remove(job)) {
            return;
        }
        if (jobs.isEmpty()) {
            refresh.stop();
            view.finish();
        } else {
            render();
        }
    }

    private void render() {
        if (jobs.isEmpty()) {
            return;
        }
        Job job = jobs.getLast();
        if (!view.isRunning()) {
            view.beginIndeterminate(null);
        }
        view.setSteps(job.done, job.total);
        String message = job.message != null ? job.message : job.label;
        if (jobs.size() > 1) {
            message = message + "  " + I18n.t("PanelJobs.more", jobs.size() - 1);
        }
        if (job.cancelled.get()) {
            message = I18n.t("PanelJobs.cancelling") + " " + message;
        }
        view.setMessage(message);
        view.setElapsed(elapsed(System.nanoTime() - job.started));
        view.setCancel(job.cancellable && job.cancellableNow && !job.cancelled.get() ? job::cancel : null);
        Progress.Hint hint = job.hint;
        if (hint == Progress.Hint.OPENFIGI_API_KEY) {
            Consumer<Progress.Hint> handler = hintHandler;
            view.setHint(I18n.t("PanelJobs.hint-openfigi"), handler == null ? null : I18n.t("PanelJobs.hint-openfigi-link"),
                    handler == null ? null : () -> handler.accept(hint));
        } else {
            view.setHint(null, null, null);
        }
    }

    /** @return "0:42" / "1:05:07" */
    static String elapsed(long nanos) {
        long s = Math.max(0, nanos / 1_000_000_000L);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    /** One running job: what it reports, read by the display. Thread-safe. */
    public final class Job implements Progress, AutoCloseable {

        private final String id;
        private final String label;
        private final boolean cancellable;
        private final long started = System.nanoTime();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Runnable> cancelActions = new CopyOnWriteArrayList<>();
        private volatile String message;
        private volatile long done;
        private volatile long total;
        private volatile Hint hint;
        private volatile boolean cancellableNow = true;

        private Job(String id, String label, boolean cancellable) {
            this.id = id;
            this.label = label;
            this.cancellable = cancellable;
        }

        @Override
        public void message(String text) {
            message = text;
        }

        @Override
        public void progress(long done, long total) {
            this.done = done;
            this.total = total;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void hint(Hint hint) {
            this.hint = hint;
        }

        @Override
        public void setCancellable(boolean cancellable) {
            cancellableNow = cancellable;
        }

        @Override
        public void onCancel(Runnable action) {
            cancelActions.add(action);
            if (cancelled.get()) {
                action.run();
            }
        }

        /** Asks the job to stop (Cancel button); it stops at its next step. */
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                cancelActions.forEach(Runnable::run);
                if (Platform.isFxApplicationThread()) {
                    render();
                }
            }
        }

        /** Ends the job: the display shows the next one or hides. Any thread. */
        @Override
        public void close() {
            if (Platform.isFxApplicationThread()) {
                end(this);
            } else {
                Platform.runLater(() -> end(this));
            }
        }

        /** @return the last message (for tests) */
        String messageForTesting() {
            return message;
        }
    }
}
