package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.telemetry.DocKind;
import org.fxt.freexmltoolkit.service.telemetry.RecordingTelemetryService;
import org.fxt.freexmltoolkit.service.telemetry.Telemetry;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the EditorHost usage hooks (file_open / file_save / view_mode / format) and that
 * they never carry a file name.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostUsageEventsTest {

    private EditorHost host;
    private RecordingTelemetryService telemetry;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @BeforeEach
    void installTelemetry() {
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
    }

    @AfterEach
    void resetTelemetry() {
        Telemetry.reset();
    }

    @Test
    void openingAFileRecordsKindAndSizeButNoName(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("confidential-schema.xsd");
        String content = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";
        Files.writeString(xsd, content);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !telemetry.events("file_open").isEmpty());

        TelemetryEvent e = telemetry.events("file_open").get(0);
        assertEquals(DocKind.XSD, e.docKind());
        assertEquals(Files.size(xsd), e.inputBytes());
        assertEquals("file", e.meta().get("source"));
        assertFalse(e.toJson().toString().contains("confidential"), "file names must never be sent");
    }

    @Test
    void newDocumentAndSaveAreRecorded(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("out.xml");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.newDocument(EditorFileType.XML);
            host.saveActiveAs(target);
            return null;
        });

        TelemetryEvent open = telemetry.events("file_open").get(0);
        assertEquals("new", open.meta().get("source"));
        assertEquals(DocKind.XML, open.docKind());
        assertEquals(1, telemetry.events("file_save").size());
        assertEquals(DocKind.XML, telemetry.events("file_save").get(0).docKind());
    }

    @Test
    void onlyActualViewModeChangesAreRecorded(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<a><b>x</b></a>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("b>x")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT); // already TEXT → no event
            host.setActiveViewMode(ViewMode.TREE);
            host.setActiveViewMode(ViewMode.TREE); // unchanged → no second event
            return null;
        });

        List<TelemetryEvent> modes = telemetry.events("view_mode");
        assertEquals(1, modes.size(), "only actual view-mode changes are recorded: " + modes);
        assertEquals("tree", modes.get(0).meta().get("mode"));
        assertEquals(TelemetryEvent.Category.NAVIGATION, modes.get(0).category());
    }

    @Test
    void formattingIsRecorded(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<a><b>x</b></a>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("b>x")).orElse(false));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.formatActive()));

        TelemetryEvent e = telemetry.events("format").get(0);
        assertEquals(TelemetryEvent.Status.OK, e.status());
        assertEquals(DocKind.XML, e.docKind());
    }
}
