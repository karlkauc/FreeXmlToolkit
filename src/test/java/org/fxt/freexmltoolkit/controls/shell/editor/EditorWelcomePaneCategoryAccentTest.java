package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The Welcome trend cards colour their dot and progress bar with a looked-up workflow colour, so
 * they follow a live light/dark switch instead of baking one theme's hex into the inline style.
 */
class EditorWelcomePaneCategoryAccentTest {

    @Test
    void categoryAccentIsALookedUpWorkflowColour() {
        assertEquals("-fxt-wf-validation-accent", EditorWelcomePane.categoryAccent("Validation"));
        assertEquals("-fxt-wf-transform-accent", EditorWelcomePane.categoryAccent("Transformation"));
        assertEquals("-fxt-wf-schema-accent", EditorWelcomePane.categoryAccent("Tools"));
        assertEquals("-fxt-wf-signature-accent", EditorWelcomePane.categoryAccent("Security"));
        assertEquals("-fxt-wf-pdf-accent", EditorWelcomePane.categoryAccent("Export"));
        assertEquals("-fxt-wf-workspace-accent", EditorWelcomePane.categoryAccent("Editing"));
        assertEquals("-fxt-wf-workspace-accent", EditorWelcomePane.categoryAccent("Something new"));
    }
}
