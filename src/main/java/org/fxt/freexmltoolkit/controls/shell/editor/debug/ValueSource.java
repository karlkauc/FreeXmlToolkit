package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import net.sf.saxon.s9api.XdmValue;

import org.fxt.freexmltoolkit.debugger.PausedSnapshot;

/**
 * What the value viewer shows: a variable or watch with its full XDM value from the pause, or —
 * when no value is available (position(), unbound slots, display-only snapshots, watch errors) —
 * the row's text.
 *
 * @param title        the variable name or watch expression
 * @param type         the XDM type label (may be empty)
 * @param scope        LOCAL / GLOBAL / PARAMETER / WATCH (may be empty)
 * @param value        the full value, or {@code null} for the text fallback
 * @param fallbackText text shown when {@code value} is {@code null}
 * @param snapshot     the pause the value belongs to (XPath scope; may be {@code null})
 */
public record ValueSource(String title, String type, String scope, XdmValue value,
                          String fallbackText, PausedSnapshot snapshot) {

    /** @return whether the full value is available (not just the row text). */
    public boolean hasValue() {
        return value != null;
    }
}
