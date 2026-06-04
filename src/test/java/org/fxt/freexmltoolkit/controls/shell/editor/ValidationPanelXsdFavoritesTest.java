package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Validation panel offers a quick-select menu of favorite XSD schemas (#40). */
@ExtendWith(ApplicationExtension.class)
class ValidationPanelXsdFavoritesTest {

    private ValidationPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        EditorHost host = new EditorHost();
        panel = new ValidationPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @Test
    void listsFavoriteXsdSchemas(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("MyFavorite.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        String path = xsd.toFile().getAbsolutePath();
        try {
            FavoritesService.getInstance().addFavorite(new File(path));
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.refreshXsdFavoritesMenu();
                return null;
            });
            assertTrue(panel.xsdFavoriteNames().stream().anyMatch(n -> n.contains("MyFavorite")),
                    "the favorited XSD must appear in the quick-select, was: " + panel.xsdFavoriteNames());
        } finally {
            FavoritesService.getInstance().removeFavoriteByPath(path);
        }
    }
}
