package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.ValueSource;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.ValueViewerWindow;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane;
import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.BreakpointStore;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.HitCondition;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.fxt.freexmltoolkit.debugger.WatchStore;
import org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the <strong>XSLT debugger</strong> documentation screenshots into {@code docs/img/}
 * (file names {@code xslt-debugger-*.png}): the DEBUG dock paused at a conditional breakpoint,
 * the value window with an XPath result and the Breakpoint Properties dialog.
 *
 * <p>Debugs {@code release/examples/xslt/FundsXML_Positions_to_CSV.xslt} on the bond fund
 * example with a logpoint, a disabled breakpoint, a conditional breakpoint
 * ({@code position() = 3}) and three watches. The breakpoints and watches of that stylesheet are restored
 * afterwards. Part of the {@code docScreenshots} task (run under xvfb):
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsltDebuggerDocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");
    private static final int BREAK_LINE = 56;

    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        props.set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        props.set("ui.theme", "light"); // the value window and dialogs follow the saved theme
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateDebuggerScreenshots() throws Exception {
        File xslt = new File(EXAMPLES, "xslt/FundsXML_Positions_to_CSV.xslt").getAbsoluteFile();
        File xml = new File(EXAMPLES, "xml/FundsXML_422_Bond_Fund.xml").getAbsoluteFile();
        if (!xslt.exists() || !xml.exists()) {
            System.out.println("[debugger-screenshot] examples missing, skipped");
            return;
        }
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle();

        String path = xslt.getPath();
        Set<Breakpoint> oldBreakpoints = BreakpointStore.load(path);
        List<String> oldWatches = WatchStore.load(path);
        try {
            BreakpointStore.save(path, List.of(
                    new Breakpoint(path, 38, true, "", HitCondition.ALWAYS, "fund {$fundIsin}", false),
                    new Breakpoint(path, 39, false),
                    new Breakpoint(path, BREAK_LINE, true, "position() = 3")));
            WatchStore.save(path, List.of("$fundName", "TotalValue/Amount/@ccy", "count(../Position)"));
            String input = Files.readString(xml.toPath());

            onFx(() -> {
                host.setDebugInputFile(xml.toPath());
                host.startXsltDebug(xslt, input, Map.of(), XsltTransformationEngine.OutputFormat.TEXT);
            });
            WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> {
                XsltDebugPane pane = host.activeDebugPane();
                return pane != null && pane.getController().getSession().getState() == DebugSession.State.PAUSED;
            });
            settle(1500); // variables, watches and the editor tint
            XsltDebugPane pane = host.activeDebugPane();
            onFx(pane::onShowInEditor); // scroll the paused line into view
            settle(800);
            shot(shell, "xslt-debugger-paused");

            // The value window on the paused context node, with an XPath result below
            PausedSnapshot snap = pane.getController().getSession().getPausedSnapshot();
            onFx(() -> pane.openViewer(new ValueSource(".", WatchEvaluator.describeType(snap.contextXdm()),
                    "LOCAL", snap.contextXdm(), "", snap)));
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> viewer() != null
                    && !viewer().getValueText().isEmpty());
            onFx(() -> {
                for (Node n : viewer().getScene().getRoot().lookupAll(".text-field")) {
                    if (n instanceof javafx.scene.control.TextField field) {
                        field.setText("TotalValue/Amount");
                    }
                }
                viewer().onRunXPath();
            });
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !viewer().getResultText().isEmpty());
            onFx(() -> {
                viewer().setWidth(900);
                viewer().setHeight(640);
            });
            settle(800);
            shot(viewer().getScene().getRoot(), "xslt-debugger-value-viewer");
            onFx(() -> viewer().close());

            // Breakpoint Properties of the conditional breakpoint
            BreakpointPropertiesDialog[] dialog = new BreakpointPropertiesDialog[1];
            onFx(() -> {
                dialog[0] = new BreakpointPropertiesDialog(xslt.getName() + ":" + BREAK_LINE,
                        new BreakpointPropertiesDialog.Settings("position() = 3", HitCondition.ALWAYS, "", true),
                        pane.getController().getSession().hitCount(path, BREAK_LINE));
                dialog[0].initOwner(root.getScene().getWindow());
                dialog[0].show();
            });
            settle(800);
            shot(dialog[0].getDialogPane(), "xslt-debugger-breakpoint-properties");
            onFx(() -> dialog[0].close());
        } finally {
            onFx(() -> {
                if (host.activeDebugPane() != null) {
                    host.endXsltDebug();
                }
            });
            BreakpointStore.save(path, oldBreakpoints);
            WatchStore.save(path, oldWatches);
        }
    }

    private static ValueViewerWindow viewer() {
        for (Window w : Window.getWindows()) {
            if (w instanceof ValueViewerWindow v && v.isShowing()) {
                return v;
            }
        }
        return null;
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle() {
        settle(400);
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(Node target, String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> target.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[debugger-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }
}
