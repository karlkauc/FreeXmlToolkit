package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures the validation images: the PROBLEMS panel with many problems grouped by rule
 * ({@code problems-grouped}), a Schematron result with failed asserts in the panel and as editor
 * markers ({@code schematron-problems}) and the quick-fix chooser of the lightbulb on such a problem
 * ({@code schematron-quick-fix}). The product catalogue, its schema and its rules are synthetic and
 * written at run time.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*ProblemsDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ProblemsDocScreenshotGenerator {

    private static final String SCHEMA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="product" type="ProductType" maxOccurs="unbounded"/>
                  </xs:sequence>
                  <xs:attribute name="updated" type="xs:date" use="required"/>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="ProductType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                  <xs:element name="category" type="CategoryType"/>
                  <xs:element name="price" type="PriceType"/>
                  <xs:element name="stock" type="xs:nonNegativeInteger"/>
                  <xs:element name="released" type="xs:date" minOccurs="0"/>
                  <xs:element name="description" type="xs:string" minOccurs="0"/>
                </xs:sequence>
                <xs:attribute name="sku" type="SkuType" use="required"/>
                <xs:attribute name="status" type="StatusType" default="active"/>
              </xs:complexType>
              <xs:complexType name="PriceType">
                <xs:simpleContent>
                  <xs:extension base="xs:decimal">
                    <xs:attribute name="currency" type="CurrencyType" use="required"/>
                  </xs:extension>
                </xs:simpleContent>
              </xs:complexType>
              <xs:simpleType name="SkuType">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{2}-[0-9]{4}"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="StatusType">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="active"/><xs:enumeration value="discontinued"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="CurrencyType">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="EUR"/><xs:enumeration value="USD"/><xs:enumeration value="GBP"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="CategoryType">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="furniture"/><xs:enumeration value="lighting"/>
                  <xs:enumeration value="storage"/><xs:enumeration value="accessories"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private static final String RULES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
                        xmlns:sqf="http://www.schematron-quickfix.com/validator/process"
                        xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <sch:title>Catalogue rules</sch:title>
              <sch:pattern id="product-rules">
                <sch:rule context="product">
                  <sch:report id="discontinued-stock" test="@status = 'discontinued' and number(stock) gt 0"
                              sqf:fix="clearStock markActive" sqf:default-fix="clearStock"
                      >Product <sch:value-of select="@sku"/> is discontinued but still has <sch:value-of select="stock"/> in stock.</sch:report>
                  <sch:assert id="released-required" test="released" sqf:fix="addReleased"
                      >Product <sch:value-of select="@sku"/> has no release date.</sch:assert>
                  <sch:assert id="name-length" test="string-length(normalize-space(name)) ge 3"
                      >The name of product <sch:value-of select="@sku"/> is too short.</sch:assert>
                  <sch:report id="price-approval" role="warning" test="number(price) gt 1000"
                      >Price <sch:value-of select="price"/> of <sch:value-of select="@sku"/> is above 1000 and needs an approval.</sch:report>
                  <sch:assert id="description-recommended" role="info" test="description" sqf:fix="addDescription"
                      >Product <sch:value-of select="@sku"/> has no description.</sch:assert>

                  <sqf:fix id="clearStock">
                    <sqf:description><sqf:title>Set the stock to 0</sqf:title></sqf:description>
                    <sqf:replace match="stock" node-type="element" target="stock">0</sqf:replace>
                  </sqf:fix>
                  <sqf:fix id="markActive">
                    <sqf:description><sqf:title>Mark the product as active</sqf:title></sqf:description>
                    <sqf:add node-type="attribute" target="status">active</sqf:add>
                  </sqf:fix>
                  <sqf:fix id="addReleased">
                    <sqf:description><sqf:title>Add a release date…</sqf:title></sqf:description>
                    <sqf:user-entry name="date" default="'2026-01-01'">
                      <sqf:description><sqf:title>Release date (YYYY-MM-DD)</sqf:title></sqf:description>
                    </sqf:user-entry>
                    <sqf:add node-type="element" target="released" position="last-child"><xsl:value-of select="$date"/></sqf:add>
                  </sqf:fix>
                  <sqf:fix id="addDescription">
                    <sqf:description><sqf:title>Add an empty description</sqf:title></sqf:description>
                    <sqf:add node-type="element" target="description" position="last-child"/>
                  </sqf:fix>
                </sch:rule>
              </sch:pattern>
              <sch:pattern id="catalog-rules">
                <sch:rule context="catalog">
                  <sch:assert id="unique-sku" test="count(product/@sku) = count(distinct-values(product/@sku))"
                      >Every sku may occur only once in the catalogue.</sch:assert>
                </sch:rule>
              </sch:pattern>
            </sch:schema>
            """;

    private final DocShell doc = new DocShell("problems-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @Test
    void generateProblemScreenshots() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path dir = DocFiles.work("catalog");
        Path xsd = dir.resolve("catalog.xsd");
        Path rules = dir.resolve("catalog-rules.sch");
        Path broken = dir.resolve("catalog-import.xml");
        Path catalog = dir.resolve("catalog.xml");
        Files.writeString(xsd, SCHEMA);
        Files.writeString(rules, RULES);
        Files.writeString(broken, catalog(true));
        Files.writeString(catalog, catalog(false));

        // --- many problems of an XSD + Schematron run, grouped by rule ---
        doc.scene("problems-grouped", () -> {
            doc.open(broken, "<catalog");
            doc.activity(Activity.VALIDATION);
            ValidationPanel panel = doc.find(".fxt-validation-panel");
            bindRules(panel, rules, broken);
            doc.onFx(panel::revalidate);
            doc.waitUntilFx(60, () -> host.getActiveProblems().size() > 20);
            doc.settle(800);
            doc.onFx(() -> {
                Region problems = doc.find(".fxt-problems-panel");
                problems.setPrefHeight(470);
                ComboBox<?> groupBy = doc.find("#problems-group-by");
                groupBy.getSelectionModel().select(2); // Rule
            });
            doc.settle(600);
            doc.onFx(() -> {
                TreeView<?> tree = doc.find("#problems-panel-tree");
                var groups = tree.getRoot().getChildren();
                // open the first group so its occurrences show under their rule, the other rules below it
                groups.getFirst().setExpanded(true);
            });
            doc.settle(800);
            doc.shot("problems-grouped");
            doc.onFx(() -> {
                ComboBox<?> groupBy = doc.find("#problems-group-by");
                groupBy.getSelectionModel().select(0); // back to the list (the choice is a stored setting)
                Region problems = doc.find(".fxt-problems-panel");
                problems.setPrefHeight(300);
            });
            doc.onFx(host::closeActiveTab);
            doc.settle(400);
        });

        // --- a Schematron result: failed asserts in PROBLEMS and as markers in the editor ---
        doc.scene("schematron-problems", () -> {
            doc.open(catalog, "<catalog");
            doc.activity(Activity.VALIDATION);
            ValidationPanel panel = doc.find(".fxt-validation-panel");
            bindRules(panel, rules, catalog);
            doc.onFx(panel::revalidate);
            doc.waitUntilFx(60, () -> host.getActiveProblems().size() >= 4);
            doc.settle(1200);
            int line = lineOf(Files.readString(catalog), "sku=\"ST-0312\"");
            doc.onFx(() -> host.goToLine(line, 5));
            doc.settle(800);
            doc.shot("schematron-problems");
        });

        // --- the quick fixes of that problem: Alt+Enter on its line (the lightbulb's chooser) ---
        doc.scene("schematron-quick-fix", () -> {
            int line = lineOf(Files.readString(catalog), "sku=\"ST-0312\"");
            doc.onFx(() -> host.goToLine(line, 5));
            doc.settle(600);
            // scroll the line up, so the chooser opens over the editor and not over the PROBLEMS header
            doc.onFx(() -> host.getActiveCodeArea().showParagraphAtTop(line - 6));
            doc.settle(600);
            doc.onFx(host::focusActiveEditor);
            doc.settle(500);
            org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();
            robot.push(KeyCode.ALT, KeyCode.ENTER);
            doc.settle(800);
            doc.shotWindow("schematron-quick-fix");
            robot.push(KeyCode.ESCAPE);
            doc.settle(300);
        });
    }

    /** Chooses the Schematron in the Validation panel's source row (the file itself opens behind the document). */
    private void bindRules(ValidationPanel panel, Path rules, Path document) throws Exception {
        doc.call(panel, "pickSchematron", rules.toFile());
        doc.settle(1200);
        doc.open(document, "<catalog");
    }

    private static int lineOf(String text, String marker) {
        int at = text.indexOf(marker);
        return (int) text.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
    }

    /** A product catalogue; the {@code broken} one also violates the schema in many places. */
    private static String catalog(boolean broken) {
        String[][] products = {
                // sku, status, name, category, price, currency, stock, released, description
                {"FU-1001", "active", "Oak desk", "furniture", "349.00", "EUR", "12", "2025-03-01", "Solid oak, 160 cm"},
                {"FU-1002", "active", "Desk chair", "furniture", "189.50", "EUR", "40", "2025-03-01", null},
                {"LI-2001", "active", "Floor lamp", "lighting", "79.90", "EUR", "25", null, "Dimmable"},
                {"ST-0312", "discontinued", "Filing cabinet", "storage", "229.00", "EUR", "7", "2023-09-15", null},
                {"FU-1003", "active", "Conference table", "furniture", "1480.00", "EUR", "3", "2025-06-20", "Seats twelve"},
                {"AC-4001", "active", "Monitor arm", "accessories", "59.00", "USD", "64", "2024-11-05", "Gas spring"},
                {"LI-2002", "discontinued", "Desk lamp", "lighting", "34.90", "EUR", "18", null, null},
                {"ST-0313", "active", "Bookshelf", "storage", "129.00", "GBP", "9", "2025-01-10", "Five shelves"},
                {"AC-4002", "active", "Cable tray", "accessories", "19.90", "EUR", "150", "2024-11-05", null},
                {"FU-1004", "active", "Standing desk", "furniture", "1120.00", "EUR", "6", "2026-02-01", "Electric, memory"},
        };
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<catalog xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                .append("         xsi:noNamespaceSchemaLocation=\"catalog.xsd\" updated=\"2026-11-20\">\n");
        int rounds = broken ? 3 : 1;
        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i < products.length; i++) {
                String[] p = products[i];
                String sku = round == 0 ? p[0] : p[0].substring(0, 3) + (Integer.parseInt(p[0].substring(3)) + round * 100);
                String price = p[4];
                String currency = p[5];
                String stock = p[6];
                String category = p[3];
                String released = p[7];
                if (broken) {
                    // typical import mistakes, spread over the file
                    switch ((i + round * 3) % 10) {
                        case 1 -> price = price.replace('.', ',');
                        case 2 -> currency = "EURO";
                        case 4 -> stock = "-" + stock;
                        case 5 -> sku = sku.toLowerCase(java.util.Locale.ROOT);
                        case 7 -> category = "office";
                        case 8 -> released = released == null ? null : "05.11.2024";
                        default -> { }
                    }
                }
                xml.append("  <product sku=\"").append(sku).append("\" status=\"").append(p[1]).append("\">\n");
                xml.append("    <name>").append(p[2]).append("</name>\n");
                xml.append("    <category>").append(category).append("</category>\n");
                xml.append("    <price currency=\"").append(currency).append("\">").append(price).append("</price>\n");
                xml.append("    <stock>").append(stock).append("</stock>\n");
                if (released != null) {
                    xml.append("    <released>").append(released).append("</released>\n");
                }
                if (p[8] != null) {
                    xml.append("    <description>").append(p[8]).append("</description>\n");
                }
                xml.append("  </product>\n");
            }
        }
        return xml.append("</catalog>\n").toString();
    }
}
