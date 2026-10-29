package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.service.telemetry.QueryFailure;

/**
 * The outcome of a runner action as shown to the user plus, for a failed run, its telemetry
 * classification.
 *
 * @param text    the result text, or {@code "ERROR: …"}
 * @param failure why the run failed ({@code null} = success)
 */
public record ActionRun(String text, QueryFailure failure) {

    /** @return whether the run failed */
    public boolean failed() {
        return failure != null;
    }

    /** A successful run. */
    public static ActionRun ok(String text) {
        return new ActionRun(text, null);
    }

    /** A failed run: {@code "ERROR: <message>"} plus the exception's classification. */
    public static ActionRun failed(Throwable t) {
        return new ActionRun("ERROR: " + t.getMessage(), QueryFailure.of(t));
    }
}
