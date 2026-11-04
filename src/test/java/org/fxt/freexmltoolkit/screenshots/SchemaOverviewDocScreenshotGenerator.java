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
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the XSD "Overview" view mode for the documentation ({@code docs/img/}): the
 * component table of a sample schema with an element selected and its Attributes tab, light
 * and dark.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SchemaOverviewDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:exp="urn:example:expenses"
                       targetNamespace="urn:example:expenses" elementFormDefault="qualified">
              <xs:import namespace="urn:example:common" schemaLocation="common.xsd"/>
              <xs:annotation>
                <xs:documentation>Expense reports of the travel department.</xs:documentation>
              </xs:annotation>
              <xs:element name="expense-report" type="exp:ReportType">
                <xs:annotation><xs:documentation>The root of every report.</xs:documentation></xs:annotation>
                <xs:key name="expenseKey">
                  <xs:selector xpath="exp:expense"/>
                  <xs:field xpath="@id"/>
                </xs:key>
              </xs:element>
              <xs:complexType name="ReportType">
                <xs:sequence>
                  <xs:element ref="exp:expense" maxOccurs="unbounded"/>
                </xs:sequence>
                <xs:attribute name="period" type="xs:gYearMonth" use="required"/>
              </xs:complexType>
              <xs:element name="expense">
                <xs:annotation><xs:documentation>One expense item.</xs:documentation></xs:annotation>
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="description" type="xs:string"/>
                    <xs:element name="amount" type="xs:decimal"/>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:ID" use="required"/>
                  <xs:attribute name="mealtype" type="exp:MealType" default="dinner"/>
                  <xs:attributeGroup ref="exp:Audit"/>
                </xs:complexType>
              </xs:element>
              <xs:simpleType name="MealType">
                <xs:annotation><xs:documentation>Kinds of meals that can be claimed.</xs:documentation></xs:annotation>
                <xs:restriction base="xs:string">
                  <xs:enumeration value="breakfast"/>
                  <xs:enumeration value="lunch"/>
                  <xs:enumeration value="dinner"/>
                  <xs:enumeration value="other"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="emailType">
                <xs:restriction base="xs:string">
                  <xs:pattern value="[^@]+@[^@]+"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:attributeGroup name="Audit">
                <xs:attribute name="createdBy" type="exp:emailType"/>
              </xs:attributeGroup>
              <xs:notation name="pdf" public="application/pdf"/>
            </xs:schema>
            """;

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateOverviewScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        UnifiedShellView shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), false));

        Path file = Files.createTempDirectory("fxt-shot").resolve("expenses.xsd");
        Files.writeString(file, SAMPLE);
        Files.writeString(file.resolveSibling("common.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:example:common">
                  <xs:simpleType name="Currency"><xs:restriction base="xs:string"/></xs:simpleType>
                </xs:schema>
                """);
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        onFx(() -> host.openFile(file));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("MealType")).orElse(false));
        onFx(() -> host.setActiveViewMode(ViewMode.OVERVIEW));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> host.getActiveSchema().isPresent());
        settle(800);
        XsdNode expense = host.getActiveSchema().orElseThrow().getChildren().stream()
                .filter(n -> "expense".equals(n.getName())).findFirst().orElseThrow();
        onFx(() -> host.selectNodeInActiveTree(expense));
        onFx(() -> root.lookupAll("#overview-detail-tabs").forEach(n ->
                ((javafx.scene.control.TabPane) n).getSelectionModel().select(1)));
        settle(1200);
        shot(shell, "unified-shell-schema-overview");
        onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), true));
        settle(800);
        shot(shell, "unified-shell-schema-overview-dark");
        onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), false));
        onFx(host::closeActiveTab);
        settle(400);
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("overview-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(UnifiedShellView shell, String name) throws Exception {
        var img = ScreenshotFx.call("overview-screenshot", () -> shell.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[overview-screenshot] wrote " + out.getAbsolutePath());
    }
}
