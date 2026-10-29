package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Files that are not UTF-8 open, reload and save in their own encoding: a reload no longer fails
 * with {@code MalformedInputException} (telemetry {@code e3103a7bdb85d6fc}) and saving leaves the
 * bytes unchanged.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostEncodingTest {

    private static final Charset LATIN_1 = StandardCharsets.ISO_8859_1;
    private static final String DECLARED = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<a>Grüße</a>\n";

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        host.setExternalChangePrompt(new ExternalChangePrompt() {
            @Override
            public ChangedChoice fileChanged(OpenDocument document, boolean dirty) {
                return ChangedChoice.RELOAD;
            }

            @Override
            public DeletedChoice fileDeleted(OpenDocument document, boolean dirty) {
                return DeletedChoice.KEEP;
            }
        });
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void declaredLatin1OpensReloadsAndSavesUnchanged(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("latin1.xml");
        byte[] original = DECLARED.getBytes(LATIN_1);
        Files.write(file, original);
        open(file);
        assertEquals(DECLARED, activeText());

        String changed = DECLARED.replace("Grüße", "Äpfel");
        Files.write(file, changed.getBytes(LATIN_1));
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 20_000));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.isExternalChangeCheckRunning());
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.checkExternalChangesNow());
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.isExternalChangeCheckRunning());
            WaitForAsyncUtils.waitForFxEvents();
            return changed.equals(activeText());
        });

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText(DECLARED));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(30, TimeUnit.SECONDS));
        assertArrayEquals(original, Files.readAllBytes(file), "saved in the declared ISO-8859-1");
    }

    @Test
    void undeclaredLatin1IsReadAsWindows1252AndWrittenBackByteForByte(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("plain.xml");
        byte[] original = "<a>café</a>\n".getBytes(LATIN_1);
        Files.write(file, original);
        open(file);
        assertEquals("<a>café</a>\n", activeText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<a>café</a>\n"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(30, TimeUnit.SECONDS));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    private void open(Path file) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().filter(d -> file.equals(d.getPath()))
                        .map(d -> d.getDiskStamp() != null).orElse(false)));
    }

    private String activeText() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(null));
    }
}
