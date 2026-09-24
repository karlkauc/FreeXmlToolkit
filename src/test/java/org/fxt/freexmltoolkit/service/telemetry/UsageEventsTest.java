package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.fxt.freexmltoolkit.service.UsageTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageEventsTest {

    private RecordingTelemetryService telemetry;
    private UsageTrackingService local;

    @BeforeEach
    void setUp() {
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        local = mock(UsageTrackingService.class);
        UsageEvents.setLocalSink(() -> local);
        UsageEvents.resetSession();
    }

    @AfterEach
    void tearDown() {
        Telemetry.reset();
        UsageEvents.setLocalSink(null);
        UsageEvents.resetSession();
    }

    private TelemetryEvent single(String eventType) {
        List<TelemetryEvent> events = telemetry.events(eventType);
        assertEquals(1, events.size(), "exactly one " + eventType + " event expected, got " + telemetry.events());
        return events.get(0);
    }

    @Test
    void fileOpenedFeedsBothSinks() {
        UsageEvents.fileOpened(DocKind.XSD, 1234, UsageEvents.SOURCE_FILE);

        TelemetryEvent e = single("file_open");
        assertEquals(TelemetryEvent.Category.ACTION, e.category());
        assertEquals(DocKind.XSD, e.docKind());
        assertEquals(1234L, e.inputBytes());
        assertEquals("file", e.meta().get("source"));
        verify(local).trackFileOpened();
    }

    @Test
    void generatedDocumentsDoNotCountAsLocallyOpenedFiles() {
        UsageEvents.fileOpened(DocKind.XML, -1, UsageEvents.SOURCE_GENERATED);

        TelemetryEvent e = single("file_open");
        assertNull(e.inputBytes(), "unknown size must be omitted");
        assertEquals("generated", e.meta().get("source"));
        verifyNoInteractions(local);
    }

    @Test
    void fileSavedIgnoresZeroCount() {
        UsageEvents.fileSaved(null, 0);
        UsageEvents.fileSaved(null, 3);

        assertEquals(3, single("file_save").fileCount());
    }

    @Test
    void validationWithProblemsIsOkWithErrorCountAndUpdatesLocalStatistics() {
        long t0 = System.nanoTime();
        UsageEvents.validated("xsd_schematron", DocKind.XML, 3, t0, false, false);

        TelemetryEvent e = single("validate");
        assertEquals(TelemetryEvent.Status.OK, e.status());
        assertEquals(3, e.errorCount());
        assertEquals("xsd_schematron", e.meta().get("schema"));
        assertNotNull(e.durationMs());
        verify(local).trackFileValidation(3);
        verify(local).trackFeatureUsed("xsd_validation");
        verify(local).trackSchematronValidation();
    }

    @Test
    void failedValidationIsErrorAndSkipsLocalStatistics() {
        UsageEvents.validated("wellformed", DocKind.XML, 0, 0, true, false);

        TelemetryEvent e = single("validate");
        assertEquals(TelemetryEvent.Status.ERROR, e.status());
        assertNull(e.durationMs(), "startNanos 0 means not measured");
        verifyNoInteractions(local);
    }

    @Test
    void liveValidationIsThrottledButExplicitRunsAreNot() {
        UsageEvents.validated("xsd", DocKind.XML, 0, 0, false, true);
        UsageEvents.validated("xsd", DocKind.XML, 0, 0, false, true);
        UsageEvents.validated("xsd", DocKind.XML, 0, 0, false, false);
        UsageEvents.validated("xsd", DocKind.XML, 0, 0, false, false);

        List<TelemetryEvent> events = telemetry.events("validate");
        assertEquals(3, events.size());
        assertEquals("live", events.get(0).meta().get("trigger"));
    }

    @Test
    void liveTransformIsThrottledIndependentlyOfValidation() {
        UsageEvents.validated("xsd", DocKind.XML, 0, 0, false, true);
        UsageEvents.xsltTransformed(1, 0, true, true);
        UsageEvents.xsltTransformed(1, 0, true, true);

        assertEquals(1, telemetry.events("validate").size());
        assertEquals(1, telemetry.events("xslt_transform").size());
    }

    @Test
    void schemaKindClassification() {
        assertEquals("json", UsageEvents.schemaKind(true, true, false));
        assertEquals("wellformed", UsageEvents.schemaKind(true, false, true));
        assertEquals("xsd", UsageEvents.schemaKind(false, true, false));
        assertEquals("schematron", UsageEvents.schemaKind(false, false, true));
        assertEquals("xsd_schematron", UsageEvents.schemaKind(false, true, true));
        assertEquals("wellformed", UsageEvents.schemaKind(false, false, false));
    }

    @Test
    void activityOpenedOncePerSession() {
        UsageEvents.activityOpened("validation");
        UsageEvents.activityOpened("validation");
        UsageEvents.activityOpened("favorites");
        UsageEvents.activityOpened(null);

        List<TelemetryEvent> events = telemetry.events("activity_open");
        assertEquals(2, events.size());
        assertEquals(TelemetryEvent.Category.NAVIGATION, events.get(0).category());
        assertEquals("validation", events.get(0).meta().get("activity"));
        verify(local).trackFeatureUsed("favorites_system");
    }

    @Test
    void viewModeOnXsdMarksVisualizationFeature() {
        UsageEvents.viewModeChanged("graphic", DocKind.XSD);

        TelemetryEvent e = single("view_mode");
        assertEquals(TelemetryEvent.Category.NAVIGATION, e.category());
        assertEquals("graphic", e.meta().get("mode"));
        verify(local).trackFeatureUsed("xsd_visualization");
    }

    @Test
    void failedOperationsDoNotCountLocally() {
        UsageEvents.xsltTransformed(1, 0, false);
        UsageEvents.xqueryExecuted(0, false);
        UsageEvents.xpathExecuted(0, false);
        UsageEvents.pdfGenerated(0, false);
        UsageEvents.signed(0, false);
        UsageEvents.xsdGenerated(1, 0, false);
        UsageEvents.formatted(DocKind.XML, false);

        assertEquals(7, telemetry.events().size());
        assertTrue(telemetry.events().stream().allMatch(e -> e.status() == TelemetryEvent.Status.ERROR));
        verifyNoInteractions(local);
    }

    @Test
    void successfulOperationsReviveLocalCounters() {
        UsageEvents.xsltTransformed(1, 0, true);
        UsageEvents.xqueryExecuted(0, true);
        UsageEvents.xpathExecuted(0, true);
        UsageEvents.pdfGenerated(0, true);
        UsageEvents.signed(0, true);
        UsageEvents.signatureVerified("basic", "invalid", 0);
        UsageEvents.xsdGenerated(2, 0, true);
        UsageEvents.formatted(DocKind.JSON, true);
        UsageEvents.schemaDocGenerated("HTML", 0, TelemetryEvent.Status.OK);
        UsageEvents.quickFixApplied();

        verify(local).trackTransformation();
        verify(local).trackXQueryExecution();
        verify(local).trackXPathQuery();
        verify(local).trackPdfGeneration();
        verify(local).trackSignatureOperation(true);
        verify(local).trackSignatureOperation(false);
        verify(local).trackSchemaGeneration();
        verify(local).trackFormatting();
        verify(local).trackFeatureUsed("xsd_documentation");
        verify(local).trackErrorCorrected(1);
        assertEquals("html", single("schema_doc").meta().get("format"));
        assertEquals(1, single("verify_signature").errorCount());
    }

    @Test
    void cancelledDocGenerationIsNotCountedLocally() {
        UsageEvents.schemaDocGenerated("pdf", 0, TelemetryEvent.Status.CANCELLED);

        assertEquals(TelemetryEvent.Status.CANCELLED, single("schema_doc").status());
        verify(local, never()).trackFeatureUsed(anyString());
    }

    @Test
    void batchTransformReportsFileAndFailureCounts() {
        UsageEvents.batchTransformed(false, 3, 1, System.nanoTime());
        UsageEvents.batchTransformed(true, 2, 2, 0);

        TelemetryEvent xslt = single("xslt_transform");
        assertEquals(3, xslt.fileCount());
        assertEquals(1, xslt.errorCount());
        assertEquals(TelemetryEvent.Status.OK, xslt.status());
        assertEquals(Boolean.TRUE, xslt.meta().get("batch"));
        TelemetryEvent xquery = single("xquery");
        assertEquals(TelemetryEvent.Status.ERROR, xquery.status());
        verify(local).trackTransformation();
        verify(local, never()).trackXQueryExecution();
    }

    @Test
    void updateCheckErrorHasErrorStatus() {
        UsageEvents.updateCheck("startup", UsageEvents.UPDATE_ERROR);

        TelemetryEvent e = single("update_check");
        assertEquals(TelemetryEvent.Status.ERROR, e.status());
        assertEquals("error", e.meta().get("result"));
        assertEquals("startup", e.meta().get("trigger"));
    }

    @Test
    void nullLocalSinkIsTolerated() {
        UsageEvents.setLocalSink(() -> null);

        assertDoesNotThrow(() -> UsageEvents.fileOpened(DocKind.XML, 1, UsageEvents.SOURCE_FILE));
        assertEquals(1, telemetry.events().size(), "telemetry still recorded");
    }

    @Test
    void failingSinksNeverPropagate() {
        UsageEvents.setLocalSink(() -> {
            throw new IllegalStateException("no registry");
        });
        Telemetry.install(new RecordingTelemetryService() {
            @Override
            public void track(String eventType, TelemetryEvent.Category category,
                              java.util.function.Consumer<TelemetryEvent.Builder> customizer) {
                throw new IllegalStateException("broken telemetry");
            }
        });

        assertDoesNotThrow(() -> UsageEvents.validated("xsd", DocKind.XML, 1, 0, false, false));
        assertDoesNotThrow(() -> UsageEvents.fileOpened(null, -1, null));
    }

    @Test
    void localSinkFailureDoesNotSuppressTelemetry() {
        doThrow(new IllegalStateException("disk full")).when(local).trackFileValidation(2);

        assertDoesNotThrow(() -> UsageEvents.validated("xsd", DocKind.XML, 2, 0, false, false));
        assertEquals(1, telemetry.events("validate").size());
    }
}
