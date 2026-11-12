package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.EditedWithComment;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Saving an XML-family document writes the "Edited with FreeXmlToolkit" comment once; other types get none. */
@ExtendWith(ApplicationExtension.class)
class EditorHostEditedWithCommentTest {

    private EditorHost host;
    private PropertiesService props;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        props = ServiceRegistry.get(PropertiesService.class);
        props.set(EditedWithComment.KEY, "true");
        host = new EditorHost();
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @AfterEach
    void restore() {
        props.set(EditedWithComment.KEY, "");
    }

    @Test
    void xmlGetsTheCommentOnceAcrossSaves(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("a.xml");
        Files.writeString(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a>1</a>\n");
        open(file);
        edit("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a>2</a>\n");
        save();
        String saved = Files.readString(file);
        assertTrue(saved.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- Edited with FreeXmlToolkit"),
                saved);
        assertTrue(saved.endsWith("-->\n<a>2</a>\n"), saved);

        edit("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a>3</a>\n");
        save();
        String again = Files.readString(file);
        assertEquals(1, again.split("Edited with FreeXmlToolkit", -1).length - 1, again);
        assertTrue(again.contains("<a>3</a>"), again);
    }

    @Test
    void switchedOffWritesTheTextAsIs(@TempDir Path tmp) throws Exception {
        props.set(EditedWithComment.KEY, "false");
        Path file = tmp.resolve("b.xml");
        Files.writeString(file, "<b/>\n");
        open(file);
        edit("<b>x</b>\n");
        save();
        assertEquals("<b>x</b>\n", Files.readString(file));
    }

    @Test
    void anXQueryGetsNoComment(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("q.xq");
        Files.writeString(file, "1\n");
        open(file);
        edit("<r>{1 + 1}</r>\n");
        save();
        assertFalse(Files.readString(file).contains("Edited with FreeXmlToolkit"));
    }

    private void edit(String text) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText(text));
    }

    private void save() throws Exception {
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(30, TimeUnit.SECONDS));
    }

    private void open(Path file) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().filter(d -> file.equals(d.getPath()))
                        .map(d -> d.getDiskStamp() != null).orElse(false)));
    }
}
