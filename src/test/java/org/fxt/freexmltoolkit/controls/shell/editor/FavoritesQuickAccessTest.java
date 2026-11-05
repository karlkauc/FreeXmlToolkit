package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The type-independent Favorites quick access menu ({@link FavoritesMenu#allItems},
 * {@link FavoritesMenu#quickAccessItems}) behind the header star, the Explorer star and
 * File → Open Favorite.
 */
@ExtendWith(ApplicationExtension.class)
class FavoritesQuickAccessTest {

    @TempDir
    Path tmp;

    @Start
    void start(Stage stage) {
        // toolkit only
    }

    @BeforeEach
    void emptyStore() {
        FavoritesTestSupport.purgeAll();
    }

    @AfterEach
    void cleanup() {
        FavoritesTestSupport.purgeAll();
    }

    @Test
    void allItemsListsEveryTypeWithTypeIconsAndSkipsMissingFiles() throws Exception {
        Path xml = Files.writeString(tmp.resolve("order.xml"), "<o/>");
        Path xsl = Files.writeString(tmp.resolve("style.xsl"), "<x/>");
        Path gone = Files.writeString(tmp.resolve("gone.xsd"), "<s/>");
        FavoritesService svc = FavoritesService.getInstance();
        svc.addFavorite(xml.toFile());
        svc.addFavorite(xsl.toFile());
        svc.addFavorite(gone.toFile());
        Files.delete(gone);

        List<File> picked = new ArrayList<>();
        List<MenuItem> items = onFx(() -> FavoritesMenu.allItems(picked::add));

        assertEquals(List.of("order", "style"), items.stream().map(MenuItem::getText).toList(),
                "all types, the missing file is left out");
        items.forEach(i -> assertInstanceOf(IconifyIcon.class, i.getGraphic(), "type icon: " + i.getText()));
        items.getFirst().fire();
        assertEquals(List.of(xml.toFile().getAbsoluteFile()), picked);
    }

    @Test
    void allItemsGroupsByFolder() throws Exception {
        Path a = Files.writeString(tmp.resolve("a.xml"), "<a/>");
        Path b = Files.writeString(tmp.resolve("b.xml"), "<b/>");
        FavoritesService svc = FavoritesService.getInstance();
        svc.addFavorite(a.toString(), "A", "Project X");
        svc.addFavorite(b.toString(), "B", null);

        List<MenuItem> items = onFx(() -> FavoritesMenu.allItems(f -> { }));

        List<String> folders = items.stream().map(MenuItem::getText).toList();
        assertTrue(folders.containsAll(List.of("Project X", "Uncategorized")), "folders were " + folders);
        Menu project = (Menu) items.stream().filter(i -> "Project X".equals(i.getText())).findFirst().orElseThrow();
        assertEquals(List.of("A"), FavoritesMenu.leafNames(project));
    }

    @Test
    void quickAccessOffersAddAndManageAndRecordsTheAccess() throws Exception {
        Path xml = Files.writeString(tmp.resolve("data.xml"), "<d/>");
        FavoritesService.getInstance().addFavorite(xml.toFile());
        List<File> opened = new ArrayList<>();
        AtomicBoolean added = new AtomicBoolean();
        AtomicBoolean managed = new AtomicBoolean();
        var actions = new FavoritesMenu.QuickAccess(opened::add, () -> added.set(true), () -> false,
                () -> managed.set(true));

        List<MenuItem> items = onFx(() -> FavoritesMenu.quickAccessItems(actions));

        assertEquals("data", items.get(0).getText());
        assertInstanceOf(SeparatorMenuItem.class, items.get(1));
        MenuItem add = byId(items, "favorites-quick-add");
        assertTrue(add.isDisable(), "nothing saved is active");
        int before = FavoritesService.getInstance().getAllFavorites().getFirst().getAccessCount();
        onFx(() -> {
            items.get(0).fire();
            byId(items, "favorites-quick-manage").fire();
            return null;
        });
        assertEquals(List.of(xml.toFile().getAbsoluteFile()), opened);
        assertTrue(managed.get());
        assertFalse(added.get());
        assertEquals(before + 1, FavoritesService.getInstance().getAllFavorites().getFirst().getAccessCount(),
                "opening a favorite counts as an access");
    }

    @Test
    void quickAccessWithoutFavoritesShowsADisabledHint() {
        var actions = new FavoritesMenu.QuickAccess(f -> fail("nothing to open"), () -> { }, () -> true, () -> { });
        List<MenuItem> items = onFx(() -> FavoritesMenu.quickAccessItems(actions));
        assertTrue(byId(items, "favorites-quick-none").isDisable());
        assertFalse(byId(items, "favorites-quick-add").isDisable(), "a saved document is active");
    }

    @Test
    void quickAccessButtonRebuildsItsItemsEachTimeItOpens() throws Exception {
        var actions = new FavoritesMenu.QuickAccess(f -> { }, () -> { }, () -> false, () -> { });
        MenuButton button = onFx(() -> FavoritesMenu.quickAccessButton("fav", "fxt-sp-action", 14, actions));
        assertTrue(button.getStyleClass().contains(FavoritesMenu.QUICK_ACCESS_STYLE));
        assertNotNull(byId(button.getItems(), "favorites-quick-none"));

        Path xml = Files.writeString(tmp.resolve("later.xml"), "<l/>");
        FavoritesService.getInstance().addFavorite(xml.toFile());
        onFx(() -> {
            button.getOnShowing().handle(new javafx.event.Event(MenuButton.ON_SHOWING));
            return null;
        });
        assertEquals("later", button.getItems().getFirst().getText(), "a favorite added later appears");
    }

    private static MenuItem byId(List<MenuItem> items, String id) {
        return items.stream().filter(i -> id.equals(i.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("no item " + id));
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
