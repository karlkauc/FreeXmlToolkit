package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * An FMA pension fund report gets the taxonomy's schema bound by its root element, although the
 * schema location it declares is a path on the sender's network that cannot be reached.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostPkReportTest {

    @TempDir
    static Path store;
    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        System.setProperty("fxt.pk.dir", store.toString());
        PluginManager.getInstance().resetForTesting();
        PluginManager.getInstance().loadAll();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("fxt.pk.dir");
        PluginManager.getInstance().resetForTesting();
    }

    @Test
    void theReportIsBoundToTheBundledSchema(@TempDir Path tmp) throws Exception {
        Path report = tmp.resolve("99.pfq.20260331.00001.test.xml");
        try (var in = getClass().getResourceAsStream("/pkmeldewesen/synthetic-pfq.xml")) {
            Files.write(report, in.readAllBytes());
        }
        var entry = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared()
                .resolveByRootElement("Nationale_Meldung_pfq");
        assertTrue(entry.isPresent(), "the Schema Library knows the root element");
        String content = Files.readString(report);
        assertTrue(EditorSchemaBinding.declaredSchemaFile(content, tmp.toFile()).isPresent(),
                "the static resolution finds it: " + entry.get().location());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(report));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertTrue(host.activeSchemaProperty().get().getName().startsWith("pk_meldung_pfq_"),
                String.valueOf(host.activeSchemaProperty().get()));
    }
}
