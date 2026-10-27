package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Transform panel: XSLT transformation of the active
 * XML, and XPath evaluation, both producing output.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelTest {

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;

    // Saved-query files created by tests, deleted in @AfterEach. FavoritesService
    // writes to a fixed user-home config directory and cannot be redirected, so the
    // tests use unique names and clean up after themselves to avoid polluting it.
    private final List<File> createdQueries = new ArrayList<>();

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @AfterEach
    void deleteCreatedQueries() {
        for (File file : createdQueries) {
            if (file != null) {
                file.delete();
            }
        }
        createdQueries.clear();
    }

    @Test
    void transformsActiveXmlWithXslt(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Hello</out>"));
        assertTrue(panel.getOutputText().contains("<out>Hello</out>"), panel.getOutputText());
        // Transform stats are shown (elapsed time + output size).
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> panel.getTransformStats().contains("chars"));
        assertTrue(panel.getTransformStats().matches("\\d+ ms · \\d+ chars"), panel.getTransformStats());
    }

    @Test
    void passesParametersAndHonorsTextOutputFormat(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("param.xslt");
        Files.writeString(xslt, """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:param name="who" select="'nobody'"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/">Hi <xsl:value-of select="$who"/></xsl:template>
                </xsl:stylesheet>
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.addParameter("who", "Karl");
            panel.setOutputFormat(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hi Karl"));
        assertTrue(panel.getOutputText().contains("Hi Karl"), panel.getOutputText());
        assertFalse(panel.getOutputText().contains("<?xml"), "text output must not be wrapped as XML");
    }

    @Test
    void livePreviewAutoTransformsWhenEnabled(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);

        // Enable live preview with a stylesheet set — do NOT call transform() manually.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.setLivePreview(true);
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Hello</out>"));
        assertTrue(panel.getOutputText().contains("<out>Hello</out>"), panel.getOutputText());
    }

    @Test
    void failingLivePreviewShowsErrorInlineWithoutFailureDialog(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("broken.xslt");
        Files.writeString(xslt, XSLT.replace("<xsl:output method=\"xml\"/>", "<xsl:foo/>"));
        var telemetry = new org.fxt.freexmltoolkit.service.telemetry.RecordingTelemetryService();
        org.fxt.freexmltoolkit.service.telemetry.Telemetry.install(telemetry);
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.setXsltFile(xslt.toFile());
                panel.setLivePreview(true);
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("XTSE0010"));
            assertTrue(panel.getOutputText().contains("xsl:foo"), panel.getOutputText());
            // The live run must not raise the modal failure dialog (which reports "dialog.*").
            assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());

            // An explicit run is recorded as a failed transform (the user's stylesheet is
            // broken: invalid_input), and not reported a second time as an app error.
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.setLivePreview(false);
                panel.transform();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> telemetry.events("xslt_transform").stream()
                    .anyMatch(e -> !e.meta().containsKey("trigger")));
            var run = telemetry.events("xslt_transform").stream()
                    .filter(e -> !e.meta().containsKey("trigger")).findFirst().orElseThrow();
            assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.INVALID_INPUT, run.status());
            assertEquals("XTSE0010", run.errorCode());
            assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());
        } finally {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reset();
        }
    }

    @Test
    void writesHtmlPreviewFile() throws Exception {
        java.io.File file = TransformOutputPanel.writeHtmlPreview("<html><body>Preview Hi</body></html>");
        assertTrue(file.isFile() && file.getName().endsWith(".html"), file.toString());
        assertTrue(Files.readString(file.toPath()).contains("Preview Hi"));
    }

    @Test
    void detectsExternalXsltFileChange(@TempDir Path tmp) throws Exception {
        Path xslt = tmp.resolve("watch.xslt");
        Files.writeString(xslt, XSLT);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            return null;
        });
        // No change right after setting the file.
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.pollXsltChanged()));
        // Modify the stylesheet on disk with a later timestamp → detected.
        Files.writeString(xslt, XSLT + "\n<!-- edited -->");
        xslt.toFile().setLastModified(System.currentTimeMillis() + 3000);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.pollXsltChanged()));
        // ...and consumed (no second trigger until it changes again).
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.pollXsltChanged()));
    }

    @Test
    void recordsAndListsRecentXslt(@TempDir Path tmp) throws Exception {
        Path xslt = tmp.resolve("recent.xslt");
        Files.writeString(xslt, XSLT);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.refreshRecentXsltMenu();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.recentXsltNames().contains("recent.xslt"),
                "the chosen stylesheet must appear in the Recent XSLT menu, was: " + panel.recentXsltNames());
    }

    @Test
    void droppingAStylesheetOnTheSourceRowLoadsIt(@TempDir Path tmp) throws Exception {
        javafx.scene.layout.Region row = (javafx.scene.layout.Region) panel.lookup("#transform-xslt-row");
        assertNotNull(row, "the STYLESHEET row must exist");
        assertNotNull(row.getOnDragOver(), "the STYLESHEET row must accept file drags");

        Path xslt = tmp.resolve("dropped.xsl");
        Files.writeString(xslt, XSLT);
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(List.of(xslt.toFile()));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.getOnDragDropped().handle(event);
            return null;
        });

        javafx.scene.control.Label name = (javafx.scene.control.Label) row.lookup(".fxt-vp-source-name");
        assertEquals("dropped.xsl", name.getText(), "the dropped stylesheet must be loaded");
        org.mockito.Mockito.verify(event).setDropCompleted(true);
        assertTrue(host.getOpenDocuments().stream().anyMatch(d -> xslt.equals(d.getPath())),
                "the dropped stylesheet is loaded into the editor");
    }

    @Test
    void droppingAnXmlOnTheInputRowOverridesTheInput(@TempDir Path tmp) throws Exception {
        javafx.scene.layout.Region row = (javafx.scene.layout.Region) panel.lookup("#transform-input-row");
        assertNotNull(row, "the INPUT row must exist");
        assertNotNull(row.getOnDragOver(), "the INPUT row must accept file drags");

        Path xml = tmp.resolve("override.xml");
        Files.writeString(xml, XML);
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(List.of(xml.toFile()));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.getOnDragDropped().handle(event);
            return null;
        });

        javafx.scene.control.Label name = (javafx.scene.control.Label) row.lookup(".fxt-vp-source-name");
        assertEquals("override.xml", name.getText(), "the dropped XML must become the input override");
        org.mockito.Mockito.verify(event).setDropCompleted(true);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(xml, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().map(OpenDocument::getPath).orElse(null)),
                "the dropped input opens as the active editor tab");
    }

    @Test
    void aStylesheetTabInFrontNeverBecomesTheInput(@TempDir Path tmp) throws Exception {
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);
        javafx.scene.control.Label name = (javafx.scene.control.Label)
                panel.lookup("#transform-input-row").lookup(".fxt-vp-source-name");

        // Picked (e.g. from Recent) while nothing else is open: its tab is the only one and in front.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectXslt(List.of(xslt.toFile()), xslt.toFile());
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveDocument().isPresent());
        assertNotEquals("t.xslt", name.getText(), "the stylesheet must not be shown as the input");

        // With an XML open, the stylesheet tab in front keeps that XML as the input.
        openGreeting(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xslt));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> xslt.equals(d.getPath())).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("doc.xml", name.getText(), "the last XML document stays the input");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Hello</out>"));
    }

    private void openGreeting(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
    }
}
