package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExportViewDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewExport;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions;
import org.fxt.freexmltoolkit.service.export.DiagramExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the "Export View" documentation images ({@code docs/img/}): the options dialog and a
 * diagram exported with it.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*ViewExportDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ViewExportDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="expense-report">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="employee" type="xs:string"/>
                    <xs:element name="expense" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="description" type="xs:string"/>
                          <xs:element name="amount" type="xs:decimal"/>
                          <xs:element name="receipt" type="xs:anyURI" minOccurs="0"/>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:ID" use="required"/>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                  <xs:attribute name="period" type="xs:gYearMonth" use="required"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateExportScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        UnifiedShellView shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), false));

        Path file = Files.createTempDirectory("fxt-shot").resolve("expense-report.xsd");
        Files.writeString(file, SAMPLE);
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        onFx(() -> host.openFile(file));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("receipt")).orElse(false));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> ScreenshotFx.call("export-screenshot", () -> {
            if (!(ViewExport.activeView(host) instanceof XsdGraphView)) {
                host.setActiveViewMode(ViewMode.GRAPHIC);
            }
            return ViewExport.activeView(host) instanceof XsdGraphView;
        }));
        XsdGraphView graph = (XsdGraphView) ViewExport.activeView(host);
        onFx(() -> {
            expand(graph.getRootNode());
            graph.repaint();
        });
        settle(1000);

        // The options dialog, as File → Export View… opens it
        ExportViewDialog dialog = ScreenshotFx.call("export-screenshot", () -> {
            ExportViewDialog d = new ExportViewDialog(graph,
                    DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG), "expense-report");
            d.initOwner(root.getScene().getWindow());
            d.show();
            return d;
        });
        settle(1000);
        var image = ScreenshotFx.call("export-screenshot",
                () -> dialog.getDialogPane().snapshot(new SnapshotParameters(), null));
        write(image, "unified-shell-export-view-dialog");
        onFx(dialog::close);

        // What the export produces: the diagram as PNG
        ExportableDiagram diagram = graph;
        DiagramExportOptions png = new DiagramExportOptions(DiagramExportOptions.Format.PNG,
                DiagramExportOptions.Area.WHOLE, 1, DiagramExportOptions.ThemeChoice.LIGHT,
                DiagramExportOptions.Background.THEME, 16, false, DiagramExportOptions.PageSize.FIT, "");
        File out = new File(IMG_DIR, "unified-shell-export-view-result.png");
        ScreenshotFx.call("export-screenshot",
                () -> DiagramExporter.export(diagram, png, out.toPath(), Runnable::run, null))
                .get(60, TimeUnit.SECONDS);
        System.out.println("[export-screenshot] wrote " + out.getAbsolutePath());
        onFx(host::closeActiveTab);
        settle(400);
    }

    private static void expand(org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode node) {
        if (node == null) {
            return;
        }
        node.setExpanded(true);
        for (var child : node.getChildren()) {
            expand(child);
        }
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("export-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void write(javafx.scene.image.Image image, String name) throws Exception {
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("[export-screenshot] wrote " + out.getAbsolutePath());
    }
}
