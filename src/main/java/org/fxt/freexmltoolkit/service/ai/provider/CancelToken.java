package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cancels a running request: closes its connection from another thread and stops any loop around it. */
public final class CancelToken {

    private volatile boolean cancelled;
    private final List<Runnable> onCancel = new CopyOnWriteArrayList<>();

    public static CancelToken none() {
        return new CancelToken();
    }

    public void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        for (Runnable action : onCancel) {
            try {
                action.run();
            } catch (RuntimeException ignored) {
                // closing a connection that is already gone
            }
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Runs {@code action} on cancel (at once when already cancelled); returns a handle that removes it. */
    public Runnable onCancel(Runnable action) {
        onCancel.add(action);
        if (cancelled) {
            action.run();
        }
        return () -> onCancel.remove(action);
    }

    public void throwIfCancelled() throws ProviderException {
        if (cancelled) {
            throw new ProviderException(ProviderException.Kind.CANCELLED, ProviderException.describe(
                    ProviderException.Kind.CANCELLED));
        }
    }
}
