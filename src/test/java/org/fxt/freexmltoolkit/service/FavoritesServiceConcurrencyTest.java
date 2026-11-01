package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The FX thread reads favourites (inspector star, menus) while another thread adds or removes
 * them; a read must never fail with a ConcurrentModificationException.
 */
class FavoritesServiceConcurrencyTest {

    private String originalUserHome;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        FavoritesService.resetInstanceForTests();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", originalUserHome);
        FavoritesService.resetInstanceForTests();
    }

    @Test
    void readsWhileAnotherThreadAddsAndRemoves() throws Exception {
        FavoritesService service = FavoritesService.getInstance();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; running.get() && i < 2000; i++) {
                    String path = "/tmp/file" + (i % 20) + ".xml";
                    service.addFavorite(new FileFavorite("f" + i, path, i % 2 == 0 ? "A" : "B"));
                    service.removeFavoriteByPath(path);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                running.set(false);
            }
        }, "favorites-writer");
        writer.start();

        while (running.get() && failure.get() == null) {
            try {
                service.isFavorite("/tmp/file3.xml");
                service.getAllFavorites();
                service.getFavoritesByFolder("A");
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }
        writer.join(30_000);
        assertNull(failure.get(), () -> "concurrent access failed: " + failure.get());
    }
}
