package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SchemaLibraryPanelTest {

    private static final String BUNDLED = """
            {"version":1,"entries":[{"namespace":"urn:bundled","location":"https://example.org/b.xsd","kind":"XSD","description":"B"}]}""";

    private Path dir;
    private SchemaLibraryServiceImpl library;
    private SchemaResourceCache cache;
    private SchemaLibraryPanel panel;
    private EditorHost host;

    @Start
    void start(Stage stage) throws Exception {
        dir = Files.createTempDirectory("schema-library-panel");
        ServiceRegistry.initialize();
        cache = new SchemaResourceCache(dir.resolve("cache"));
        library = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), cache,
                () -> new ByteArrayInputStream(BUNDLED.getBytes()));
        host = new EditorHost();
        panel = new SchemaLibraryPanel(host, library, cache, ServiceRegistry.get(XmlService.class));
        stage.setScene(new Scene(panel, 480, 640));
        stage.show();
    }

    @AfterEach
    void tearDown() throws Exception {
        ServiceRegistry.reset();
        org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
    }

    @SuppressWarnings("unchecked")
    private TableView<SchemaLibraryEntry> table(FxRobot robot) {
        return robot.lookup("#library-mappings-table").queryAs(TableView.class);
    }

    @Test
    void showsBundledEntriesAndReflectsServiceChanges(FxRobot robot) throws Exception {
        assertEquals(1, table(robot).getItems().size());
        Path xsd = dir.resolve("u.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:u'/>");
        library.addEntry(SchemaLibraryEntry.user("urn:u", xsd.toString(), SchemaKind.XSD, "mine", null));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, table(robot).getItems().size());
    }

    @Test
    void filterNarrowsRows(FxRobot robot) {
        library.addEntry(SchemaLibraryEntry.user("urn:zzz", dir.resolve("z.xsd").toString(), SchemaKind.XSD, "", null));
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#library-filter").write("zzz");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, table(robot).getItems().size());
        assertEquals("urn:zzz", table(robot).getItems().getFirst().namespace());
    }

    @Test
    void removeIsDisabledForBundledAndRemovesUserEntry(FxRobot robot) {
        SchemaLibraryEntry user = library.addEntry(SchemaLibraryEntry.user("urn:u2", dir.resolve("u2.xsd").toString(), SchemaKind.XSD, "", null));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> table(robot).getSelectionModel().select(
                table(robot).getItems().stream().filter(e -> e.source() == EntrySource.BUNDLED).findFirst().orElseThrow()));
        assertTrue(robot.lookup("#library-remove").queryButton().isDisabled());
        robot.interact(() -> table(robot).getSelectionModel().select(
                table(robot).getItems().stream().filter(e -> e.id().equals(user.id())).findFirst().orElseThrow()));
        assertFalse(robot.lookup("#library-remove").queryButton().isDisabled());
        robot.interact(() -> panel.removeSelectedWithoutConfirm());
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(library.getEntries().stream().noneMatch(e -> e.id().equals(user.id())));
    }

    @Test
    void toggleFlipsEnabledWithoutException(FxRobot robot) {
        SchemaLibraryEntry user = library.addEntry(SchemaLibraryEntry.user("urn:u3", dir.resolve("u3.xsd").toString(), SchemaKind.XSD, "", null));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(user.enabled());
        robot.interact(() -> table(robot).getSelectionModel().select(
                table(robot).getItems().stream().filter(e -> e.id().equals(user.id())).findFirst().orElseThrow()));
        robot.clickOn("#library-toggle");
        // Surfaces any FX-thread exception thrown by the toggle action (e.g. the TableView
        // setAll-while-selected crash trap when the observable list is re-set with a row selected).
        WaitForAsyncUtils.waitForFxEvents();
        boolean nowEnabled = library.getEntries().stream()
                .filter(e -> e.id().equals(user.id())).findFirst().orElseThrow().enabled();
        assertFalse(nowEnabled);
    }

    @Test
    void catalogsTabListsRegisteredCatalogsWithEntryCounts(FxRobot robot) throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<uri name='urn:c1' uri='c1.xsd'/><uri name='urn:c2' uri='c2.xsd'/></catalog>");
        robot.interact(() -> panel.addCatalogFile(cat));
        WaitForAsyncUtils.waitForFxEvents();
        ListView<SchemaCatalogRef> list = robot.lookup("#library-catalogs-list").queryAs(ListView.class);
        assertEquals(1, list.getItems().size());
        assertEquals(2, library.catalogEntryCount(list.getItems().getFirst().id()));
        assertTrue(robot.lookup(".fxt-lib-catalog-count").queryLabeled().getText().contains("2"));
    }

    @Test
    void unparsableCatalogShowsError(FxRobot robot) throws Exception {
        Path bad = dir.resolve("bad.xml");
        Files.writeString(bad, "<catalog");
        robot.interact(() -> panel.addCatalogFile(bad));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(robot.lookup(".fxt-lib-catalog-error").queryAll().isEmpty());
    }
}
