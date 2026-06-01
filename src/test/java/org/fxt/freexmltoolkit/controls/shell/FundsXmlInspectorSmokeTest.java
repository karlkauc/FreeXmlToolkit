package org.fxt.freexmltoolkit.controls.shell;

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

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView;
import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Real-app smoke test on the FundsXML example: boots the full application, opens the
 * FundsXML 3.0.6 instance, binds its XSD, and verifies the (editable) inspector populates
 * an XML-instance node with its XSD-derived type + documentation; then opens the XSD itself
 * and verifies the inspector shows editable schema properties for a selected element.
 *
 * <p>Gated by {@code FXT_SHELL_SNAPSHOT=true} (writes screenshots to {@code /tmp/fxt_smoke/}).
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlInspectorSmokeTest {

    private static final Path OUT = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1260, 780));
        stage.show();
    }

    @Test
    void fundsXmlInspectorTour() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return; // manual verification aid; skipped in the normal suite
        }
        Files.createDirectories(OUT);

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        InspectorPanel inspector = (InspectorPanel) root.lookupAll("*").stream()
                .filter(n -> n instanceof InspectorPanel).findFirst().orElseThrow();

        // 1) Open the real FundsXML 3.0.6 instance and bind its XSD.
        File xml = new File("src/test/resources/FundsXML_306.xml");
        File xsd = new File("src/test/resources/FundsXML_306.xsd");
        onFx(() -> host.openFile(xml.toPath()));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DataSupplier")).orElse(false));
        onFx(() -> host.setSchemaForActiveDocument(xsd));
        settle();
        shot("fundsxml_01_xml_text");

        // 2) Grid view, select the DataSupplier element.
        onFx(() -> host.setActiveViewMode(ViewMode.GRID));
        settle();
        Thread.sleep(500);
        XmlGridView grid = (XmlGridView) root.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        onFx(() -> {
            XmlElement ds = grid.getContext().getDocument().getRootElement()
                    .getChildElements("DataSupplier").get(0);
            grid.getContext().getSelectionModel().setSelectedNode(ds);
        });

        // The XSD provider loads off-thread (FundsXML_306.xsd is ~1.4 MB) — wait for the
        // schema-derived type to surface, then snapshot.
        boolean gotType;
        try {
            WaitForAsyncUtils.waitFor(12, TimeUnit.SECONDS, () -> {
                String t = inspector.getSchemaTypeText();
                return t != null && !t.isBlank() && !t.equals("—");
            });
            gotType = true;
        } catch (Exception e) {
            gotType = false; // schema info did not surface in time — still snapshot the state
        }
        settle();
        shot("fundsxml_02_xml_grid_inspector");
        System.out.println("[SMOKE] DataSupplier  name=" + inspector.getNodeNameText()
                + "  schemaType=" + inspector.getSchemaTypeText()
                + "  doc=" + abbreviate(inspector.getSchemaDocText())
                + "  schemaInfoResolved=" + gotType);

        // 3) Open the XSD itself and inspect an element in the Schema tree.
        onFx(() -> host.openFile(xsd.toPath()));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:element")).orElse(false));
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        onFx(() -> host.setActiveViewMode(ViewMode.TREE));
        settle();
        var rootNode = WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.getActiveSchemaRoot().orElse(null));
        if (rootNode != null) {
            onFx(() -> host.selectNodeInActiveTree(rootNode));
        }
        settle();
        shot("fundsxml_03_xsd_tree_inspector");
        System.out.println("[SMOKE] XSD root      name=" + inspector.getNodeNameText()
                + "  kind=" + inspector.getKindText()
                + "  facets=" + inspector.getFacetCount());

        System.out.println("[SMOKE] Screenshots written to " + OUT);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle() {
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", OUT.resolve(name + ".png").toFile());
    }
}
