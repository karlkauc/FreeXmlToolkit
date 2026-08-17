package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ToggleButton;
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
 * Re-captures the <strong>legacy documentation screenshots</strong> (the pre-unified-shell
 * images referenced from {@code docs/*.md}: {@code main-window.png}, {@code xml-editor-*.png},
 * {@code xsd-*.png}, {@code signature-*.png}, …) showing the current Unified Shell UI. The
 * file names stay identical, so the docs pages need no markdown changes.
 *
 * <p>Every scene is wrapped in {@link #scene(String, SceneBody)} so a single flaky capture
 * logs and skips instead of aborting the remaining shots.
 *
 * <p>Run via the dedicated {@code docScreenshots} Gradle task on a real display:
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class PagesDocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");

    private Parent root;
    private UnifiedShellView shell;
    private EditorHost host;
    private final org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

    @FunctionalInterface
    interface SceneBody {
        void run() throws Exception;
    }

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
    void generatePageScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        host = shell.getEditorHost();
        settle();

        File demoXml = new File(EXAMPLES, "xml/context-sensitive-demo.xml");
        File demoXsd = new File(EXAMPLES, "xsd/context-sensitive-demo.xsd");
        File fundsXml = new File(EXAMPLES, "xml/FundsXML_422_Bond_Fund.xml");
        File checkXslt = new File(EXAMPLES, "xslt/Check_FundsXML_File.xslt");
        File factsheetXsl = new File(EXAMPLES, "xsl/FundsXML_Factsheet.xsl");
        File quickfixXml = new File(EXAMPLES, "schematron/funds-quickfix-demo.xml");
        File quickfixSch = new File(EXAMPLES, "schematron/funds-quickfix-rules.sch");
        File signedXml = new File(EXAMPLES, "signature/FundsXML4_Equity_Fund_signed.xml");
        File demoKeystore = new File(EXAMPLES, "signature/fundsxml-demo_KeyStore.jks");

        // ---------------------------------------------------------------- XML editor basics
        scene("main-window + xml-editor-text", () -> {
            openAndWait(demoXml, "IntelliSense");
            onFx(() -> shell.getSelectionModel().select(Activity.EXPLORER));
            settle();
            shot("main-window");
            shot("xml-editor-text");
        });

        scene("xml-editor-text-mode/tree/graphic", () -> {
            openAndWait(fundsXml, "FundsXML4");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
            shot("xml-editor-text-mode");
            onFx(() -> host.setActiveViewMode(ViewMode.TREE));
            settle(1500);
            shot("xml-editor-tree-view");
            copyShot("xml-editor-tree-view", "xml-editor-tree");
            onFx(() -> host.setActiveViewMode(ViewMode.GRAPHIC));
            settle(1500);
            shot("xml-editor-graphic");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
        });

        scene("xml-editor-file-operations (toolbar)", () -> {
            // Header + editor toolbar rows (the bare toolbar HBox alone is a 34px sliver).
            Node topRegion = shell.lookup(".fxt-top-region");
            if (topRegion != null) {
                shotNode(topRegion, "xml-editor-file-operations");
            }
        });

        scene("xml-editor-folding", () -> {
            // Text view of a large, deeply nested document: fold gutter markers visible.
            openAndWait(fundsXml, "FundsXML4");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
            shot("xml-editor-folding");
        });

        // ---------------------------------------------------------------- IntelliSense popup
        scene("intellisense popup", () -> {
            openAndWait(demoXml, "IntelliSense");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            // The demo XML carries no schema reference — bind its XSD manually so the
            // completion popup has schema-aware suggestions (async provider load).
            onFx(() -> host.setSchemaForActiveDocument(demoXsd));
            settle(2000);
            // Pick the demo document's CodeArea by content: editors of unselected tabs
            // still report isVisible(), so a bare ".code-area" lookup may hit another tab.
            org.fxmisc.richtext.CodeArea area = robot.lookup(".code-area")
                    .match((Node n) -> n instanceof org.fxmisc.richtext.CodeArea ca
                            && ca.getText().contains("</navigation>"))
                    .queryAs(org.fxmisc.richtext.CodeArea.class);
            Node parent = area;
            while (parent != null
                    && !(parent instanceof org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2)) {
                parent = parent.getParent();
            }
            var editor = (org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2) parent;
            var offset = new java.util.concurrent.atomic.AtomicInteger(-1);
            onFx(() -> {
                // Simulate the user having typed '<' right before </navigation>, then ask
                // the engine for completions directly (robot key events are unreliable for
                // '<' on a headless X layout).
                int pos = area.getText().indexOf("</navigation>");
                offset.set(pos);
                area.moveTo(pos);
                area.insertText(pos, "<");
                area.requestFocus();
            });
            settle(300);
            onFx(() -> editor.getIntelliSenseEngine().showCompletions());
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> robot.lookup(".intellisense-list").tryQuery().isPresent());
            settle(500);
            shotScreen("intellisense-overview");
            copyShot("intellisense-overview", "xml-editor-intellisense");
            copyShot("intellisense-overview", "xml-editor-intellisense-popup");
            robot.push(javafx.scene.input.KeyCode.ESCAPE);
            onFx(() -> area.deleteText(offset.get(), offset.get() + 1)); // undo the '<'
            settle();
        });

        // ---------------------------------------------------------------- Pretty print
        scene("pretty print before/after", () -> {
            Path ugly = Path.of(System.getProperty("java.io.tmpdir"), "fxt-doc-pretty-print.xml");
            Files.writeString(ugly, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><catalog>"
                    + "<book id=\"bk101\"><author>Gambardella, Matthew</author>"
                    + "<title>XML Developer's Guide</title><genre>Computer</genre>"
                    + "<price>44.95</price></book><book id=\"bk102\"><author>Ralls, Kim</author>"
                    + "<title>Midnight Rain</title><genre>Fantasy</genre><price>5.95</price>"
                    + "</book></catalog>");
            ugly.toFile().deleteOnExit();
            openAndWait(ugly.toFile(), "catalog");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
            shot("xml-editor-pretty-print-before");
            onFx(() -> host.formatActive());
            settle(800);
            shot("xml-editor-pretty-print-after");
        });

        // ---------------------------------------------------------------- Validation scenes
        scene("schema-xsd-support (valid doc)", () -> {
            openAndWait(demoXml, "IntelliSense");
            onFx(() -> {
                host.setSchemaForActiveDocument(demoXsd);
                shell.getSelectionModel().select(Activity.VALIDATION);
            });
            settle();
            onFx(() -> validationPanel().revalidate());
            settle(2500);
            shot("schema-xsd-support");
        });

        scene("xml-editor-validation (errors)", () -> {
            Path invalid = Path.of(System.getProperty("java.io.tmpdir"), "fxt-doc-invalid.xml");
            Files.writeString(invalid, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <document xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:noNamespaceSchemaLocation="%s">
                        <header>
                            <title>Broken document</title>
                            <subtitle>Not allowed by the schema</subtitle>
                        </header>
                        <unknownElement>also invalid</unknownElement>
                    </document>
                    """.formatted(demoXsd.getAbsolutePath()));
            invalid.toFile().deleteOnExit();
            openAndWait(invalid.toFile(), "Broken document");
            onFx(() -> {
                // Bind the schema explicitly — the auto-resolver may not pick up the
                // absolute-path xsi:noNamespaceSchemaLocation hint in time.
                host.setSchemaForActiveDocument(demoXsd);
                shell.getSelectionModel().select(Activity.VALIDATION);
            });
            settle(800);
            onFx(() -> validationPanel().revalidate());
            waitUntilFx(20, () -> validationPanel().getProblemCount() > 0);
            settle(600);
            shot("xml-editor-validation");
        });

        scene("xml-editor-schematron", () -> {
            openAndWait(quickfixXml, "FundsXML");
            onFx(() -> {
                host.setActiveSchematron(quickfixSch);
                shell.getSelectionModel().select(Activity.VALIDATION);
            });
            settle();
            onFx(() -> validationPanel().revalidate());
            waitUntilFx(30, () -> validationPanel().getProblemCount() > 0);
            settle(600);
            shot("xml-editor-schematron");
        });

        scene("xsd-validation-batch", () -> {
            List<File> batch = List.of(demoXml, fundsXml,
                    new File(EXAMPLES, "xml/FundsXML4_Equity_Fund.xml"));
            onFx(() -> {
                shell.getSelectionModel().select(Activity.VALIDATION);
                validationPanel().setBatchMode(true);
            });
            settle();
            onFx(() -> validationPanel().runBatch(batch));
            waitUntilFx(90, () -> validationPanel().batchResultCount() >= batch.size());
            settle(600);
            shot("xsd-validation-batch");
            onFx(() -> validationPanel().setBatchMode(false));
            settle();
        });

        // ---------------------------------------------------------------- XPath / Query Console
        scene("xml-editor-xpath", () -> {
            openAndWait(demoXml, "IntelliSense");
            onFx(() -> shell.getSelectionModel().select(Activity.EXPLORER));
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            onFx(shell::toggleQueryConsole);
            settle(600);
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
            robot.push(javafx.scene.input.KeyCode.ESCAPE);
            settle(400);
            shot("xml-editor-xpath");
            onFx(shell::toggleQueryConsole);
            settle();
        });

        // ---------------------------------------------------------------- Schema tools
        scene("xsd-type-editor", () -> {
            openAndWait(demoXsd, "schema");
            onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
            settle();
            onFx(() -> host.openTypeEditorTab("SectionType"));
            settle(1200);
            shot("xsd-type-editor");
        });

        scene("xsd-documentation", () -> {
            onFx(() -> host.openFile(demoXsd.toPath())); // re-select the schema tab
            settle();
            onFx(shell::onGenerateDocs);
            settle(1000);
            shot("xsd-documentation");
        });

        scene("xsd-sample-generator (advanced dialog)", () -> {
            var xpaths = org.fxt.freexmltoolkit.controls.shell.editor.ProfiledSampleRunner
                    .extractXPaths(demoXsd);
            var dialogRef = new java.util.concurrent.atomic.AtomicReference<
                    org.fxt.freexmltoolkit.controls.shell.editor.ProfiledSampleDialog>();
            onFx(() -> host.openFile(demoXsd.toPath()));
            settle();
            onFx(() -> {
                var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.ProfiledSampleDialog(xpaths);
                dialog.initOwner(shell.getScene().getWindow());
                dialog.show();
                dialogRef.set(dialog);
            });
            settle(800);
            shotScreen("xsd-sample-generator");
            onFx(() -> dialogRef.get().close());
            settle();
        });

        scene("schema-schematron-support (.sch open)", () -> {
            openAndWait(quickfixSch, "schema");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
            shot("schema-schematron-support");
        });

        // ---------------------------------------------------------------- Transform / XSLT
        scene("xslt-developer-overview", () -> {
            openAndWait(checkXslt, "stylesheet");
            onFx(() -> shell.getSelectionModel().select(Activity.TRANSFORM));
            settle();
            if (shell.lookup(".fxt-transform-panel")
                    instanceof org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel panel) {
                onFx(() -> {
                    panel.selectInput(List.of(fundsXml), fundsXml);
                    panel.selectXslt(List.of(checkXslt), checkXslt);
                });
                settle(2500); // auto-run
            }
            shot("xslt-developer-overview");
        });

        scene("xslt-factsheet (HTML result)", () -> {
            if (shell.lookup(".fxt-transform-panel")
                    instanceof org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel panel) {
                onFx(() -> {
                    panel.selectInput(List.of(fundsXml), fundsXml);
                    panel.selectXslt(List.of(factsheetXsl), factsheetXsl);
                });
                settle(3500); // auto-run + HTML render
                shot("xslt-factsheet");
            }
        });

        // ---------------------------------------------------------------- PDF / FOP
        scene("fop-pdf", () -> {
            onFx(() -> {
                host.transformOutputPanel().hide();
                shell.getSelectionModel().select(Activity.PDF_FOP);
            });
            settle();
            if (shell.lookup(".fxt-fop-panel")
                    instanceof org.fxt.freexmltoolkit.controls.shell.editor.FopPanel panel) {
                onFx(() -> {
                    panel.setXmlOverride(fundsXml);
                    panel.setXslFile(factsheetXsl);
                    panel.setMetadata("Fund Factsheet", "FreeXmlToolkit", "FundsXML demo");
                });
                settle();
            }
            shot("fop-pdf");
        });

        // ---------------------------------------------------------------- Signature
        scene("signature scenes", () -> {
            openAndWait(signedXml, "FundsXML4");
            onFx(() -> shell.getSelectionModel().select(Activity.SIGNATURE));
            settle();
            var panel = (org.fxt.freexmltoolkit.controls.shell.editor.SignaturePanel)
                    shell.lookup(".fxt-signature-panel");

            fireNav("#sig-nav-create");
            shot("signature-create-cert");

            fireNav("#sig-nav-expert");
            shot("signature-expert");

            fireNav("#sig-nav-validate");
            onFx(panel::validateActive);
            WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                    () -> panel.getStatusText().contains("Signature valid"));
            settle(400);
            shot("signature-validation");

            // Sign: demo keystore + credentials, then the Sign card in the editor area.
            onFx(() -> {
                panel.setKeystore(demoKeystore);
                panel.setCredentials("fundsxml-demo", "changeit", "changeit");
            });
            fireNav("#sig-nav-sign");
            settle(800);
            shot("signature-sign-process");
        });

        // ---------------------------------------------------------------- Favorites
        scene("favorites-overview", () -> {
            // Clean up leftovers from the signature/xpath scenes: show an XML document
            // instead of the Sign card, and make sure the Query Console is hidden.
            onFx(() -> {
                if (shell.isQueryConsoleShown()) {
                    shell.toggleQueryConsole();
                }
            });
            openAndWait(demoXml, "IntelliSense");
            settle(2500); // let the live re-validation settle so no transient problem bar shows
            var favorites = org.fxt.freexmltoolkit.service.FavoritesService.getInstance();
            List<File> seeded = List.of(demoXml, fundsXml, demoXsd, checkXslt, quickfixSch);
            List<File> added = new java.util.ArrayList<>();
            try {
                for (File f : seeded) {
                    // Only seed entries that are not already favorites (leave real ones alone).
                    if (favorites.getAllFavorites().stream()
                            .noneMatch(fav -> f.getAbsolutePath().equals(fav.getFilePath()))) {
                        favorites.addFavorite(f);
                        added.add(f);
                    }
                }
                onFx(() -> shell.getSelectionModel().select(Activity.FAVORITES));
                settle(600);
                shot("favorites-overview");
            } finally {
                for (File f : added) {
                    favorites.removeFavoriteByPath(f.getAbsolutePath());
                }
            }
        });

        // ---------------------------------------------------------------- Templates / New File
        scene("templates-overview (New File dialog)", () -> {
            var dialogRef = new java.util.concurrent.atomic.AtomicReference<
                    org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog>();
            onFx(() -> {
                var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog();
                dialog.initOwner(shell.getScene().getWindow());
                dialog.show();
                dialogRef.set(dialog);
            });
            settle(800);
            shotScreen("templates-overview");
            onFx(() -> dialogRef.get().close());
            settle();
        });
    }

    // ------------------------------------------------------------------- helpers

    /** Runs one capture scene; logs and continues on failure so the other shots still happen. */
    private void scene(String label, SceneBody body) {
        try {
            body.run();
            System.out.println("[pages-screenshot] scene OK: " + label);
        } catch (Throwable t) {
            System.out.println("[pages-screenshot] scene FAILED (skipped): " + label + " -> " + t);
        }
    }

    /** Opens {@code file} and waits until its text (containing {@code marker}) is active. */
    private void openAndWait(File file, String marker) throws Exception {
        onFx(() -> host.openFile(file.toPath()));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
        settle();
    }

    /** Polls {@code condition} on the FX thread until it holds (panel state is FX-owned). */
    private void waitUntilFx(long seconds, java.util.concurrent.Callable<Boolean> condition)
            throws Exception {
        WaitForAsyncUtils.waitFor(seconds, TimeUnit.SECONDS,
                () -> Boolean.TRUE.equals(WaitForAsyncUtils.waitForAsyncFx(5000, condition)));
    }

    private org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel() {
        return (org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel)
                shell.lookup(".fxt-validation-panel");
    }

    /** Fires a Signature panel nav toggle by its {@code fx:id} selector. */
    private void fireNav(String selector) {
        onFx(() -> {
            if (shell.lookup(selector) instanceof ToggleButton toggle) {
                toggle.fire();
            }
        });
        settle(500);
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

    private void shot(String name) throws Exception {
        shotNode(shell != null ? shell : root, name);
    }

    private void shotNode(Node target, String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000,
                () -> target.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[pages-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }

    /** Captures the whole X screen so separate popup windows (dialogs, completion lists) show. */
    private void shotScreen(String name) throws Exception {
        settle(300);
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        java.awt.Robot awt = new java.awt.Robot();
        java.awt.image.BufferedImage img = awt.createScreenCapture(
                new java.awt.Rectangle(0, 0, screen.width, screen.height));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("[pages-screenshot] (screen) wrote " + out.getAbsolutePath()
                + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    /** Copies an already-written shot to a second documented file name. */
    private void copyShot(String from, String to) throws Exception {
        Files.copy(new File(IMG_DIR, from + ".png").toPath(),
                new File(IMG_DIR, to + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[pages-screenshot] copied " + from + " -> " + to);
    }
}
