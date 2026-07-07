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
}
