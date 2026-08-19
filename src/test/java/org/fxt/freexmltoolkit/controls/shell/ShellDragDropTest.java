package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ShellDragDropTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = WaitForAsyncUtils.waitForAsyncFx(3000, UnifiedShellView::new);
    }

    @Test
    void acceptsXmlFamilyFilesOnly() {
        assertTrue(UnifiedShellView.acceptsDrop(List.of(new File("a.xml"))));
        assertTrue(UnifiedShellView.acceptsDrop(List.of(new File("a.xsd"))));
        assertFalse(UnifiedShellView.acceptsDrop(List.of(new File("a.png"))));
        assertFalse(UnifiedShellView.acceptsDrop(List.of()));
    }

    @Test
    void droppingAnXsdOnTheStatusBarSchemaIndicatorBindsIt(@TempDir Path tmp) throws Exception {
        java.util.concurrent.TimeUnit SECONDS = java.util.concurrent.TimeUnit.SECONDS;
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.openDroppedFiles(List.of(xml.toFile())));
        WaitForAsyncUtils.waitFor(3, SECONDS, () -> shell.getEditorHost()
                .getActiveText().map(t -> t.contains("root")).orElse(false));

        javafx.scene.control.Label indicator =
                (javafx.scene.control.Label) shell.lookup("#status-schema");
        assertNotNull(indicator, "the status bar must show the XSD indicator");
        assertNotNull(indicator.getOnDragOver(), "the XSD indicator must accept file drags");

        Path xsd = tmp.resolve("dropped.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(List.of(xsd.toFile()));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            indicator.getOnDragDropped().handle(event);
            return null;
        });

        assertEquals(xsd.toFile(), shell.getEditorHost().activeSchemaProperty().get(),
                "dropping must bind the XSD like clicking the indicator and choosing it");
        org.mockito.Mockito.verify(event).setDropCompleted(true);
    }

    @Test
    void droppingAJsonSchemaOnTheIndicatorBindsItForJsonDocuments(@TempDir Path tmp) throws Exception {
        java.util.concurrent.TimeUnit SECONDS = java.util.concurrent.TimeUnit.SECONDS;
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{\"a\": 1}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.openFile(json);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, SECONDS, () -> shell.getEditorHost()
                .getActiveText().map(t -> t.contains("\"a\"")).orElse(false));

        javafx.scene.control.Label indicator =
                (javafx.scene.control.Label) shell.lookup("#status-schema");
        assertNotNull(indicator, "the status bar must show the schema indicator");

        // A dropped .xsd does not match the JSON document's kind — it must be ignored.
        Path xsd = tmp.resolve("wrong.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        dropOn(indicator, xsd.toFile());
        assertEquals(null, shell.getEditorHost().activeSchemaProperty().get(),
                "an XSD dropped on a JSON document must not bind");

        Path schema = tmp.resolve("dropped-schema.json");
        Files.writeString(schema, "{\"type\": \"object\"}");
        dropOn(indicator, schema.toFile());
        assertEquals(schema.toFile(), shell.getEditorHost().activeSchemaProperty().get(),
                "dropping a JSON Schema must bind it to the JSON document");
    }

    private static void dropOn(javafx.scene.control.Label indicator, File file) throws Exception {
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(List.of(file));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            indicator.getOnDragDropped().handle(event);
            return null;
        });
    }

    @Test
    void openDroppedFilesOpensSupportedFiles(@TempDir Path tmp) throws Exception {
        File xml = tmp.resolve("dropped.xml").toFile();
        Files.writeString(xml.toPath(), "<root/>");
        File png = tmp.resolve("ignored.png").toFile();
        Files.writeString(png.toPath(), "x");

        int opened = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.openDroppedFiles(List.of(xml, png)));
        assertEquals(1, opened, "only the XML file is opened");
    }
}
