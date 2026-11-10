package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.util.Optional;

import org.fxt.freexmltoolkit.service.ai.AiPolicy;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderStore;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * Whether the AI actions are offered: the policy allows AI, the user switched it on and a usable default provider
 * exists. The entry points (Problems menu, Query Console, Transform panel) bind their visibility to
 * {@link #availableProperty()}; Settings → AI calls {@link #refresh()} after every change.
 */
public final class AiState {

    private static final ReadOnlyBooleanWrapper AVAILABLE = new ReadOnlyBooleanWrapper(compute());

    private AiState() {
    }

    public static ReadOnlyBooleanProperty availableProperty() {
        return AVAILABLE.getReadOnlyProperty();
    }

    public static boolean available() {
        return AVAILABLE.get();
    }

    /** Re-reads the settings and the policy. */
    public static void refresh() {
        boolean value = compute();
        if (Platform.isFxApplicationThread()) {
            AVAILABLE.set(value);
        } else {
            Platform.runLater(() -> AVAILABLE.set(value));
        }
    }

    /** The provider the actions use. */
    public static Optional<ProviderConfig> provider() {
        return ProviderStore.settings().defaultProvider();
    }

    private static boolean compute() {
        try {
            return AiPolicy.allowed() && AiSettings.current().enabled() && provider().isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
