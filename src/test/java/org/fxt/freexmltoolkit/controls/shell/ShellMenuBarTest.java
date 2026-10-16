package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The shell's menu bar: structure, icons, enabled state and the actions behind the items. */
@ExtendWith(ApplicationExtension.class)
class ShellMenuBarTest {

    private UnifiedShellView shell;
    private ShellMenuBar menuBar;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        menuBar = shell.getMenuBar();
        stage.setScene(new Scene(shell, 1280, 800));
        stage.show();
    }

    @Test
    void barIsPartOfTheShellWithTheFiveMenus() {
        assertSame(menuBar, shell.lookup("#shell-menu-bar"), "the menu bar sits in the shell chrome");
        assertEquals(List.of("menu-file", "menu-edit", "menu-view", "menu-tools", "menu-help"),
                menuBar.getMenus().stream().map(Menu::getId).toList());
    }

    @Test
    void everyItemHasTextIdAndIcon() {
        for (MenuItem item : menuBar.allItems()) {
            if (item instanceof SeparatorMenuItem) {
                continue;
            }
            assertNotNull(item.getText(), "menu item without text");
            assertFalse(item.getText().isBlank(), "menu item without text");
            assertInstanceOf(IconifyIcon.class, item.getGraphic(), "no icon: " + item.getText());
            if (item.getParentMenu() != item("menu-file-open-recent")) {
                assertNotNull(item.getId(), "no id: " + item.getText());
            }
        }
    }

    @Test
    void documentItemsAreDisabledWithoutADocumentAndEnabledWithOne() {
        String[] documentItems = {"menu-file-save", "menu-file-save-as", "menu-edit-undo", "menu-edit-find",
                "menu-edit-format", "menu-tools-validate", "menu-file-close-tab"};
        for (String id : documentItems) {
            assertTrue(item(id).isDisable(), id + " must be disabled without a document");
        }
        for (String id : new String[]{"menu-file-new", "menu-file-open", "menu-file-settings",
                "menu-view-side-panel", "menu-help-about"}) {
            assertFalse(item(id).isDisable(), id + " must always be enabled");
        }

        onFx(() -> shell.getEditorHost().newDocument(EditorFileType.XML));

        for (String id : documentItems) {
            assertFalse(item(id).isDisable(), id + " must be enabled for an XML document");
        }
        assertTrue(item("menu-tools-type-editor").isDisable(), "the Type Editor is for schemas only");
        assertTrue(item("menu-tools-run").isDisable(), "Run is only for query / XSLT / XProc documents, like the toolbar");
        assertFalse(item("menu-view-mode-text").isDisable(), "an XML document has a Text view");
    }

    @Test
    void panelItemsToggleThePanels() {
        boolean inspector = shell.isInspectorOpen();
        fire("menu-view-inspector");
        assertEquals(!inspector, shell.isInspectorOpen());
        fire("menu-view-inspector");
        assertEquals(inspector, shell.isInspectorOpen());

        assertFalse(shell.isQueryConsoleShown());
        fire("menu-view-query-console");
        assertTrue(shell.isQueryConsoleShown());
        fire("menu-view-query-console");
        assertFalse(shell.isQueryConsoleShown());
    }

    @Test
    void activityItemsSelectTheActivity() {
        assertNull(menuBar.allItems().stream()
                .filter(i -> "menu-view-activity-settings".equals(i.getId())).findFirst().orElse(null),
                "Settings has its own item in the File menu");
        fire("menu-view-activity-validation");
        assertEquals(Activity.VALIDATION, shell.getSelectionModel().getActive());
        assertTrue(shell.isLeftPanelOpen(), "choosing an activity reveals its side panel");
    }

    @Test
    void helpMenuReplacesTheHelpActivity() {
        assertTrue(menuBar.allItems().stream().noneMatch(i -> "menu-help-panel".equals(i.getId())));
        assertTrue(menuBar.allItems().stream().noneMatch(i -> "menu-view-activity-help".equals(i.getId())));
        assertNotNull(item("menu-help-sponsor"), "Sponsor moved from the Help panel into the menu");
        javafx.scene.control.ContextMenu popup = WaitForAsyncUtils.waitForAsyncFx(2000, menuBar::createHelpPopup);
        List<String> ids = popup.getItems().stream().map(MenuItem::getId).toList();
        assertTrue(ids.containsAll(List.of("help-popup-docs", "help-popup-shortcuts", "help-popup-github",
                "help-popup-sponsor", "help-popup-about")), ids.toString());
        // The FundsXML links follow the extension switch; never a doubled separator.
        assertEquals(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunner.isEnabled(),
                ids.contains("help-popup-fundsxml-site"));
        for (int i = 1; i < popup.getItems().size(); i++) {
            assertFalse(popup.getItems().get(i) instanceof SeparatorMenuItem
                    && popup.getItems().get(i - 1) instanceof SeparatorMenuItem, "doubled separator at " + i);
        }
        assertFalse(popup.getItems().getFirst() instanceof SeparatorMenuItem);
        assertFalse(popup.getItems().getLast() instanceof SeparatorMenuItem);
    }

    @Test
    void settingsItemOpensTheSettingsPage() {
        fire("menu-file-settings");
        assertNotNull(shell.lookup(".fxt-settings-page"));
    }

    @Test
    void closeTabClosesACleanDocument() {
        onFx(() -> shell.getEditorHost().newDocument(EditorFileType.XML));
        assertEquals(1, shell.getEditorHost().getOpenDocuments().size());
        fire("menu-file-close-tab");
        assertTrue(shell.getEditorHost().getOpenDocuments().isEmpty(), "Close Tab drops the document");
        assertTrue(item("menu-file-close-tab").isDisable());
    }

    @Test
    void ctrlWClosesTheActiveTabAndCtrlCommaOpensSettings() {
        onFx(() -> shell.getEditorHost().newDocument(EditorFileType.XML));
        fireShortcut(javafx.scene.input.KeyCode.W);
        assertTrue(shell.getEditorHost().getOpenDocuments().isEmpty(), "Ctrl+W closes the tab");

        fireShortcut(javafx.scene.input.KeyCode.COMMA);
        assertNotNull(shell.lookup(".fxt-settings-page"), "Ctrl+, opens the settings");
    }

    @Test
    void shortcutHandledByTheShellDoesNotAlsoFireItsMenuAccelerator() {
        // Ctrl+Shift+X is both a shell shortcut and the Query Console item's accelerator:
        // run twice it would open and close the console again.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.fireEvent(new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.X,
                true, true, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(shell.isQueryConsoleShown(), "the shortcut must run exactly once");
    }

    @Test
    void applicationItemsCanBeDetachedForTheMacApplicationMenu() {
        List<MenuItem> detached = WaitForAsyncUtils.waitForAsyncFx(2000, menuBar::detachApplicationItems);
        assertEquals(List.of("menu-help-about", "menu-help-updates", "menu-file-settings"),
                detached.stream().map(MenuItem::getId).toList());
        List<String> remaining = menuBar.allItems().stream().map(MenuItem::getId).toList();
        assertFalse(remaining.contains("menu-file-exit"), "Exit is the system's Quit on macOS");
        assertFalse(remaining.contains("menu-file-settings"));
        assertFalse(remaining.contains("menu-help-about"));
        for (Menu menu : menuBar.getMenus()) {
            List<MenuItem> items = menu.getItems();
            assertFalse(items.get(items.size() - 1) instanceof SeparatorMenuItem,
                    menu.getId() + " must not end with a separator");
        }
    }

    private MenuItem item(String id) {
        return menuBar.allItems().stream().filter(i -> id.equals(i.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("no menu item " + id));
    }

    private void fire(String id) {
        onFx(() -> item(id).fire());
    }

    private void fireShortcut(javafx.scene.input.KeyCode code) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.fireEvent(new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, false, true, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
