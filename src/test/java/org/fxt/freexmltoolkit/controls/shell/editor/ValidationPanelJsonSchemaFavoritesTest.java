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

/** The Validation panel offers a quick-select menu of favorited JSON (Schema) files. */
@ExtendWith(ApplicationExtension.class)
class ValidationPanelJsonSchemaFavoritesTest {

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
    void listsFavoriteJsonSchemas(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("MyFavoriteSchema.json");
        Files.writeString(schema, "{\"type\": \"object\"}");
        String path = schema.toFile().getAbsolutePath();
        try {
            FavoritesService.getInstance().addFavorite(new File(path));
            var names = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.jsonSchemaFavoriteNames());
            assertTrue(names.stream().anyMatch(n -> n.contains("MyFavoriteSchema")),
                    "the favorited JSON Schema must appear in the quick-select, was: " + names);
        } finally {
            FavoritesService.getInstance().removeFavoriteByPath(path);
        }
    }
}
