package org.fxt.freexmltoolkit.screenshots;

import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the user-documentation screenshots that are referenced from {@code docs/*.md}
 * but missing from {@code docs/img/}.
 *
 * <p>This is <strong>not</strong> a normal unit test - it launches the real application and
 * captures full-window PNGs. It is excluded from the {@code test} task and must be run via the
 * dedicated {@code docScreenshots} Gradle task on a real X display:
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 *
 * <p>Capturing uses {@link java.awt.Robot} against the X framebuffer so that JavaFX popups and
 * dialogs (which are separate windows) are included in the image. Each capture is wrapped so a
 * single failure does not abort the rest of the run.
 */
@ExtendWith(ApplicationExtension.class)
class DocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");

    private static MainController mainController;
    private final FxRobot robot = new FxRobot();

    @Start
    private void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        Parent root = loader.load();
        mainController = loader.getController();

        Scene scene = new Scene(root, 1680, 1000);
        stage.setScene(scene);
        stage.setX(0);
        stage.setY(0);
        stage.setWidth(1680);
        stage.setHeight(1000);
        stage.show();
    }

    @Test
    void generateScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        settle(2500);

        // --- XSD tools (Type Library / Type Editor / Schema Analysis) ---
        safe("xsd tools", () -> {
            File xsd = example("xsd", "FundsXML4.xsd");
            runFx(() -> mainController.switchToXsdViewAndLoadFile(xsd));
            boolean ready = waitFor(() -> {
                try {
                    return field(controller("xsdController"), "cachedXsdSchema") != null;
                } catch (Exception e) {
                    return false;
                }
            }, 120_000);
            System.out.println("[screenshot] XSD schema ready=" + ready);
            settle(2000); // let updateTypeLibrary/updateTypeEditor populate the views

            // NOTE: MainController.openTypeLibrary()/openTypeEditor()/openSchemaAnalysis() reload
            // the XSD page (loadPageFromPath), which would discard the loaded schema. Select the
            // sub-tabs directly on the SAME controller instead.
            invokeStr("xsdController", "selectSubTab", "typeLibraryTab");
            settle(2000);
            capture("xsd-type-library");

            invokeStr("xsdController", "selectSubTab", "typeEditorTab");
            settle(1000);
            // The Type Editor opens with no type tabs; open a substantial complex type so the
            // graphical editor is visible.
            openComplexTypeInEditor();
            settle(2500);
            capture("xsd-type-editor");

            invokeStr("xsdController", "selectSubTab", "schemaAnalysisTab");
            settle(3000);
            capture("xsd-schema-analysis");
        });

        // --- Digital signatures sub-tabs ---
        safe("signature create-cert", () -> {
            runFx(() -> mainController.openCreateCertificate());
            settle(1500);
            capture("signature-create-cert");
        });
        safe("signature sign", () -> {
            runFx(() -> mainController.openSignXml());
            settle(1500);
            capture("signature-sign-process");
        });
        safe("signature expert", () -> {
            runFx(() -> mainController.openExpertSigning());
            settle(1500);
            capture("signature-expert");
        });

        // --- JSON editor ---
        safe("json editor", () -> {
            File json = writeTempJson();
            runFx(() -> mainController.navigateToPage("json"));
            settle(1500);
            invokeFile("jsonController", "loadJsonFile", json);
            settle(2000);
            capture("json-editor");
        });

        // --- XSLT Developer ---
        safe("xslt developer", () -> {
            File xslt = example("xslt", "FundsXML_Factsheet.xslt");
            runFx(() -> mainController.switchToXsltDeveloperAndLoadFile(xslt));
            settle(3000);
            capture("xslt-developer-overview");
        });

        // (XSLT Viewer retired in Phase 10c — quick XSLT transforms live in the
        // Unified Shell's Transform panel and the XSLT Developer.)

        // --- XSD Validation: single-file view (used for schema-xsd-support) ---
        safe("xsd validation single", () -> {
            runFx(() -> mainController.navigateToPage("xsdValidation"));
            settle(2500);
            capture("schema-xsd-support");
        });

        // --- XSD Validation: batch view ---
        safe("xsd validation batch", () -> {
            runFx(() -> mainController.navigateToPage("xsdValidation"));
            settle(1500);
            clickText("Batch Validation");
            settle(1500);
            capture("xsd-validation-batch");
        });

        // --- Schematron editor (used for schema-schematron-support) ---
        safe("schematron", () -> {
            File sch = example("schematron", "funds-business-rules-validation.sch");
            runFx(() -> mainController.switchToSchematronViewAndLoadFile(sch));
            settle(3000);
            capture("schema-schematron-support");
        });

        // --- Templates (Template Builder sub-tab) ---
        safe("templates", () -> {
            runFx(() -> mainController.openTemplateBuilder());
            settle(2500);
            capture("templates-overview");
        });

        // --- Favorites panel ---
        safe("favorites", () -> {
            runFx(() -> mainController.navigateToPage("xsltDeveloper"));
            settle(1500);
            invokeNoArg("xsltDeveloperController", "toggleFavoritesPanelPublic");
            settle(2000);
            capture("favorites-overview");
        });

        // --- IntelliSense (XML editor with schema-backed document) ---
        safe("intellisense", () -> {
            File xml = example("xml", "context-sensitive-demo.xml");
            runFx(() -> mainController.switchToXmlViewAndLoadFile(xml));
            settle(3500);
            capture("intellisense-overview");
        });
    }

    // ---------------------------------------------------------------- helpers

    private interface Capture {
        void run() throws Exception;
    }

    private void safe(String label, Capture c) {
        try {
            c.run();
        } catch (Throwable t) {
            System.out.println("[screenshot] SKIPPED '" + label + "': " + t);
        }
    }

    private static File example(String sub, String name) {
        return new File(new File(EXAMPLES, sub), name);
    }

    private File writeTempJson() throws Exception {
        File f = File.createTempFile("doc-sample", ".json");
        java.nio.file.Files.writeString(f.toPath(), """
                {
                  "fund": {
                    "name": "Global Bond Fund",
                    "currency": "EUR",
                    "shareClasses": [
                      { "isin": "LU0000000001", "nav": 102.45, "active": true },
                      { "isin": "LU0000000002", "nav": 98.10, "active": false }
                    ],
                    "totalAssets": 1532000000
                  }
                }
                """);
        f.deleteOnExit();
        return f;
    }

    private void runFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX action timed out");
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Poll a condition (evaluated on the FX thread) until true or timeout. */
    private boolean waitFor(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            boolean[] result = new boolean[1];
            runFx(() -> result[0] = condition.getAsBoolean());
            if (result[0]) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private Object controller(String field) throws Exception {
        Field f = MainController.class.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(mainController);
    }

    private static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private void invokeFile(String controllerField, String method, File arg) throws Exception {
        Object controller = controller(controllerField);
        if (controller == null) {
            throw new IllegalStateException("Controller " + controllerField + " is null");
        }
        Method m = controller.getClass().getMethod(method, File.class);
        runFx(() -> {
            try {
                m.invoke(controller, arg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void invokeStr(String controllerField, String method, String arg) throws Exception {
        Object controller = controller(controllerField);
        if (controller == null) {
            throw new IllegalStateException("Controller " + controllerField + " is null");
        }
        Method m = controller.getClass().getMethod(method, String.class);
        runFx(() -> {
            try {
                m.invoke(controller, arg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void invokeNoArg(String controllerField, String method) throws Exception {
        Object controller = controller(controllerField);
        if (controller == null) {
            throw new IllegalStateException("Controller " + controllerField + " is null");
        }
        Method m = controller.getClass().getMethod(method);
        runFx(() -> {
            try {
                m.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Find a content-rich complex type in the loaded schema and open it in the Type Editor. */
    private void openComplexTypeInEditor() throws Exception {
        Object xsdController = controller("xsdController");
        Object schema = field(xsdController, "cachedXsdSchema");
        Object mgr = field(xsdController, "currentTypeEditorManager");
        if (!(schema instanceof XsdNode root) || !(mgr instanceof TypeEditorTabManager manager)) {
            throw new IllegalStateException("schema or type-editor manager unavailable");
        }
        XsdComplexType best = findBestComplexType(root);
        if (best == null) {
            throw new IllegalStateException("no complex type found");
        }
        runFx(() -> manager.openComplexTypeTab(best));
    }

    private XsdComplexType findBestComplexType(XsdNode root) {
        XsdComplexType best = null;
        int bestChildren = -1;
        java.util.Deque<XsdNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        java.util.Set<XsdNode> seen = new java.util.HashSet<>();
        while (!stack.isEmpty()) {
            XsdNode node = stack.pop();
            if (!seen.add(node)) {
                continue;
            }
            if (node instanceof XsdComplexType ct && ct.getName() != null) {
                int count = ct.getChildren() == null ? 0 : ct.getChildren().size();
                if (count > bestChildren) {
                    bestChildren = count;
                    best = ct;
                }
            }
            if (node.getChildren() != null) {
                for (XsdNode child : node.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return best;
    }

    private void clickText(String text) {
        try {
            robot.clickOn(text);
        } catch (Exception e) {
            System.out.println("[screenshot] could not click '" + text + "': " + e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) throws InterruptedException {
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(millis);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void capture(String name) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        java.awt.Robot awt = new java.awt.Robot();
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = awt.createScreenCapture(screen);
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("[screenshot] wrote " + out.getAbsolutePath() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
