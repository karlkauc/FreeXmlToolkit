package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the editor tab's right-click menu ({@link EditorTabMenu}) and of tab
 * reordering: every action targets the right-clicked tab, not the active one.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostTabMenuTest {

    private EditorHost host;
    private final List<String> reloadAsked = new ArrayList<>();
    private volatile boolean reloadAnswer;
    private final List<Path> revealed = new ArrayList<>();

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        host.setReloadConfirmer(doc -> {
            reloadAsked.add(doc.getDisplayName());
            return reloadAnswer;
        });
        host.setRevealInExplorerHandler(revealed::add);
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void everyTabGetsTheMenu(@TempDir Path tmp) throws Exception {
        open(tmp, "a.xml", "<a/>");
        Tab tool = fx(() -> host.openToolTab("Tool", "bi-gear", new Pane()));

        // ContextMenu.show() ignores a menu without items, so it must not start out empty.
        assertFalse(fx(() -> tabs().get(0).getContextMenu().getItems().isEmpty()));
        assertFalse(fx(() -> tool.getContextMenu().getItems().isEmpty()));
        assertEquals(List.of("tab-ctx-reload", "tab-ctx-close", "tab-ctx-close-others", "tab-ctx-save",
                        "tab-ctx-save-as", "tab-ctx-copy-path", "tab-ctx-reveal-os", "tab-ctx-reveal-explorer"),
                ids(tabs().get(0)));
        assertEquals(List.of("tab-ctx-close", "tab-ctx-close-others"), ids(tool),
                "a tab without a document only offers closing");
    }

    @Test
    void rightClickOnTheTabHeaderShowsTheMenu(org.testfx.api.FxRobot robot, @TempDir Path tmp) throws Exception {
        open(tmp, "a.xml", "<a/>");
        Tab tab = tabNamed("a.xml");

        // The context-menu request the platform sends for a right-click, on the skin's tab header.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            javafx.scene.Node header = robot.lookup(".tab").query();
            javafx.geometry.Point2D screen = header.localToScreen(5, 5);
            header.fireEvent(new javafx.scene.input.ContextMenuEvent(
                    javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5,
                    screen.getX(), screen.getY(), false, null));
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> tab.getContextMenu().isShowing()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.getContextMenu().hide());
    }

    @Test
    void closeOthersKeepsOnlyTheRightClickedTab(@TempDir Path tmp) throws Exception {
        open(tmp, "a.xml", "<a/>");
        open(tmp, "b.xml", "<b/>");
        open(tmp, "c.xml", "<c/>");
        Tab b = tabNamed("b.xml");

        run(b, "tab-ctx-close-others");

        assertEquals(List.of(b), fx(() -> List.copyOf(tabs())));
        assertEquals(List.of("b.xml"), docNames());
        assertTrue(item(b, "tab-ctx-close-others").isDisable(), "nothing else left to close");
    }

    @Test
    void closeClosesTheRightClickedTabNotTheActiveOne(@TempDir Path tmp) throws Exception {
        open(tmp, "a.xml", "<a/>");
        open(tmp, "b.xml", "<b/>"); // active

        run(tabNamed("a.xml"), "tab-ctx-close");

        assertEquals(List.of("b.xml"), docNames());
    }

    @Test
    void copyPathPutsTheAbsolutePathOnTheClipboard(@TempDir Path tmp) throws Exception {
        Path a = open(tmp, "a.xml", "<a/>");
        open(tmp, "b.xml", "<b/>");

        run(tabNamed("a.xml"), "tab-ctx-copy-path");

        assertEquals(a.toAbsolutePath().toString(), fx(() -> Clipboard.getSystemClipboard().getString()));
    }

    @Test
    void revealInExplorerHandsTheFileToTheShell(@TempDir Path tmp) throws Exception {
        Path a = open(tmp, "a.xml", "<a/>");

        run(tabNamed("a.xml"), "tab-ctx-reveal-explorer");

        assertEquals(List.of(a.toAbsolutePath()), revealed);
    }

    @Test
    void reloadOfACleanTabReadsTheDiskWithoutAsking(@TempDir Path tmp) throws Exception {
        Path a = open(tmp, "a.xml", "<a/>");
        Files.writeString(a, "<a-changed/>");

        run(tabNamed("a.xml"), "tab-ctx-reload");

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> "<a-changed/>".equals(activeText()));
        assertTrue(reloadAsked.isEmpty());
    }

    @Test
    void reloadOfADirtyTabAsksFirstAndKeepsTheTextOnNo(@TempDir Path tmp) throws Exception {
        Path a = open(tmp, "a.xml", "<a/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<mine/>"));
        Files.writeString(a, "<disk/>");

        reloadAnswer = false;
        run(tabNamed("a.xml"), "tab-ctx-reload");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("a.xml"), reloadAsked);
        assertEquals("<mine/>", activeText());

        reloadAnswer = true;
        run(tabNamed("a.xml"), "tab-ctx-reload");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> "<disk/>".equals(activeText()));
        assertFalse(fx(() -> host.getActiveDocument().orElseThrow().isDirty()));
    }

    @Test
    void saveWritesTheRightClickedTabEvenWhenAnotherIsActive(@TempDir Path tmp) throws Exception {
        Path a = open(tmp, "a.xml", "<a/>");
        Tab aTab = tabNamed("a.xml");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<a-edited/>"));
        open(tmp, "b.xml", "<b/>"); // now active
        assertFalse(item(aTab, "tab-ctx-save").isDisable());

        run(aTab, "tab-ctx-save");

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> "<a-edited/>".equals(Files.readString(a)));
        assertEquals("b.xml", fx(() -> host.getActiveDocument().orElseThrow().getDisplayName()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> item(aTab, "tab-ctx-save").isDisable());
    }

    @Test
    void fileActionsAreDisabledForAnUntitledDocument() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.newDocument(EditorFileType.XML));
        Tab tab = fx(() -> tabs().get(0));

        for (String id : List.of("tab-ctx-reload", "tab-ctx-copy-path", "tab-ctx-reveal-os",
                "tab-ctx-reveal-explorer")) {
            assertTrue(item(tab, id).isDisable(), id);
        }
        assertFalse(item(tab, "tab-ctx-save").isDisable(), "Save of an untitled document is a Save As");
        assertFalse(item(tab, "tab-ctx-save-as").isDisable());
    }

    @Test
    void movingATabKeepsItWorkingAndReordersTheOpenDocuments(@TempDir Path tmp) throws Exception {
        Path a = open(tmp, "a.xml", "<a/>");
        open(tmp, "b.xml", "<b/>");
        Tab aTab = tabNamed("a.xml");

        // As a programmatic move (remove + add); the skin's drag fires a permutation instead.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tabs().remove(aTab);
            tabs().add(aTab);
            aTab.getTabPane().getSelectionModel().select(aTab);
        });

        assertEquals(List.of("b.xml", "a.xml"), docNames());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<a-moved/>"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(30, TimeUnit.SECONDS));
        assertEquals("<a-moved/>", Files.readString(a));
    }

    // ----- helpers ---------------------------------------------------------

    private Path open(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().filter(d -> file.equals(d.getPath()))
                        .map(d -> d.getDiskStamp() != null).orElse(false)));
        return file;
    }

    private List<Tab> tabs() {
        return host.tabsForTest();
    }

    private Tab tabNamed(String name) {
        return fx(() -> tabs().stream().filter(t -> t.getText() != null && t.getText().contains(name)).findFirst().orElseThrow());
    }

    private List<String> docNames() {
        return fx(() -> host.getOpenDocuments().stream().map(OpenDocument::getDisplayName).toList());
    }

    private List<String> ids(Tab tab) {
        return fx(() -> host.tabMenuItems(tab).stream().map(MenuItem::getId)
                .filter(java.util.Objects::nonNull).toList());
    }

    private MenuItem item(Tab tab, String id) {
        return fx(() -> host.tabMenuItems(tab).stream().filter(i -> id.equals(i.getId())).findFirst().orElseThrow());
    }

    private void run(Tab tab, String id) {
        MenuItem item = item(tab, id);
        assertFalse(item.isDisable(), id + " must be enabled");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> item.fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String activeText() {
        return fx(() -> host.getActiveText().orElse(null));
    }

    private static <T> T fx(java.util.concurrent.Callable<T> call) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, call);
    }
}
