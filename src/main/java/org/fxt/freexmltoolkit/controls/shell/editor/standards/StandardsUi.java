package org.fxt.freexmltoolkit.controls.shell.editor.standards;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.scene.Node;

import org.fxt.freexmltoolkit.service.standards.StandardsSettings;

/**
 * The UI side of the {@code standards.*} settings: an observable expert-mode flag the version controls bind
 * their visibility to, and a revision counter that ticks whenever the standards settings were saved (so the
 * tools refresh their "Default (…)" entries).
 */
public final class StandardsUi {

    private static final ReadOnlyBooleanWrapper EXPERT_MODE = new ReadOnlyBooleanWrapper(readExpertMode());
    private static final ReadOnlyIntegerWrapper REVISION = new ReadOnlyIntegerWrapper();

    private StandardsUi() {
    }

    /** Whether the expert mode (version choice) is on. FX thread only. */
    public static ReadOnlyBooleanProperty expertModeProperty() {
        return EXPERT_MODE.getReadOnlyProperty();
    }

    /** Ticks after every {@link #settingsChanged()}. FX thread only. */
    public static ReadOnlyIntegerProperty revisionProperty() {
        return REVISION.getReadOnlyProperty();
    }

    /** The current settings. */
    public static StandardsSettings settings() {
        return StandardsSettings.current();
    }

    /** Re-reads the settings after Settings → Standards was saved. */
    public static void settingsChanged() {
        Runnable update = () -> {
            EXPERT_MODE.set(readExpertMode());
            REVISION.set(REVISION.get() + 1);
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    /** Shows {@code node} (and lays it out) only while the expert mode is on. */
    public static void showInExpertMode(Node node) {
        node.visibleProperty().bind(EXPERT_MODE);
        node.managedProperty().bind(EXPERT_MODE);
    }

    private static boolean readExpertMode() {
        try {
            return StandardsSettings.current().expertMode();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
