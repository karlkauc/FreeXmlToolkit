package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.junit.jupiter.api.Test;

/**
 * The Welcome trend cards take their category colour from the workflow family: the card carries the
 * family's style class and {@code unified-shell.css} colours dot and bar with its accent token, so they
 * follow a live light/dark switch. No inline looked-up colours: those fail to convert (one CSS warning
 * per pass) wherever the design tokens are not in scope.
 */
class EditorWelcomePaneCategoryAccentTest {

    @Test
    void categoriesMapToWorkflowFamilies() {
        assertEquals(Workflow.VALIDATION, EditorWelcomePane.categoryWorkflow("Validation"));
        assertEquals(Workflow.TRANSFORM, EditorWelcomePane.categoryWorkflow("Transformation"));
        assertEquals(Workflow.SCHEMA, EditorWelcomePane.categoryWorkflow("Tools"));
        assertEquals(Workflow.SIGNATURE, EditorWelcomePane.categoryWorkflow("Security"));
        assertEquals(Workflow.PDF, EditorWelcomePane.categoryWorkflow("Export"));
        assertEquals(Workflow.WORKSPACE, EditorWelcomePane.categoryWorkflow("Editing"));
        assertEquals(Workflow.WORKSPACE, EditorWelcomePane.categoryWorkflow("Something new"));
    }

    @Test
    void everyWorkflowHasTrendCardRules() throws Exception {
        String css;
        try (InputStream in = EditorWelcomePane.class.getResourceAsStream("/css/unified-shell.css")) {
            assertNotNull(in);
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (Workflow workflow : Workflow.values()) {
            String card = ".fxt-trend-cat-card." + workflow.cssClass();
            assertTrue(css.contains(card + " .fxt-trend-cat-dot { -fx-background-color: -fxt-wf-" + workflow.id()
                    + "-accent; }"), "dot rule for " + workflow);
            assertTrue(css.contains(card + " .fxt-trend-cat-bar { -fx-accent: -fxt-wf-" + workflow.id()
                    + "-accent; }"), "bar rule for " + workflow);
        }
    }
}
