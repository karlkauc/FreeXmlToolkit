package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/** Workflow colour families (spec 2026-09-26-workflow-colours-branding-design.md §1, §5.2). */
class WorkflowTest {

    @Test
    void everyWorkflowHasSixSlotsWithMatchingCssNames() {
        for (Workflow wf : Workflow.values()) {
            assertEquals("-fxt-wf-" + wf.id() + "-accent", wf.accent().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-fg", wf.fg().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-fill", wf.fill().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-bg", wf.bg().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-border", wf.border().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-rail", wf.rail().cssVariable());
            assertEquals("fxt-wf-" + wf.id(), wf.cssClass());
        }
    }

    @Test
    void railIsTheDarkAccentInBothThemes() {
        for (Workflow wf : Workflow.values()) {
            Color darkAccent = wf.accent().color(DesignTokens.Theme.DARK);
            assertEquals(darkAccent, wf.rail().color(DesignTokens.Theme.LIGHT), wf + " rail light");
            assertEquals(darkAccent, wf.rail().color(DesignTokens.Theme.DARK), wf + " rail dark");
        }
    }

    @Test
    void paletteMatchesSpec() {
        assertEquals(Color.web("#2f9e44"), Workflow.VALIDATION.accent().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#238636"), Workflow.VALIDATION.fill().color(DesignTokens.Theme.DARK));
        assertEquals(Color.web("#126ccc"), Workflow.WORKSPACE.fg().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#ac560d"), Workflow.TRANSFORM.fg().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#d6336c"), Workflow.PDF.accent().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#1f6feb"), Workflow.WORKSPACE.fill().color(DesignTokens.Theme.DARK));
    }

    @Test
    void categoryLookupCoversWelcomeCategoriesAndFallsBack() {
        assertEquals(Workflow.VALIDATION, Workflow.forCategory("Validation"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Editing"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Query"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Organization"));
        assertEquals(Workflow.TRANSFORM, Workflow.forCategory("Transformation"));
        assertEquals(Workflow.SCHEMA, Workflow.forCategory("Tools"));
        assertEquals(Workflow.SIGNATURE, Workflow.forCategory("Security"));
        assertEquals(Workflow.PDF, Workflow.forCategory("Export"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Something new"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory(null));
    }

    @Test
    void operationTypeLookupMapsRunsToWorkflowsAndFallsBack() {
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XSLT"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XQUERY"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XPATH"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("JSONPATH"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XPROC"));
        assertEquals(Workflow.VALIDATION, Workflow.forOperationType("VALIDATION"));
        assertEquals(Workflow.PDF, Workflow.forOperationType("FOP_PDF"));
        assertEquals(Workflow.NEUTRAL, Workflow.forOperationType("SOMETHING_NEW"));
        assertEquals(Workflow.NEUTRAL, Workflow.forOperationType(null));
    }

    @Test
    void actionColorsMapOntoExistingSemanticTokens() {
        assertEquals(DesignTokens.ColorToken.SUCCESS, ActionColor.CREATE.token());
        assertEquals(DesignTokens.ColorToken.DANGER, ActionColor.DELETE.token());
        assertEquals(DesignTokens.ColorToken.ACCENT, ActionColor.MODIFY.token());
        assertEquals(DesignTokens.ColorToken.INFO, ActionColor.NAVIGATE.token());
        assertEquals(DesignTokens.ColorToken.PURPLE, ActionColor.STRUCTURE.token());
        assertEquals(DesignTokens.ColorToken.PRIMARY, ActionColor.TOOL.token());
        assertEquals(DesignTokens.ColorToken.NEUTRAL, ActionColor.NEUTRAL.token());
    }
}
