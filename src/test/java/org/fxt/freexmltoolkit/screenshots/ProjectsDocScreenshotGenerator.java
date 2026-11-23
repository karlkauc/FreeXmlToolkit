package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.ProjectActions;
import org.fxt.freexmltoolkit.controls.shell.editor.ProjectSettingsDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures the project images for {@code docs/guides/projects.md}: the Project Settings dialog with
 * its bindings ({@code project-settings}) and its scenarios ({@code project-settings-scenarios}), and the
 * Transform panel's SCENARIO section after a regression check that found differences
 * ({@code project-scenario-check}). The project and its files are synthetic and written at run time.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*ProjectsDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ProjectsDocScreenshotGenerator {

    private static final String PROJECT = """
            {
              "name": "Shop orders",
              "folders": ["orders", "schemas", "stylesheets", "expected"],
              "schemaBindings": [
                { "files": "orders/*.xml", "schema": "schemas/order.xsd",
                  "schematron": "schemas/order-rules.sch", "scenario": "Order summary" },
                { "files": "archive/**/*.xml", "schema": "schemas/order.xsd" },
                { "files": "config/*.json", "schema": "schemas/config.schema.json" }
              ],
              "catalogs": [],
              "scenarios": [
                { "name": "Order summary", "stylesheet": "stylesheets/order-summary.xsl", "input": "${file}",
                  "parameters": { "currency": "EUR" }, "outputFormat": "HTML", "output": "out/${name}.html" },
                { "name": "Order totals", "stylesheet": "stylesheets/order-totals.xsl", "input": "orders/order-1042.xml",
                  "parameters": {}, "outputFormat": "XML", "expected": "expected/order-1042-totals.xml",
                  "compare": { "tolerance": 0.001, "ignoreComments": true } },
                { "name": "Open items", "stylesheet": "stylesheets/open-items.xq", "input": "${file}",
                  "parameters": {}, "outputFormat": "XML", "output": "out/${name}-open.xml" }
              ]
            }
            """;

    private static final String TOTALS_XSL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs">
              <xsl:output method="xml" indent="yes"/>
              <xsl:template match="/order">
                <totals order="{@id}" currency="EUR">
                  <xsl:for-each select="items/item">
                    <line sku="{@sku}" amount="{format-number(xs:decimal(@quantity) * xs:decimal(@price), '0.00')}"/>
                  </xsl:for-each>
                  <items><xsl:value-of select="count(items/item)"/></items>
                  <total><xsl:value-of
                      select="format-number(sum(items/item/(xs:decimal(@quantity) * xs:decimal(@price))), '0.00')"/></total>
                </totals>
              </xsl:template>
            </xsl:stylesheet>
            """;

    /** The expected result of an older stylesheet: one amount and the total differ, {@code items} is missing. */
    private static final String EXPECTED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Expected result of the scenario "Order totals" -->
            <totals order="PO-1042" currency="EUR">
              <line sku="BK-101" amount="49.80"/>
              <line sku="BK-207" amount="37.50"/>
              <line sku="BK-318" amount="36.00"/>
              <total>123.30</total>
            </totals>
            """;

    private final DocShell doc = new DocShell("projects-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @AfterEach
    void closeProject() {
        // the open project and the recent list are stored settings: later generators start without them
        doc.onFx(() -> {
            ProjectService.getInstance().close();
            ProjectService.getInstance().clearRecent();
        });
    }

    @Test
    void generateProjectScreenshots() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path dir = DocFiles.work("shop-project");
        Path projectFile = dir.resolve("shop.fxtproj");
        Files.writeString(projectFile, PROJECT);
        Path orders = Files.createDirectories(dir.resolve("orders"));
        for (int i = 0; i < 3; i++) {
            Path source = DocFiles.gettingStarted("order.xml");
            Files.writeString(orders.resolve("order-" + (1042 + i) + ".xml"), Files.readString(source)
                    .replace("xsi:noNamespaceSchemaLocation=\"order.xsd\"", "xsi:noNamespaceSchemaLocation=\"../schemas/order.xsd\"")
                    .replace("PO-1042", "PO-" + (1042 + i)));
        }
        Path schemas = Files.createDirectories(dir.resolve("schemas"));
        Files.copy(DocFiles.gettingStarted("order.xsd"), schemas.resolve("order.xsd"));
        Files.writeString(schemas.resolve("order-rules.sch"), """
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <sch:pattern>
                    <sch:rule context="item">
                      <sch:assert test="number(@price) gt 0">An item needs a price above zero.</sch:assert>
                    </sch:rule>
                  </sch:pattern>
                </sch:schema>
                """);
        Path stylesheets = Files.createDirectories(dir.resolve("stylesheets"));
        Files.copy(DocFiles.gettingStarted("order-summary.xslt"), stylesheets.resolve("order-summary.xsl"));
        Files.writeString(stylesheets.resolve("order-totals.xsl"), TOTALS_XSL);
        Files.writeString(stylesheets.resolve("open-items.xq"), "<open>{ //item[@quantity > 1] }</open>\n");
        Files.writeString(Files.createDirectories(dir.resolve("expected")).resolve("order-1042-totals.xml"), EXPECTED);

        doc.onFx(() -> ProjectActions.openProject(projectFile));
        doc.open(orders.resolve("order-1042.xml"), "PO-1042");
        doc.activity(Activity.EXPLORER);
        doc.settle(1000);
        if (doc.findType(ExplorerPanel.class) == null) {
            throw new IllegalStateException("no Explorer");
        }

        // --- Project Settings: which schema, Schematron and scenario belong to which files; the scenarios ---
        doc.scene("project-settings", () -> {
            ProjectSettingsDialog dialog = doc.fx(() -> {
                ProjectSettingsDialog d = new ProjectSettingsDialog(ProjectService.getInstance().current().orElseThrow());
                d.initOwner(doc.stage());
                d.show();
                return d;
            });
            doc.settle(800);
            TabPane tabs = (TabPane) doc.fx(() -> dialog.getDialogPane().lookup("#project-settings-tabs"));
            doc.onFx(() -> {
                tabs.setPrefWidth(980); // every column of the scenario table shows
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
                tabs.getSelectionModel().select(1);
                ((TableView<?>) dialog.getDialogPane().lookup("#project-settings-binding-table")).getSelectionModel().select(0);
            });
            doc.settle(800);
            doc.shotNode(dialog.getDialogPane(), "project-settings");
            doc.onFx(() -> {
                tabs.getSelectionModel().select(2);
                ((TableView<?>) dialog.getDialogPane().lookup("#project-settings-scenario-table")).getSelectionModel().select(1);
            });
            doc.settle(800);
            doc.shotNode(dialog.getDialogPane(), "project-settings-scenarios");
            doc.onFx(dialog::close);
            doc.settle(400);
        });

        // --- Transform panel, SCENARIO: Check found differences from the expected result ---
        doc.scene("project-scenario-check", () -> {
            doc.activity(Activity.TRANSFORM);
            TransformPanel panel = doc.find(".fxt-transform-panel");
            doc.onFx(() -> {
                @SuppressWarnings("unchecked")
                ComboBox<String> scenario = (ComboBox<String>) panel.lookup("#transform-scenario");
                scenario.setValue("Order totals");
            });
            doc.settle(800);
            doc.onFx(panel::onCheckScenario);
            doc.waitUntilFx(90, () -> {
                Node actions = panel.lookup("#transform-check-actions");
                return actions != null && actions.isVisible();
            });
            doc.onFx(() -> host.bottomDock().setPrefHeight(300));
            doc.settle(1500);
            doc.shot("project-scenario-check");
        });
    }
}
