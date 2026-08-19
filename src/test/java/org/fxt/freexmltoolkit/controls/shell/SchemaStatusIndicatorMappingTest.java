package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus;
import org.junit.jupiter.api.Test;

/**
 * Pure mapping of {@link SchemaStatus} to the status bar indicator's text, icon, style class
 * and tooltip (no JavaFX toolkit needed). This is also the only reliable place to pin the
 * transient LOADING label, which TestFX cannot catch deterministically.
 */
class SchemaStatusIndicatorMappingTest {

    private static final File XSD = new File("FundsXML4.xsd");

    @Test
    void loadingStateAnnouncesDetection() {
        assertEquals("Detecting XSD…", UnifiedShellView.schemaStatusText(SchemaStatus.LOADING, null));
        assertEquals("bi-hourglass-split", UnifiedShellView.schemaStatusIcon(SchemaStatus.LOADING));
        assertEquals("fxt-status-schema-loading", UnifiedShellView.schemaStatusStyleClass(SchemaStatus.LOADING));
        assertTrue(UnifiedShellView.schemaStatusTooltip(SchemaStatus.LOADING, null).contains("IntelliSense"));
    }

    @Test
    void readyStateShowsTheSchemaNameAndIntelliSenseAvailability() {
        assertEquals("XSD: FundsXML4.xsd", UnifiedShellView.schemaStatusText(SchemaStatus.READY, XSD));
        assertEquals("bi-check-circle", UnifiedShellView.schemaStatusIcon(SchemaStatus.READY));
        assertEquals("fxt-status-schema-ready", UnifiedShellView.schemaStatusStyleClass(SchemaStatus.READY));
        String tooltip = UnifiedShellView.schemaStatusTooltip(SchemaStatus.READY, XSD);
        assertTrue(tooltip.contains("IntelliSense is available") && tooltip.contains("FundsXML4.xsd"));
    }

    @Test
    void noneStateKeepsTheEstablishedLabelContract() {
        // "No XSD" is asserted verbatim by UnifiedShellViewTest — do not reword.
        assertEquals("No XSD", UnifiedShellView.schemaStatusText(SchemaStatus.NONE, null));
        assertEquals("bi-slash-circle", UnifiedShellView.schemaStatusIcon(SchemaStatus.NONE));
        assertEquals("fxt-status-schema-none", UnifiedShellView.schemaStatusStyleClass(SchemaStatus.NONE));
        assertTrue(UnifiedShellView.schemaStatusTooltip(SchemaStatus.NONE, null).contains("IntelliSense"));
    }

    @Test
    void errorStateExplainsThatIntelliSenseIsUnavailable() {
        assertEquals("XSD error", UnifiedShellView.schemaStatusText(SchemaStatus.ERROR, null));
        assertEquals("bi-exclamation-triangle", UnifiedShellView.schemaStatusIcon(SchemaStatus.ERROR));
        assertEquals("fxt-status-schema-error", UnifiedShellView.schemaStatusStyleClass(SchemaStatus.ERROR));
        assertTrue(UnifiedShellView.schemaStatusTooltip(SchemaStatus.ERROR, null).contains("unavailable"));
    }

    // ----- JSON Schema kind (JSON documents) --------------------------------

    private static final File JSON_SCHEMA = new File("product-schema.json");
    private static final String JSON_KIND = "JSON Schema";

    @Test
    void schemaKindLabelFollowsTheFileType() {
        assertEquals("JSON Schema", UnifiedShellView.schemaKindLabel(
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.JSON));
        assertEquals("XSD", UnifiedShellView.schemaKindLabel(
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML));
        assertEquals("XSD", UnifiedShellView.schemaKindLabel(
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSD));
    }

    @Test
    void jsonKindComposesTheSameStatusTexts() {
        assertEquals("Detecting JSON Schema…",
                UnifiedShellView.schemaStatusText(SchemaStatus.LOADING, null, JSON_KIND));
        assertEquals("JSON Schema: product-schema.json",
                UnifiedShellView.schemaStatusText(SchemaStatus.READY, JSON_SCHEMA, JSON_KIND));
        assertEquals("JSON Schema error",
                UnifiedShellView.schemaStatusText(SchemaStatus.ERROR, null, JSON_KIND));
        assertEquals("No JSON Schema",
                UnifiedShellView.schemaStatusText(SchemaStatus.NONE, null, JSON_KIND));
    }

    @Test
    void xsdKindStaysByteIdenticalToTheTwoArgMappers() {
        for (SchemaStatus status : SchemaStatus.values()) {
            assertEquals(UnifiedShellView.schemaStatusText(status, XSD),
                    UnifiedShellView.schemaStatusText(status, XSD, "XSD"));
            assertEquals(UnifiedShellView.schemaStatusTooltip(status, XSD),
                    UnifiedShellView.schemaStatusTooltip(status, XSD, "XSD"));
        }
    }

    @Test
    void jsonKindTooltipsTalkAboutValidationNotIntelliSense() {
        for (SchemaStatus status : SchemaStatus.values()) {
            String tooltip = UnifiedShellView.schemaStatusTooltip(status, JSON_SCHEMA, JSON_KIND);
            assertFalse(tooltip.contains("IntelliSense"),
                    status + " tooltip must not mention IntelliSense: " + tooltip);
        }
        assertTrue(UnifiedShellView.schemaStatusTooltip(SchemaStatus.READY, JSON_SCHEMA, JSON_KIND)
                .contains("product-schema.json"));
        assertTrue(UnifiedShellView.schemaStatusTooltip(SchemaStatus.NONE, null, JSON_KIND)
                .contains("well-formedness"));
    }
}
