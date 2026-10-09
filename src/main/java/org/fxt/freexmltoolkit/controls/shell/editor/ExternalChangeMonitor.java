package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Detects open documents whose file was changed or deleted on disk by another program.
 * <p>
 * Polls instead of using a {@link java.nio.file.WatchService}: file-system events are not
 * delivered reliably for network drives (WebDAV, SMB), where a stat comparison always works.
 * The stat calls run on a background executor - never on the FX thread - and the result is
 * handed back on the FX thread. Polling only happens while the owning window is focused, plus
 * once the moment it regains focus, so the user is never prompted while working in another
 * application.
 */
final class ExternalChangeMonitor {

    /** How often the open documents are checked while the window is focused. */
    static final Duration POLL_INTERVAL = Duration.seconds(2);

    /**
     * A document to check.
     *
     * @param document the open document
     * @param path     its file
     * @param known    the stamp recorded at the last load/save ({@code null} = none yet)
     */
    record Watched(OpenDocument document, Path path, DiskStamp known) { }

    /**
     * A document whose file no longer matches its recorded stamp.
     *
     * @param document the open document
     * @param path     the file that was checked
     * @param known    the stamp the check started from
     * @param current  the stamp found on disk
     * @param diskText the file's current text, or {@code null} when deleted or unreadable
     */
    record Change(OpenDocument document, Path path, DiskStamp known, DiskStamp current, String diskText) {
        boolean deleted() {
            return !current.exists();
        }
    }

    private final Supplier<List<Watched>> watched;
    private final Consumer<List<Change>> onChanges;
    private final Executor executor;
    /** True from the start of a check until its result has been handled (incl. open prompts). */
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Timeline timeline;
    /** Held in a field: the flat-mapped binding is only weakly reachable otherwise. */
    private ObservableValue<Boolean> windowFocused;

    /**
     * @param watched   supplies the documents to check; called on the FX thread
     * @param onChanges receives the non-empty list of changes; called on the FX thread
     * @param executor  runs the disk access off the FX thread
     */
    ExternalChangeMonitor(Supplier<List<Watched>> watched, Consumer<List<Change>> onChanges, Executor executor) {
        this.watched = watched;
        this.onChanges = onChanges;
        this.executor = executor;
        this.timeline = new Timeline(new KeyFrame(POLL_INTERVAL, e -> {
            if (Boolean.TRUE.equals(windowFocused.getValue())) {
                checkNow();
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * Starts polling for as long as {@code host} is part of a scene, and checks immediately
     * whenever the host's window regains focus.
     */
    void attachTo(Node host) {
        windowFocused = host.sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::focusedProperty);
        windowFocused.addListener((obs, was, focused) -> {
            if (Boolean.TRUE.equals(focused)) {
                checkNow();
            }
        });
        host.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                timeline.stop();
            } else {
                timeline.play();
            }
        });
        if (host.getScene() != null) {
            timeline.play();
        }
    }

    /**
     * Checks all watched documents once, regardless of window focus. No-op while a previous
     * check is still running or being handled. Must be called on the FX thread.
     */
    void checkNow() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        List<Watched> snapshot = watched.get();
        if (snapshot.isEmpty()) {
            busy.set(false);
            return;
        }
        try {
            executor.execute(() -> {
                List<Change> changes;
                try {
                    changes = probe(snapshot);
                } catch (RuntimeException e) {
                    busy.set(false);
                    return;
                }
                if (changes.isEmpty()) {
                    busy.set(false);
                    return;
                }
                // runLater (not the Timeline handler): the consumer shows modal dialogs, which
                // is not allowed during animation processing.
                Platform.runLater(() -> {
                    try {
                        onChanges.accept(changes);
                    } finally {
                        busy.set(false);
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            busy.set(false); // application is shutting down
        }
    }

    /** @return whether a check is running or its result is still being handled */
    boolean isBusy() {
        return busy.get();
    }

    /**
     * Compares each document's recorded stamp with the disk. Blocking; call off the FX thread.
     *
     * @return the documents whose file differs from the recorded stamp, in input order
     */
    static List<Change> probe(List<Watched> snapshot) {
        List<Change> changes = new ArrayList<>();
        for (Watched w : snapshot) {
            DiskStamp current = DiskStamp.of(w.path());
            if (current == null || Objects.equals(current, w.known())) {
                continue; // undeterminable right now, or unchanged
            }
            String diskText = null;
            if (current.exists()) {
                try {
                    diskText = Files.readString(w.path(), StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException | OutOfMemoryError e) {
                    // unreadable: reported as changed without text
                }
            }
            changes.add(new Change(w.document(), w.path(), w.known(), current, diskText));
        }
        return changes;
    }
}
