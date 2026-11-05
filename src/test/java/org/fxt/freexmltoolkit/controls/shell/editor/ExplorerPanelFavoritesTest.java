package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Explorer's favorites: the FAVORITES | RECENT tabs of the bottom pane (one tab always
 * selected, choice persisted, a click opens the file) and the header's quick access star.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelFavoritesTest {

    private EditorHost host;
    private ExplorerPanel panel;
    private Path favXml;
    private PropertiesService props;
    private String savedTab;

    @Start
    void start(Stage stage) throws Exception {
        ServiceRegistry.initialize();
        FavoritesTestSupport.purgeAll();
        props = ServiceRegistry.get(PropertiesService.class);
        savedTab = props.get(ExplorerPanel.FAV_RECENT_TAB_KEY);
        props.set(ExplorerPanel.FAV_RECENT_TAB_KEY, "");
        favXml = Files.createTempFile("fxt-explorer-fav-tab", ".xml");
        Files.writeString(favXml, "<fav/>");
        FavoritesService.getInstance().addFavorite(favXml.toFile());
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 700));
        stage.show();
    }

    @AfterEach
    void cleanup() throws Exception {
        FavoritesTestSupport.purgeAll();
        Files.deleteIfExists(favXml);
        props.set(ExplorerPanel.FAV_RECENT_TAB_KEY, savedTab != null ? savedTab : "");
    }

    @Test
    void favoritesTabIsTheDefaultWhileThereAreFavoritesAndListsThem() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(ExplorerPanel.FavRecentTab.FAVORITES, panel.getSelectedTab());
        ListView<?> list = (ListView<?>) panel.lookup("#explorer-favorites-list");
        assertTrue(list.isVisible());
        assertFalse(panel.lookup("#explorer-recent-list").isVisible());
        assertEquals(java.util.List.of(favXml.toString()),
                panel.getFavoriteItems().stream().map(FileFavorite::getFilePath).toList());
    }

    @Test
    void aTabCannotBeToggledOffAndTheChoiceIsPersisted(FxRobot robot) {
        robot.clickOn("#explorer-tab-recent");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(ExplorerPanel.FavRecentTab.RECENT, panel.getSelectedTab());
        assertEquals("recent", props.get(ExplorerPanel.FAV_RECENT_TAB_KEY));

        // Clicking the selected tab again keeps it selected.
        robot.clickOn("#explorer-tab-recent");
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(((ToggleButton) panel.lookup("#explorer-tab-recent")).isSelected());
        assertFalse(((ToggleButton) panel.lookup("#explorer-tab-favorites")).isSelected());

        robot.clickOn("#explorer-tab-favorites");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(ExplorerPanel.FavRecentTab.FAVORITES, panel.getSelectedTab());
        assertEquals("favorites", props.get(ExplorerPanel.FAV_RECENT_TAB_KEY));
    }

    @Test
    void clickingAFavoriteOpensIt(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn(robot.lookup("#explorer-favorites-list .list-cell").nth(0).queryAs(javafx.scene.Node.class));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> favXml.equals(d.getPath())));
        ListView<?> list = (ListView<?>) panel.lookup("#explorer-favorites-list");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> list.getSelectionModel().isEmpty());
    }

    @Test
    void refreshFavoritesPicksUpNewEntriesAndMarksMissingFiles() throws Exception {
        Path second = Files.createTempFile("fxt-explorer-fav-second", ".xsd");
        try {
            FavoritesService.getInstance().addFavorite(second.toFile());
            Files.delete(second);
            WaitForAsyncUtils.asyncFx(panel::refreshFavorites).get(5, TimeUnit.SECONDS);
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(2, panel.getFavoriteItems().size(), "a missing file stays listed (muted)");
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () ->
                    panel.lookupAll(".fxt-explorer-missing").size() == 1);
        } finally {
            Files.deleteIfExists(second);
        }
    }

    @Test
    void headerStarListsTheFavorites() {
        WaitForAsyncUtils.waitForFxEvents();
        MenuButton star = (MenuButton) panel.lookup("#explorer-favorites");
        assertNotNull(star, "the Explorer header has a Favorites star");
        String name = favXml.getFileName().toString().replaceFirst("\\.xml$", "");
        assertTrue(star.getItems().stream().anyMatch(i -> name.equals(i.getText())),
                "items were " + star.getItems().stream().map(i -> i.getText()).toList());
        assertTrue(star.getItems().stream().anyMatch(i -> "favorites-quick-manage".equals(i.getId())));
    }
}
