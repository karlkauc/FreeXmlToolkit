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
 * @param failureKind          fixed identifier of a failure for usage statistics (never text from the
 *                             system): {@link #FAILED}, {@link #POLICY_BLOCKED}, {@link #UNSUPPORTED},
 *                             {@link #NO_TYPES}; {@code null} on success
 */
public record FileAssociationResult(boolean success, String message,
                                    boolean systemSettingsOpened, List<String> errors, String failureKind) {

    /** A system command failed for another reason. */
    public static final String FAILED = "failed";
    /** The system refused by an administrator policy (e.g. registry editing disabled on Windows). */
    public static final String POLICY_BLOCKED = "policy_blocked";
    /** No native launcher (IDE / build directory run). */
    public static final String UNSUPPORTED = "unsupported";
    /** Nothing was selected. */
    public static final String NO_TYPES = "no_types";

    public FileAssociationResult(boolean success, String message, boolean systemSettingsOpened, List<String> errors) {
        this(success, message, systemSettingsOpened, errors, success ? null : FAILED);
    }

    public static FileAssociationResult ok(String message) {
        return new FileAssociationResult(true, message, false, List.of());
    }

    public static FileAssociationResult failure(String message, List<String> errors) {
        return failure(message, errors, FAILED);
    }

    public static FileAssociationResult failure(String message, List<String> errors, String failureKind) {
        return new FileAssociationResult(false, message, false, errors == null ? List.of() : List.copyOf(errors),
                failureKind);
    }
}
