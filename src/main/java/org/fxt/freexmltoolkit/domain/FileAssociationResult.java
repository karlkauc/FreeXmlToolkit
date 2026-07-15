package org.fxt.freexmltoolkit.domain;

import java.util.List;

/**
 * Outcome of a file-association register/unregister operation.
 *
 * @param success              true when all requested types were processed without error
 * @param message              user-facing English summary (shown in the Settings status label)
 * @param systemSettingsOpened true when the OS settings page was opened for the user to
 *                             complete the default-app selection (Windows only)
 * @param errors               per-extension error details (empty on full success)
 */
public record FileAssociationResult(boolean success, String message,
                                    boolean systemSettingsOpened, List<String> errors) {

    public static FileAssociationResult ok(String message) {
        return new FileAssociationResult(true, message, false, List.of());
    }

    public static FileAssociationResult failure(String message, List<String> errors) {
        return new FileAssociationResult(false, message, false, errors == null ? List.of() : List.copyOf(errors));
    }
}
