package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A document's DOCTYPE / xml-model binds its DTD / RELAX NG schema, with IntelliSense from it. */
@ExtendWith(ApplicationExtension.class)
class EditorGrammarBindingTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    private void openAndWait(Path file, String grammarName) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            host.openFile(file);
            return null;
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> {
            var schema = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSchemaProperty().get());
            return schema != null && schema.getName().equals(grammarName);
        });
        assertEquals(EditorHost.SchemaStatus.READY, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSchemaStatusProperty().get()));
    }

    private List<String> validChildren() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.resolveValidChildren("/book"));
    }

    @Test
    void doctypeBindsTheDtdWithIntelliSense(@TempDir Path dir) throws Exception {
        System.setProperty("fxt.grammarXsd.dir", dir.resolve("cache").toString());
        try {
            Files.writeString(dir.resolve("book.dtd"), "<!ELEMENT book (title, chapter+)>\n<!ELEMENT title (#PCDATA)>\n<!ELEMENT chapter (#PCDATA)>\n");
            Path xml = Files.writeString(dir.resolve("book.xml"), "<!DOCTYPE book SYSTEM \"book.dtd\">\n<book><title>T</title></book>");
            openAndWait(xml, "book.dtd");
            WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> validChildren().contains("chapter"));
            assertTrue(validChildren().contains("title"));
        } finally {
            System.clearProperty("fxt.grammarXsd.dir");
        }
    }

    @Test
    void xmlModelBindsRelaxNgCompact(@TempDir Path dir) throws Exception {
        System.setProperty("fxt.grammarXsd.dir", dir.resolve("cache").toString());
        try {
            Files.writeString(dir.resolve("book.rnc"), "start = element book { element title { text }, element chapter { text }+ }\n");
            Path xml = Files.writeString(dir.resolve("book.xml"),
                    "<?xml-model href=\"book.rnc\" type=\"application/relax-ng-compact-syntax\"?>\n<book/>");
            openAndWait(xml, "book.rnc");
            WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> validChildren().contains("chapter"));
        } finally {
            System.clearProperty("fxt.grammarXsd.dir");
        }
    }
}
