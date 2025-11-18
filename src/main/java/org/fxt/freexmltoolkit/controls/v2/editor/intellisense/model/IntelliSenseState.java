package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model;

/**
 * IntelliSense state machine states.
 */
public enum IntelliSenseState {
    /**
     * Popup is hidden.
     */
    HIDDEN,

    /**
     * Showing element completions.
     */
    SHOWING_ELEMENTS,

    /**
     * Showing attribute completions.
     */
    SHOWING_ATTRIBUTES,

    /**
     * Showing attribute value completions.
     */
    SHOWING_VALUES
}
