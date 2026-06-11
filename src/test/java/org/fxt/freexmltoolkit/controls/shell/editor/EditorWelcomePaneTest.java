package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Welcome / Dashboard landing (Figma "Redesign · Unified — Welcome /
 * Dashboard"): a hero, quick-action cards, a recent-files list with Clear, and a
 * Tools grid whose cards switch activities via stable activity ids.
 */
@ExtendWith(ApplicationExtension.class)
class EditorWelcomePaneTest {

    private final AtomicReference<EditorFileType> newType = new AtomicReference<>();
    private final AtomicReference<Boolean> openCalled = new AtomicReference<>(false);
    private final AtomicReference<File> openedRecent = new AtomicReference<>();
    private final AtomicReference<Boolean> clearCalled = new AtomicReference<>(false);
    private final AtomicReference<String> action = new AtomicReference<>();
    private EditorWelcomePane pane;

    @Start
    void start(Stage stage) {
        pane = new EditorWelcomePane(newType::set, () -> openCalled.set(true), openedRecent::set,
                () -> clearCalled.set(true), action::set);
        stage.setScene(new Scene(pane, 1200, 760));
        stage.show();
    }

    @Test
    void carriesTheEmptyStateStyleClass() {
        assertTrue(pane.getStyleClass().contains("fxt-editor-empty-state"));
    }

    @Test
    void showsTheHeroTitle() {
        assertTrue(hasLabel("Welcome to FreeXmlToolkit"), "the hero title must be shown");
    }

    @Test
    void newFileCardInvokesTheNewCallback() {
        fire("#welcome-new");
        assertEquals(EditorFileType.XML, newType.get());
    }

    @Test
    void openFileCardInvokesTheOpenCallback() {
        fire("#welcome-open");
        assertTrue(openCalled.get());
    }

    @Test
    void toolCardSwitchesActivityById() {
        fire("#tool-validation");
        assertEquals("validation", action.get(), "the Validate tool must request the validation activity");
    }

    @Test
    void schemaToolCardSwitchesToSchema() {
        fire("#tool-schema");
        assertEquals("schema", action.get());
    }

    @Test
    void explorerToolCardSwitchesToExplorer() {
        fire("#tool-explorer");
        assertEquals("explorer", action.get(), "the Explorer card must request the explorer activity");
    }

    @Test
    void settingsToolCardSwitchesToSettings() {
        fire("#tool-settings");
        assertEquals("settings", action.get(), "the Settings card must request the settings activity");
    }

    @Test
    void clearLinkInvokesTheClearCallback() {
        fire("#welcome-clear");
        assertTrue(clearCalled.get());
    }

    @Test
    void clickingARecentEntryOpensIt() {
        File recent = new File("/tmp/sample.xml");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            pane.setRecentFiles(List.of(recent));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            ListView<File> list = (ListView<File>) pane.lookup(".fxt-welcome-recent");
            list.getSelectionModel().select(recent);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(recent, openedRecent.get());
    }

    @Test
    void showsDataBackedStatCards() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            pane.setStats(new EditorWelcomePane.WelcomeStats(7, 3, 12, 5));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("7", statText("#welcome-stat-recent"));
        assertEquals("3", statText("#welcome-stat-favorites"));
        assertEquals("12", statText("#welcome-stat-templates"));
        assertEquals("5", statText("#welcome-stat-queries"));
        assertTrue(hasLabel("Recent files") && hasLabel("Favorites") && hasLabel("Templates"),
                "stat card labels must be present");
    }

    private String statText(String id) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> ((Label) pane.lookup(id)).getText());
    }

    private void fire(String id) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ButtonBase b = (ButtonBase) pane.lookup(id);
            assertNotNull(b, "missing actionable node: " + id);
            b.fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean hasLabel(String text) {
        for (Node n : pane.lookupAll(".label")) {
            if (n instanceof Label l && text.equals(l.getText())) {
                return true;
            }
        }
        return false;
    }
}
