package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Button;
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
 * Verifies the Explorer's Schematron bar: the sticky picker (label, menu
 * entries, active-document binding, recents) and the Validate button's
 * delegation to the shell-wired callback.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelSchematronTest {

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        // A clean recent store so label/menu assertions are deterministic.
        org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .clearRecentSchematronFiles();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void pickerStartsUnsetAndShowsTheChosenSchematron(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-schematron");
        assertNotNull(picker, "the Explorer must offer a Schematron picker");
        assertEquals("Schematron…", picker.getText(), "unset picker shows the placeholder");

        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<sch:schema xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\"/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            return null;
        });

        assertEquals("rules.sch", picker.getText(), "picker label follows the chosen file");
        assertEquals(sch.toFile(), host.getActiveSchematron(),
                "picking must bind the Schematron to the active document");
        assertTrue(org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                        .getRecentSchematronFiles().contains(sch.toFile().getAbsoluteFile()),
                "picking must record the file in the shared recent store");
    }

    @Test
    void menuOffersRecentsFavoritesChooseAndClear(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("recent-rules.sch");
        Files.writeString(sch, "<x/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            return null;
        });

        List<String> texts = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> panel.schematronMenuItemTexts());
        assertTrue(texts.contains("recent-rules.sch"), "menu lists recents, was: " + texts);
        assertTrue(texts.contains("Favorites"), "menu offers the Favorites submenu, was: " + texts);
        assertTrue(texts.contains("Choose Schematron…"), texts.toString());
        assertTrue(texts.contains("Clear recent"), texts.toString());
    }

    @Test
    void clearRecentResetsThePickerLabel(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("gone.sch");
        Files.writeString(sch, "<x/>");
        MenuButton picker = (MenuButton) panel.lookup("#explorer-schematron");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            panel.schematronMenuItemTexts(); // rebuild the menu items
            panel.clearRecentSchematron();
            return null;
        });
        assertEquals("Schematron…", picker.getText(), "clearing resets the sticky choice");
        assertTrue(org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .getRecentSchematronFiles().isEmpty());
    }

    @Test
    void droppingASchematronFileOnThePickerBindsIt(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-schematron");
        assertNotNull(picker.getOnDragOver(), "the picker must accept file drags");

        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("dropped.sch");
        Files.writeString(sch, "<x/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        javafx.scene.input.DragEvent event = dropEventWithFiles(sch.toFile());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            picker.getOnDragDropped().handle(event);
            return null;
        });

        assertEquals("dropped.sch", picker.getText(), "picker label follows the dropped file");
        assertEquals(sch.toFile(), host.getActiveSchematron(),
                "dropping must bind the Schematron like picking it");
        org.mockito.Mockito.verify(event).setDropCompleted(true);
    }

    @Test
    void droppingAFileWithWrongExtensionOnThePickerIsRejected(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-schematron");
        Path txt = tmp.resolve("notes.txt");
        Files.writeString(txt, "not a schematron");

        javafx.scene.input.DragEvent event = dropEventWithFiles(txt.toFile());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            picker.getOnDragDropped().handle(event);
            return null;
        });

        assertEquals("Schematron…", picker.getText(), "a rejected drop must not change the picker");
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

    @Test
    void validateButtonDelegatesActiveDocumentAndSchematron(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("active.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<x/>");

        AtomicReference<List<File>> gotFiles = new AtomicReference<>();
        AtomicReference<File> gotSchematron = new AtomicReference<>();
        panel.setSchematronValidateAction((files, schematron) -> {
            gotFiles.set(files);
            gotSchematron.set(schematron);
        });

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            ((Button) panel.lookup("#explorer-validate")).fire();
            return null;
        });

        assertNotNull(gotFiles.get(), "Validate must invoke the shell-wired action");
        assertEquals(List.of(xml.toFile()), gotFiles.get(),
                "with no tree selection the active document is the fallback input");
        assertEquals(sch.toFile(), gotSchematron.get());
    }
}
