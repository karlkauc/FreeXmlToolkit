package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the <strong>Unified Shell</strong> documentation screenshots into
 * {@code docs/img/} (file names {@code unified-shell-*.png}).
 *
 * <p>Loads the Unified Shell FXML ({@code tab_unified_shell.fxml}) directly — the same root
 * {@code FxtGui} boots into — then drives the shell's activities and editor view modes and
 * writes a JavaFX {@code snapshot} of the shell node for each. This uses node snapshots so it
 * works with software rendering and captures the shell content cleanly.
 *
 * <p>Run via the dedicated {@code docScreenshots} Gradle task on a real display:
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ShellDocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");

    private Parent root;
    private UnifiedShellView shell;
    private final org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateShellScreenshots() throws Exception {
        IMG_DIR.mkdirs();

        // The app boots into the shell.
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle();

        // --- Overview: an XML document open (Explorer + Text editor + inspector) ---
        File xml = new File(EXAMPLES, "xml/context-sensitive-demo.xml");
        if (xml.exists()) {
            onFx(() -> host.openFile(xml.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> !t.isBlank()).orElse(false));
            settle();
            shot("unified-shell-overview");
        }

        // --- XSD in the Graphic view (Schema activity) ---
        File xsd = new File(EXAMPLES, "xsd/context-sensitive-demo.xsd");
        if (xsd.exists()) {
            onFx(() -> host.openFile(xsd.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("schema")).orElse(false));
            onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
            settle();
            shot("unified-shell-type-library");
            onFx(() -> host.setActiveViewMode(ViewMode.GRAPHIC));
            settle(900);
            shot("unified-shell-schema-graphic");
            onFx(() -> host.setActiveViewMode(ViewMode.TREE));
            settle();
            shot("unified-shell-schema-tree");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
            shot("unified-shell-schema-text");

            // --- Flatten Schema options dialog over the open schema. The dialog is a
            // separate top-level window, so capture the real screen (like the
            // IntelliSense popup shot) instead of an FX node snapshot.
            var flattenDialog = new java.util.concurrent.atomic.AtomicReference<
                    org.fxt.freexmltoolkit.controls.shell.editor.FlattenOptionsDialog>();
            onFx(() -> {
                var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.FlattenOptionsDialog();
                dialog.initOwner(shell.getScene().getWindow());
                dialog.show();
                flattenDialog.set(dialog);
            });
            settle(700);
            try {
                shotScreen("unified-shell-flatten-options");
            } catch (Exception e) {
                System.out.println("[shell-screenshot] flatten dialog capture failed: " + e);
            }
            onFx(() -> flattenDialog.get().close());
            settle();

            // Schema statistics open as an in-shell text tab. The action moved into
            // the panel's ⋮ overflow menu, so drive the panel directly.
            onFx(() -> {
                var panel = shell.lookup(".fxt-schema-panel");
                if (panel instanceof org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel library) {
                    library.statisticsActive();
                }
            });
            settle(900);
            shot("unified-shell-schema-statistics");
        }

        // --- Activity panels (selecting the activity shows its side panel) ---
        onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
        settle();
        shot("unified-shell-validation");

        onFx(() -> shell.getSelectionModel().select(Activity.TRANSFORM));
        settle();
        shot("unified-shell-transform");

        // --- Transform: browsing XSLT/XML favorites with ◀/▶ (auto-running each step) ---
        // Establish a stylesheet browse list (a folder of data-quality checks) and an input
        // browse list (FundsXML samples), select the first of each so the ◀/▶ controls and the
        // "i / n" position labels appear, then auto-run produces a result in the output dock.
        File checkXslt = new File(EXAMPLES, "xslt/Check_FundsXML_File.xslt");
        File fundsXml = new File(EXAMPLES, "xml/FundsXML_422_Bond_Fund.xml");
        if (checkXslt.exists() && fundsXml.exists()
                && shell.lookup(".fxt-transform-panel")
                        instanceof org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel transformPanel) {
            var dqChecks = java.util.List.of(
                    checkXslt,
                    new File(EXAMPLES, "xslt/FundsXML_Structure_Check.xslt"),
                    new File(EXAMPLES, "xslt/Basic_Checks.xslt"));
            var inputs = java.util.List.of(
                    fundsXml,
                    new File(EXAMPLES, "xml/context-sensitive-demo.xml"));
            onFx(() -> {
                transformPanel.selectInput(inputs, inputs.getFirst());
                transformPanel.selectXslt(dqChecks, dqChecks.getFirst());
            });
            settle(2500); // let the auto-run transform complete and render its result
            shot("unified-shell-transform-favorites");
        }

        onFx(() -> shell.getSelectionModel().select(Activity.SIGNATURE));
        settle();
        shot("unified-shell-signature");

        // --- JSON document in the Tree view ---
        Path json = Path.of(System.getProperty("java.io.tmpdir"), "fxt-shell-doc-sample.json");
        Files.writeString(json, "{\n  \"fund\": {\n    \"id\": \"EAM\",\n    \"items\": [1, 2, 3],\n    \"active\": true\n  }\n}\n");
        json.toFile().deleteOnExit();
        onFx(() -> host.openFile(json));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));
        onFx(() -> host.setActiveViewMode(ViewMode.TREE));
        settle();
        shot("unified-shell-json-tree");

        // --- HTML document rendered in the read-only Preview view (the default for HTML) ---
        Path html = Path.of(System.getProperty("java.io.tmpdir"), "fxt-shell-doc-report.html");
        Files.writeString(html, """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <title>Fund Validation Report</title>
                <style>
                  body { font-family: sans-serif; margin: 2em; color: #212529; }
                  h1 { color: #1373d9; border-bottom: 2px solid #1373d9; padding-bottom: .3em; }
                  table { border-collapse: collapse; margin-top: 1em; }
                  th, td { border: 1px solid #dee2e6; padding: .45em .9em; text-align: left; }
                  th { background: #eef4fb; }
                  .ok { color: #28a745; font-weight: bold; }
                  .warn { color: #e8590c; font-weight: bold; }
                </style>
                </head>
                <body>
                <h1>Fund Validation Report</h1>
                <p>Generated by <em>Check_FundsXML_File.xslt</em> — 4 rules evaluated.</p>
                <table>
                  <tr><th>Rule</th><th>Scope</th><th>Result</th></tr>
                  <tr><td>NAV reconciliation</td><td>Fund EAM_FUND_1</td><td class="ok">PASS</td></tr>
                  <tr><td>Position totals</td><td>42 positions</td><td class="ok">PASS</td></tr>
                  <tr><td>Currency codes</td><td>ISO 4217</td><td class="ok">PASS</td></tr>
                  <tr><td>Missing ISINs</td><td>2 positions</td><td class="warn">WARNING</td></tr>
                </table>
                </body>
                </html>
                """);
        html.toFile().deleteOnExit();
        onFx(() -> {
            shell.getSelectionModel().select(Activity.EXPLORER);
            host.transformOutputPanel().hide(); // drop the transform scene's leftover result
            host.openFile(html); // HTML opens in the Preview view by default
        });
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Validation Report")).orElse(false));
        settle(1500); // give the WebView time to render the loaded content
        shot("unified-shell-html-preview");

        // --- Query Console docked at the bottom (XPath/XQuery against the open document) ---
        // Captured via a real screen grab (java.awt.Robot) so the IntelliSense completion
        // popup — a separate top-level window that an FX node snapshot cannot include — is
        // visible in the screenshot.
        if (xml.exists()) {
            onFx(() -> shell.getSelectionModel().select(Activity.EXPLORER));
            onFx(() -> host.openFile(xml.toPath()));
            settle();
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            onFx(shell::toggleQueryConsole);
            settle(600);
            try {
                // Run a query first so the RESULTS pane shows syntax-highlighted XML
                // (the sample document uses a default namespace, hence the XPath 3.0
                // wildcard-namespace form). Escape closes the completion popup that
                // opens while typing, so Ctrl+Enter reaches the run filter.
                Node xpathInput = robot.lookup(".fxt-query-input").match(Node::isVisible).query();
                robot.clickOn(xpathInput);
                settle(200);
                robot.write("//*:menuItem");
                robot.push(javafx.scene.input.KeyCode.ESCAPE);
                robot.push(javafx.scene.input.KeyCode.CONTROL, javafx.scene.input.KeyCode.ENTER);
                org.fxmisc.richtext.CodeArea results =
                        robot.lookup(".fxt-query-results").queryAs(org.fxmisc.richtext.CodeArea.class);
                WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                        () -> results.getText().contains("<menuItem"));
                settle(300);
                // Then type '/' (appending to the expression) to open the completion popup.
                robot.clickOn(xpathInput);
                settle(200);
                robot.write("/");
                // Wait until the popup has rendered as a real window.
                WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                        () -> robot.lookup(".intellisense-list").tryQuery().isPresent());
                settle(500);
                shotScreen("unified-shell-query-console");
                robot.push(javafx.scene.input.KeyCode.ESCAPE);
            } catch (Exception e) {
                System.out.println("[shell-screenshot] IntelliSense popup capture failed, "
                        + "falling back to a node snapshot: " + e);
                shot("unified-shell-query-console");
            }
            settle();
            onFx(shell::toggleQueryConsole); // hide again so later shots are unaffected
            settle();
        }

        // --- XML document in the Grid (XMLSpy-style) view ---
        if (xml.exists()) {
            onFx(() -> shell.getSelectionModel().select(Activity.EXPLORER));
            onFx(() -> host.openFile(xml.toPath())); // re-selects the already-open XML tab
            settle();
            onFx(() -> host.setActiveViewMode(ViewMode.GRAPHIC));
            settle();
            shot("unified-shell-xml-grid");
        }

        // --- Query document with the Target dropdown open + a run result in OUTPUT ---
        // Captured as a real screen grab: the dropdown's popup is a separate window
        // that an FX node snapshot cannot include.
        File fundsSample = new File(EXAMPLES, "xml/FundsXML4_Equity_Fund.xml");
        File xquery = new File(EXAMPLES, "xquery/12-positions-csv-export.xq");
        if (fundsSample.exists() && xquery.exists()) {
            onFx(() -> shell.getSelectionModel().select(Activity.EXPLORER));
            onFx(() -> host.openFile(fundsSample.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));
            onFx(() -> host.openFile(xquery.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("xquery version")).orElse(false));
            settle();
            onFx(shell::onRunQuery); // Automatic target = the FundsXML document
            WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                    () -> host.transformOutputPanel().getOutputText() != null
                            && host.transformOutputPanel().getOutputText().contains("FundISIN"));
            settle(500);
            try {
                Node targetButton = robot.lookup("#doc-query-target").match(Node::isVisible).query();
                robot.clickOn(targetButton);
                settle(600);
                shotScreen("unified-shell-query-target");
                robot.push(javafx.scene.input.KeyCode.ESCAPE);
            } catch (Exception e) {
                System.out.println("[shell-screenshot] Target dropdown capture failed, "
                        + "falling back to a node snapshot: " + e);
                shot("unified-shell-query-target");
            }
            settle();
        }

        // --- XProc pipeline with its CSV result in the OUTPUT panel ---
        File pipeline = new File(EXAMPLES, "xproc/03-positions-csv.xpl");
        if (fundsSample.exists() && pipeline.exists()) {
            onFx(() -> host.openFile(pipeline.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("declare-step")).orElse(false));
            settle();
            onFx(() -> host.transformOutputPanel().hide()); // drop the previous query result
            onFx(shell::onRunPipeline); // Automatic target = the FundsXML document
            WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS,
                    () -> host.transformOutputPanel().getOutputText() != null
                            && host.transformOutputPanel().getOutputText().contains("FundISIN"));
            settle(500);
            shot("unified-shell-xproc-pipeline");
        }

        // --- FundsXML activity panel + Welcome quick-access row (last: closes all tabs) ---
        captureFundsXmlScenes(host);
    }

    /**
     * Captures the FundsXML side panel and the Welcome page's FUNDSXML quick-access row.
     * Temporarily enables the feature flag and, when no real content cache exists, seeds a
     * minimal one from {@code release/examples}; both are restored/removed afterwards so the
     * generator leaves no trace in the developer's configuration.
     */
    private void captureFundsXmlScenes(EditorHost host) throws Exception {
        var props = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        String enabledKey = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED;
        String oldEnabled = props.get(enabledKey);
        var cache = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache.getInstance();
        Path cacheBase = Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "fundsxml");
        // Paths this method creates; removed afterwards so the developer's real cache
        // is left as found (the empty base dirs are (re)created by the app anyway).
        java.util.List<Path> seededPaths = new java.util.ArrayList<>();
        try {
            props.set(enabledKey, "true");
            // Seed a minimal content cache when nothing is installed (the base dirs may
            // exist but be empty — FundsXmlCache creates them eagerly).
            if (cache.listInstalledVersions().isEmpty()) {
                Path schemaDir = cacheBase.resolve("schema").resolve("4.2.11");
                Files.createDirectories(schemaDir);
                seededPaths.add(seedCopy(new File(EXAMPLES, "xsd/FundsXML4.xsd"),
                        schemaDir.resolve("FundsXML4.xsd")));
                seededPaths.add(schemaDir);
                seededPaths.add(seedCopy(new File(EXAMPLES, "xml/FundsXML_422_Bond_Fund.xml"),
                        cacheBase.resolve("examples").resolve("FundsXML_422_Bond_Fund.xml")));
                seededPaths.add(seedCopy(new File(EXAMPLES, "xquery/01-portfolio-nav-reconciliation.xq"),
                        cacheBase.resolve("queries").resolve("01-portfolio-nav-reconciliation.xq")));
                Path metadata = cacheBase.resolve("metadata.json");
                if (!Files.exists(metadata)) {
                    seededPaths.add(metadata);
                }
                var meta = cache.loadMetadata();
                meta.setActiveSchemaVersion("4.2.11");
                cache.saveMetadata(meta);
            }

            // Hide the transform OUTPUT dock left over from the transform scene, make the
            // conditional activity-bar button appear, then show the panel.
            onFx(() -> {
                host.transformOutputPanel().hide();
                if (shell.lookup(".fxt-activity-bar")
                        instanceof org.fxt.freexmltoolkit.controls.shell.ActivityBar bar) {
                    bar.refresh();
                }
                shell.getSelectionModel().select(Activity.FUNDSXML);
            });
            settle(600);
            shot("unified-shell-fundsxml");

            // Welcome page: close every tab so the dashboard (with the FUNDSXML row) shows.
            onFx(() -> {
                if (shell.lookup(".fxt-editor-tabpane")
                        instanceof javafx.scene.control.TabPane tabs) {
                    tabs.getTabs().clear();
                }
            });
            settle(600);
            shot("unified-shell-welcome-fundsxml");
        } finally {
            props.set(enabledKey, oldEnabled == null ? "false" : oldEnabled);
            for (Path p : seededPaths.reversed()) {
                try {
                    if (p != null) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Copies {@code source} to {@code target} (creating parents) and returns the target. */
    private static Path seedCopy(File source, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Files.copy(source.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
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

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[shell-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }

    /**
     * Captures the whole X screen with {@link java.awt.Robot} (not an FX node snapshot), so any
     * popup windows that are open — e.g. the IntelliSense completion list — appear in the image.
     * Run the {@code docScreenshots} task under an xvfb screen sized to the window (1680x1000)
     * so the captured screen is exactly the app.
     */
    private void shotScreen(String name) throws Exception {
        settle(300);
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        java.awt.Robot awt = new java.awt.Robot();
        java.awt.image.BufferedImage img = awt.createScreenCapture(
                new java.awt.Rectangle(0, 0, screen.width, screen.height));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("[shell-screenshot] (screen) wrote " + out.getAbsolutePath()
                + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
