package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer's XSD bar: the picker mirrors the schema bound to the
 * active document, picking/dropping an XSD binds it and triggers the
 * shell-wired validation, and the menu offers recents/favorites/unbind.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelXsdTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"/>
            </xs:schema>
            """;

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance().clearRecentXsdFiles();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    private void openXml(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
    }

    private File xsd(Path tmp, String name) throws Exception {
        return Files.writeString(tmp.resolve(name), XSD).toFile();
    }

    @Test
    void pickerMirrorsTheSchemaBoundToTheActiveDocument(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-xsd");
        assertNotNull(picker, "the Explorer must offer an XSD picker");
        assertEquals("XSD…", picker.getText(), "no document → placeholder");

        openXml(tmp);
        File schema = xsd(tmp, "bound.xsd");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(schema));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("bound.xsd", picker.getText(),
                "the picker follows the host's active schema, even when bound elsewhere");
    }

    @Test
    void pickingAnXsdBindsItRecordsItAndTriggersValidation(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-xsd");
        AtomicInteger validations = new AtomicInteger();
        panel.setXsdValidateAction(validations::incrementAndGet);

        openXml(tmp);
        File schema = xsd(tmp, "picked.xsd");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useXsd(schema);
            return null;
        });

        assertEquals("picked.xsd", picker.getText());
        assertEquals(schema, host.activeSchemaProperty().get(),
                "picking must bind the XSD to the active document");
        assertEquals(1, validations.get(), "changing the XSD must run validation");
        assertTrue(org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                        .getRecentXsdFiles().contains(schema.getAbsoluteFile()),
                "picking must record the file in the shared recent store");
    }

    @Test
    void unbindClearsTheSchemaAndRevalidates(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-xsd");
        AtomicInteger validations = new AtomicInteger();
        panel.setXsdValidateAction(validations::incrementAndGet);

        openXml(tmp);
        File schema = xsd(tmp, "bound.xsd");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useXsd(schema);
            panel.unbindXsd();
            return null;
        });

        assertNull(host.activeSchemaProperty().get(), "unbind must clear the schema");
        assertEquals("XSD…", picker.getText());
        assertEquals(2, validations.get(), "unbinding re-runs validation too");
    }

    @Test
    void menuOffersRecentsFavoritesChooseUnbindAndClear(@TempDir Path tmp) throws Exception {
        openXml(tmp);
        File schema = xsd(tmp, "recent.xsd");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useXsd(schema);
            return null;
        });

        List<String> texts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.xsdMenuItemTexts());
        assertTrue(texts.contains("recent.xsd"), "menu lists recents, was: " + texts);
        assertTrue(texts.contains("Favorites"), texts.toString());
        assertTrue(texts.contains("Choose XSD…"), texts.toString());
        assertTrue(texts.contains("Unbind schema"), texts.toString());
        assertTrue(texts.contains("Clear recent"), texts.toString());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.clearRecentXsd();
            return null;
        });
        assertTrue(org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .getRecentXsdFiles().isEmpty());
    }

    @Test
    void droppingAnXsdOnThePickerBindsIt(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-xsd");
        assertNotNull(picker.getOnDragOver(), "the picker must accept file drags");
        AtomicInteger validations = new AtomicInteger();
        panel.setXsdValidateAction(validations::incrementAndGet);

        openXml(tmp);
        File schema = xsd(tmp, "dropped.xsd");
        javafx.scene.input.DragEvent event = dropEventWithFiles(schema);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            picker.getOnDragDropped().handle(event);
            return null;
        });

        assertEquals("dropped.xsd", picker.getText());
        assertEquals(schema, host.activeSchemaProperty().get());
        assertEquals(1, validations.get());
        org.mockito.Mockito.verify(event).setDropCompleted(true);
    }

    @Test
    void droppingAFileWithWrongExtensionIsRejected(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-xsd");
        Path txt = tmp.resolve("notes.txt");
        Files.writeString(txt, "not a schema");

        javafx.scene.input.DragEvent event = dropEventWithFiles(txt.toFile());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            picker.getOnDragDropped().handle(event);
            return null;
        });

        assertEquals("XSD…", picker.getText());
        org.mockito.Mockito.verify(event).setDropCompleted(false);
    }

    /** Mocks a DRAG_DROPPED event carrying OS files (TestFX cannot simulate real file drags). */
    private static javafx.scene.input.DragEvent dropEventWithFiles(File... files) {
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(List.of(files));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);
        return event;
    }
}
