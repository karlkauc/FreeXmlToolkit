package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.plugin.PluginMenuItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A plugin's activity, menu items, document listener and editor bar in the shell. */
@ExtendWith(ApplicationExtension.class)
class PluginShellTest {

    static final AtomicBoolean VISIBLE = new AtomicBoolean(true);
    static final List<String> OPENED = new CopyOnWriteArrayList<>();
    static final List<String> RAN = new CopyOnWriteArrayList<>();

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        PluginManager.getInstance().loadAll();
        if (Activity.fromId("sample-plugin").isEmpty()) {
            PluginManager.getInstance().registerForTesting(new FxtPlugin() {
                @Override public String id() { return "test.sample"; }
                @Override public String name() { return "Sample"; }
                @Override public void register(PluginContext context) {
                    context.addActivity(new PluginActivity("sample-plugin", "Sample", "bi-puzzle", null,
                            ui -> new Label("sample panel"), VISIBLE::get));
                    context.addActivity(new PluginActivity("broken-plugin", "Broken", "bi-bug", ui -> {
                        throw new IllegalStateException("no panel");
                    }));
                    context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.TOOLS, "sample-tool", "Sample Tool",
                            "bi-puzzle", ui -> RAN.add("tool"), VISIBLE::get));
                    context.addDocumentListener((ui, path, content) -> {
                        OPENED.add(path == null ? "?" : path.getFileName().toString());
                        if (content.contains("<sample")) {
                            Label bar = new Label("sample bar");
                            bar.setId("sample-bar");
                            ui.showEditorBar(bar);
                        }
                    });
                }
            });
        }
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1280, 800));
        stage.show();
    }

    @AfterAll
    static void reset() {
        PluginManager.getInstance().resetForTesting();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(5000, action);
    }

    private MenuItem menuItem(String id) {
        return shell.getMenuBar().allItems().stream().filter(i -> id.equals(i.getId())).findFirst().orElse(null);
    }

    @Test
    void activityMenuItemsAndEditorBar(@TempDir Path dir) throws Exception {
        assertNotNull(fx(() -> shell.lookup("#activity-sample-plugin")), "the rail shows the plugin's activity");
        Activity sample = Activity.fromId("sample-plugin").orElseThrow();
        fx(() -> {
            shell.showActivity(sample);
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> shell.lookup(".label") != null
                && shell.lookupAll(".label").stream().anyMatch(n -> "sample panel".equals(((Label) n).getText()))));

        // A failing panel factory shows an error instead of breaking the shell
        fx(() -> {
            shell.showActivity(Activity.fromId("broken-plugin").orElseThrow());
            return null;
        });
        assertNotNull(fx(() -> shell.lookup("#plugin-panel-error")));

        // Tools item runs; View → Activities lists the activity
        MenuItem tool = fx(() -> menuItem("menu-tools-sample-tool"));
        assertNotNull(tool);
        fx(() -> {
            tool.fire();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> RAN.contains("tool"));
        assertNotNull(fx(() -> menuItem("menu-view-activity-sample-plugin")));

        // Hidden: rail button, Tools and View items go away
        VISIBLE.set(false);
        fx(() -> {
            shell.pluginUi().refreshContributions();
            return null;
        });
        assertNull(fx(() -> shell.lookup("#activity-sample-plugin")));
        assertFalse(fx(() -> menuItem("menu-tools-sample-tool").isVisible()));
        assertFalse(fx(() -> menuItem("menu-view-activity-sample-plugin").isVisible()));
        VISIBLE.set(true);
        fx(() -> {
            shell.pluginUi().refreshContributions();
            return null;
        });

        // Document listener + editor bar
        Path file = dir.resolve("s.xml");
        Files.writeString(file, "<sample/>");
        fx(() -> {
            shell.openFile(file);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> OPENED.contains("s.xml"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> shell.lookup("#sample-bar") != null));
        fx(() -> {
            shell.pluginUi().hideEditorBar();
            return null;
        });
        assertNull(fx(() -> shell.lookup("#sample-bar")));
        assertEquals("sample-plugin", sample.id());
        assertTrue(sample.plugin().isPresent());
    }
}
