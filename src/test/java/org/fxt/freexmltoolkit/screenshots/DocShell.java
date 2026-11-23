package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Unified Shell as the documentation screenshot generators drive it: boots the shell the way
 * {@code FxtGui} does (1680 x 1000, light theme, bundled fonts), and offers the steps every scene
 * needs - run on the FX thread, wait, open a file, capture the shell or the window with its popups.
 * A generator holds one instance and calls {@link #start} from its {@code @Start} method.
 */
final class DocShell {

    static final File IMG_DIR = new File("docs/img");

    /** One capture scene; a failing scene is logged and the next ones still run. */
    @FunctionalInterface
    interface SceneBody {
        void run() throws Exception;
    }

    private final String tag;
    private Parent root;
    private Stage stage;
    private UnifiedShellView shell;

    /** @param tag the prefix of this generator's log lines, e.g. {@code problems-screenshot} */
    DocShell(String tag) {
        this.tag = tag;
    }

    /** Loads the shell into {@code stage} (call from the generator's {@code @Start} method). */
    void start(Stage stage) throws Exception {
        start(stage, 1680, 1000);
    }

    /** Loads the shell with a scene of the given size (the videos record a whole 1920 x 1080 screen). */
    void start(Stage stage, int width, int height) throws Exception {
        this.stage = stage;
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        // The one-time FundsXML hint would sit above the editor in every later image.
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        // The bundled fonts, as the application loads them (else the shots fall back to a system font).
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, width, height));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    /** Waits for the shell, switches to the light theme and returns the shell (call first in the test). */
    UnifiedShellView boot() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), false));
        // both side panes show, whatever an earlier run left in the stored layout
        onFx(() -> {
            shell.setLeftPanelVisible(true);
            shell.setInspectorVisible(true);
        });
        settle(600);
        return shell;
    }

    UnifiedShellView shell() {
        return shell;
    }

    EditorHost host() {
        return shell.getEditorHost();
    }

    Stage stage() {
        return stage;
    }

    /** Runs one capture scene; logs and continues on failure so the other shots still happen. */
    void scene(String label, SceneBody body) {
        try {
            body.run();
            System.out.println("[" + tag + "] scene OK: " + label);
        } catch (Throwable t) {
            System.out.println("[" + tag + "] scene FAILED (skipped): " + label + " -> " + t);
            t.printStackTrace(System.out);
        }
    }

    void onFx(Runnable action) {
        ScreenshotFx.run(tag, action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    <T> T fx(Callable<T> action) {
        return ScreenshotFx.call(tag, action);
    }

    void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Polls {@code condition} on the FX thread until it holds; fails after {@code seconds}. */
    void waitUntilFx(long seconds, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            try {
                if (Boolean.TRUE.equals(WaitForAsyncUtils.waitForAsyncFx(5000, condition))) {
                    return;
                }
            } catch (RuntimeException e) {
                if (!(e.getCause() instanceof java.util.concurrent.TimeoutException)) {
                    throw e;
                }
            }
            WaitForAsyncUtils.sleep(100, TimeUnit.MILLISECONDS);
        }
        ScreenshotFx.printThreadStacks(tag);
        throw new java.util.concurrent.TimeoutException("condition not met within " + seconds + " s");
    }

    /** Opens {@code file} and waits until its text (containing {@code marker}) is the active one. */
    void open(Path file, String marker) throws Exception {
        onFx(() -> host().openFile(file));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host().getActiveText().map(t -> t.contains(marker)).orElse(false));
        settle(500);
    }

    /** Shows an activity's side panel (or page). */
    void activity(Activity activity) {
        onFx(() -> shell.getSelectionModel().select(activity));
        settle(500);
    }

    /** Closes every editor and tool tab (the next scene starts from the Welcome page). */
    void closeAllTabs() {
        for (int i = 0; i < 40; i++) {
            boolean more = fx(() -> {
                if (!(shell.lookup(".fxt-editor-tabpane") instanceof javafx.scene.control.TabPane tabs)
                        || tabs.getTabs().isEmpty()) {
                    return false;
                }
                int before = tabs.getTabs().size();
                if (!host().closeActiveTab() || tabs.getTabs().size() == before) {
                    tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
                }
                return true;
            });
            WaitForAsyncUtils.waitForFxEvents();
            if (!more) {
                break;
            }
        }
        settle(400);
    }

    /** Hides the transform/query OUTPUT dock if it is showing. */
    void hideOutputDock() {
        onFx(() -> {
            for (Node node : root.lookupAll("*")) {
                if (node instanceof org.fxt.freexmltoolkit.controls.shell.editor.TransformOutputPanel output
                        && output.isVisible()) {
                    output.hide();
                }
            }
        });
    }

    /** @return the first node matching {@code selector} that is showing (panels of other activities stay in memory) */
    @SuppressWarnings("unchecked")
    <T extends Node> T find(String selector) {
        return fx(() -> {
            for (Node node : root.lookupAll(selector)) {
                if (isShown(node)) {
                    return (T) node;
                }
            }
            return (T) root.lookup(selector);
        });
    }

    /** @return the showing node of the given class (a side panel, a view), or {@code null} */
    <T extends Node> T findType(Class<T> type) {
        return fx(() -> {
            for (Node node : root.lookupAll("*")) {
                if (type.isInstance(node) && isShown(node)) {
                    return type.cast(node);
                }
            }
            for (Node node : root.lookupAll("*")) {
                if (type.isInstance(node)) {
                    return type.cast(node);
                }
            }
            return null;
        });
    }

    private static boolean isShown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    /**
     * Calls a method the application keeps package-private for its own tests (for example a
     * recipient added without the file chooser). The generators live in another package.
     */
    Object call(Object target, String method, Object... args) {
        return fx(() -> {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                        m.setAccessible(true);
                        return m.invoke(target, args);
                    }
                }
            }
            throw new NoSuchMethodException(target.getClass().getName() + "." + method);
        });
    }

    /** Captures the shell node. */
    void shot(String name) throws Exception {
        shotNode(shell, name);
    }

    /** Captures one node (a dialog pane, a view). */
    void shotNode(Node target, String name) throws Exception {
        var img = fx(() -> target.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[" + tag + "] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }

    /**
     * Captures the application window from the X screen (not an FX node snapshot), so popups and
     * dialogs over it appear in the image. The image has the window's size, 1680 x 1000.
     */
    void shotWindow(String name) throws Exception {
        settle(300);
        java.awt.Rectangle area = fx(() -> {
            Scene scene = stage.getScene();
            return new java.awt.Rectangle((int) Math.round(stage.getX() + scene.getX()),
                    (int) Math.round(stage.getY() + scene.getY()),
                    (int) Math.round(scene.getWidth()), (int) Math.round(scene.getHeight()));
        });
        java.awt.image.BufferedImage img = new java.awt.Robot().createScreenCapture(area);
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("[" + tag + "] (screen) wrote " + out.getAbsolutePath()
                + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
