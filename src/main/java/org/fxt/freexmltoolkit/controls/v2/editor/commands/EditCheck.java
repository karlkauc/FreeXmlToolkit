package org.fxt.freexmltoolkit.controls.v2.editor.commands;

/**
 * The verdict of an {@link XsdEditRules} check: whether a structural edit keeps the schema valid.
 *
 * @param status       allowed, allowed only after replacing conflicting content, or blocked
 * @param reason       user-facing English explanation (empty when allowed)
 * @param replaceLabel button text for the replacement (only for {@link Status#REPLACEABLE})
 */
public record EditCheck(Status status, String reason, String replaceLabel) {

    /** How an edit relates to the current model. */
    public enum Status {
        /** The edit keeps the schema valid. */
        ALLOWED,
        /** The edit conflicts with existing content; it is valid once that content is removed. */
        REPLACEABLE,
        /** The edit would make the schema invalid. */
        BLOCKED
    }

    private static final EditCheck ALLOWED_CHECK = new EditCheck(Status.ALLOWED, "", "");

    public EditCheck {
        reason = reason == null ? "" : reason;
        replaceLabel = replaceLabel == null ? "" : replaceLabel;
    }

    public static EditCheck allowed() {
        return ALLOWED_CHECK;
    }

    public static EditCheck blocked(String reason) {
        return new EditCheck(Status.BLOCKED, reason, "");
    }

    public static EditCheck replaceable(String reason, String replaceLabel) {
        return new EditCheck(Status.REPLACEABLE, reason, replaceLabel);
    }

    public boolean isAllowed() {
        return status == Status.ALLOWED;
    }

    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }

    public boolean needsConfirmation() {
        return status == Status.REPLACEABLE;
    }
}
