package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.fxt.freexmltoolkit.controls.v2.view.render.DiagramExportMenu;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** File → Export View… / Edit → Copy View as Image: enabled state, dialog, file and the views' menus. */
@ExtendWith(ApplicationExtension.class)
class ViewExportTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Catalog">
                <xs:complexType><xs:sequence>
                  <xs:element name="Product" type="xs:string" maxOccurs="unbounded"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    private UnifiedShellView shell;
    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        host = shell.getEditorHost();
        stage.setScene(new Scene(shell, 1200, 720));
        stage.show();
    }

    @AfterEach
    void resetHooks() {
        ViewExport.setDialogHandler(null);
        ViewExport.setFileChooser(null);
    }

    private MenuItem item(String id) {
        return shell.getMenuBar().getMenus().stream().flatMap(m -> m.getItems().stream())
                .filter(i -> id.equals(i.getId())).findFirst().orElseThrow();
    }

    private void openSchemaInGraphicView() throws Exception {
        Path schema = dir.resolve("catalog.xsd");
        Files.writeString(schema, XSD, StandardCharsets.UTF_8);
        WaitForAsyncUtils.asyncFx(() -> host.openFile(schema)).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.getActiveDocument().isPresent()
                && host.activeSupportsView(ViewMode.GRAPHIC));
        // The text is read in the background; switch (again) until the diagram stands
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> {
            if (!(ViewExport.activeView(host) instanceof XsdGraphView)) {
                host.setActiveViewMode(ViewMode.GRAPHIC);
            }
            return ViewExport.activeView(host) instanceof XsdGraphView;
        }).get(5, TimeUnit.SECONDS));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void itemsAreEnabledOnlyWhileAGraphicalViewIsShown() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(item("menu-file-export-view").isDisable(), "nothing to export without a document");
        assertTrue(item("menu-edit-copy-view").isDisable());

        openSchemaInGraphicView();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !item("menu-file-export-view").isDisable());
        assertFalse(item("menu-edit-copy-view").isDisable());

        WaitForAsyncUtils.asyncFx(() -> host.setActiveViewMode(ViewMode.TEXT)).get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> item("menu-file-export-view").isDisable());
        assertNull(ViewExport.activeView(host), "the text view is no graphical view");
    }

    @Test
    void exportAsksForOptionsAndFileAndWritesIt() throws Exception {
        openSchemaInGraphicView();
        AtomicReference<Path> proposed = new AtomicReference<>();
        Path target = dir.resolve("out").resolve("diagram"); // no extension: it is added
        Files.createDirectories(target.getParent());
        ViewExport.setDialogHandler(dialog -> {
            DiagramExportOptions options = dialog.currentOptions();
            return Optional.of(new DiagramExportOptions(DiagramExportOptions.Format.SVG, options.area(), 1,
                    options.theme(), options.background(), 8, false, options.pageSize(), options.title()));
        });
        ViewExport.setFileChooser((owner, proposal) -> {
            proposed.set(proposal);
            return target;
        });

        Path written = WaitForAsyncUtils.asyncFx(() -> ViewExport.export(ViewExport.activeView(host),
                ViewExport.baseName(host, ViewExport.activeView(host)), dir, null))
                .get(5, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);

        assertEquals("catalog.svg", proposed.get().getFileName().toString(), "named after the document");
        assertEquals(target.resolveSibling("diagram.svg"), written);
        String svg = Files.readString(written, StandardCharsets.UTF_8);
        assertTrue(svg.contains(">Catalog<"), svg.substring(0, Math.min(400, svg.length())));
        assertTrue(svg.contains("<title>catalog</title>"));
        assertEquals(DiagramExportOptions.Format.SVG, ViewExport.loadOptions().format(), "the choice is remembered");
    }

    @Test
    void cancellingTheDialogWritesNothing() throws Exception {
        openSchemaInGraphicView();
        ViewExport.setDialogHandler(dialog -> Optional.empty());
        ViewExport.setFileChooser((owner, proposal) -> {
            throw new AssertionError("no file is asked for after a cancel");
        });
        Path result = WaitForAsyncUtils.asyncFx(() -> ViewExport.export(ViewExport.activeView(host), "x", dir, null))
                .get(5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
        assertNull(result);
    }

    @Test
    void dialogShowsTheSizeAndOnlyTheRowsOfTheFormat() throws Exception {
        openSchemaInGraphicView();
        ExportViewDialog dialog = WaitForAsyncUtils.asyncFx(() -> new ExportViewDialog(ViewExport.activeView(host),
                DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG), "catalog")).get(5, TimeUnit.SECONDS);
        javafx.scene.Node content = dialog.getDialogPane().getContent();
        javafx.scene.control.Label size = (javafx.scene.control.Label) content.lookup("#export-view-size");
        assertTrue(size.getText().contains("×"), size.getText());
        assertTrue(content.lookup("#export-view-scale").isVisible());
        assertFalse(content.lookup("#export-view-page-size").isVisible());

        WaitForAsyncUtils.asyncFx(() -> dialog.setOptions(DiagramExportOptions.defaults(DiagramExportOptions.Format.PDF)))
                .get(5, TimeUnit.SECONDS);
        assertFalse(content.lookup("#export-view-scale").isVisible());
        assertTrue(content.lookup("#export-view-page-size").isVisible());
        assertEquals(DiagramExportOptions.Format.PDF, dialog.currentOptions().format());
        assertEquals("catalog", dialog.currentOptions().title());
    }

    @Test
    void theDiagramsContextMenuOffersExportAndCopy() throws Exception {
        openSchemaInGraphicView();
        ExportableDiagram view = ViewExport.activeView(host);
        var items = WaitForAsyncUtils.asyncFx(() -> DiagramExportMenu.items(view, (javafx.scene.Node) view))
                .get(5, TimeUnit.SECONDS);
        assertEquals(3, items.size(), "separator, export, copy (the shell registered the handler)");
        assertNotNull(items.get(1).getGraphic());
        assertEquals("diagram-export-view", items.get(1).getId());
        assertEquals("diagram-copy-view", items.get(2).getId());
    }

    @Test
    void fileNamesLoseCharactersFileSystemsReject() {
        assertEquals("a_b_c", ViewExport.safeFileName("a/b:c"));
        assertEquals("diagram", ViewExport.safeFileName("  "));
    }
}
