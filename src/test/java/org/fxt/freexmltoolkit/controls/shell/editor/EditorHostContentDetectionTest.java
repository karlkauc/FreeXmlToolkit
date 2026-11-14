package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A file of unknown extension opens as XML / JSON when its content is XML / JSON. */
@ExtendWith(ApplicationExtension.class)
class EditorHostContentDetectionTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    private EditorFileType open(Path file) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.openFile(file);
            host.openFile(file); // a second request while the first bytes are read opens no second tab
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> file.equals(d.getPath())).orElse(false)
                && !host.getActiveText().orElse("").isEmpty());
        return host.getActiveDocument().orElseThrow().getFileType();
    }

    @Test
    void unknownExtensionsAreDetectedFromTheContent(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("web.config");
        Files.writeString(config, "<?xml version=\"1.0\"?>\n<configuration><appSettings/></configuration>\n");
        assertEquals(EditorFileType.XML, open(config));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRAPHIC)),
                "a detected XML file gets the grid");

        Path geo = tmp.resolve("map.geojson");
        Files.writeString(geo, "{\"type\": \"FeatureCollection\", \"features\": []}\n");
        assertEquals(EditorFileType.JSON, open(geo));

        Path log = tmp.resolve("server.log");
        Files.writeString(log, "2026-11-14 INFO started\n");
        assertEquals(EditorFileType.OTHER, open(log));
        assertEquals(3, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getOpenDocuments().size()));
    }
}
