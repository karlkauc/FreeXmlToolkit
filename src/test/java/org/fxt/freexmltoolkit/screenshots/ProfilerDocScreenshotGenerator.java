package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.ProfileView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures the XSLT profiler and the XSpec result: a profiled run of a small stylesheet with several
 * templates and functions - the hotspot table ({@code profile-table}) and the flame graph
 * ({@code profile-flame-graph}) - and an XSpec run with one failing test ({@code xspec-result}).
 * Stylesheet, input and tests are synthetic and written at run time.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*ProfilerDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ProfilerDocScreenshotGenerator {

    private static final String STYLESHEET = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema"
                            xmlns:o="urn:example:orders" exclude-result-prefixes="xs o">
              <xsl:output method="xml" indent="yes"/>

              <!-- the total of one order -->
              <xsl:function name="o:total" as="xs:decimal">
                <xsl:param name="order" as="element(order)"/>
                <xsl:sequence select="sum($order/item ! (xs:decimal(@quantity) * xs:decimal(@price)))"/>
              </xsl:function>

              <!-- small, medium or large, by the total -->
              <xsl:function name="o:size" as="xs:string">
                <xsl:param name="total" as="xs:decimal"/>
                <xsl:sequence select="if ($total ge 1000) then 'large' else if ($total ge 250) then 'medium' else 'small'"/>
              </xsl:function>

              <!-- how many orders the same customer placed before this one (deliberately slow) -->
              <xsl:function name="o:earlier-orders" as="xs:integer">
                <xsl:param name="order" as="element(order)"/>
                <xsl:sequence select="count($order/preceding-sibling::order[@customer = $order/@customer])"/>
              </xsl:function>

              <xsl:template match="/orders">
                <report orders="{count(order)}">
                  <xsl:call-template name="summary"/>
                  <xsl:apply-templates select="order"/>
                </report>
              </xsl:template>

              <xsl:template name="summary">
                <summary>
                  <xsl:for-each-group select="order" group-by="@country">
                    <xsl:sort select="current-grouping-key()"/>
                    <country code="{current-grouping-key()}" orders="{count(current-group())}"
                             total="{format-number(sum(current-group() ! o:total(.)), '0.00')}"/>
                  </xsl:for-each-group>
                </summary>
              </xsl:template>

              <xsl:template match="order">
                <xsl:variable name="total" select="o:total(.)"/>
                <order id="{@id}" size="{o:size($total)}" total="{format-number($total, '0.00')}"
                       earlier="{o:earlier-orders(.)}">
                  <xsl:apply-templates select="item"/>
                </order>
              </xsl:template>

              <xsl:template match="item">
                <line sku="{@sku}"><xsl:value-of select="format-number(@quantity * @price, '0.00')"/></line>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String UTILS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema"
                            xmlns:o="urn:example:orders" exclude-result-prefixes="xs o">

              <xsl:function name="o:size" as="xs:string">
                <xsl:param name="total" as="xs:decimal"/>
                <xsl:sequence select="if ($total ge 1000) then 'large' else if ($total ge 250) then 'medium' else 'small'"/>
              </xsl:function>

              <xsl:function name="o:gross" as="xs:decimal">
                <xsl:param name="net" as="xs:decimal"/>
                <xsl:param name="vat-percent" as="xs:decimal"/>
                <xsl:sequence select="round($net * (100 + $vat-percent)) div 100"/>
              </xsl:function>

              <xsl:template match="item">
                <line sku="{@sku}"><xsl:value-of select="format-number(@quantity * @price, '0.00')"/></line>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String XSPEC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec"
                           xmlns:o="urn:example:orders" stylesheet="order-utils.xsl">

              <x:scenario label="o:size">
                <x:scenario label="of a total of 120.00">
                  <x:call function="o:size"><x:param select="120.00"/></x:call>
                  <x:expect label="is small" select="'small'"/>
                </x:scenario>
                <x:scenario label="of a total of 250.00">
                  <x:call function="o:size"><x:param select="250.00"/></x:call>
                  <x:expect label="is medium" select="'medium'"/>
                </x:scenario>
                <x:scenario label="of a total of 1000.00">
                  <x:call function="o:size"><x:param select="1000.00"/></x:call>
                  <x:expect label="is large" select="'large'"/>
                </x:scenario>
              </x:scenario>

              <x:scenario label="o:gross">
                <x:scenario label="of 100.00 with 20 % VAT">
                  <x:call function="o:gross"><x:param select="100.00"/><x:param select="20"/></x:call>
                  <x:expect label="is 120" select="120"/>
                </x:scenario>
              </x:scenario>

              <x:scenario label="an item with quantity 3 and price 12.00">
                <x:context><item sku="BK-318" quantity="3" price="12.00">Pocket guide</item></x:context>
                <x:expect label="becomes a line with the amount">
                  <line sku="BK-318">36.0</line>
                </x:expect>
              </x:scenario>
            </x:description>
            """;

    private final DocShell doc = new DocShell("profiler-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @Test
    void generateProfilerScreenshots() throws Exception {
        var shell = doc.boot();
        EditorHost host = doc.host();
        Path dir = DocFiles.work("orders");
        Path xslt = dir.resolve("order-report.xsl");
        Path input = dir.resolve("orders.xml");
        Files.writeString(xslt, STYLESHEET);
        Files.writeString(input, orders(1500));

        // --- Transform panel, "Profile run" ticked: the profile opens in its own tab ---
        doc.scene("profile-table / profile-flame-graph", () -> {
            doc.open(xslt, "o:earlier-orders");
            doc.activity(Activity.TRANSFORM);
            TransformPanel panel = doc.find(".fxt-transform-panel");
            doc.onFx(() -> {
                panel.setXsltFile(xslt.toFile());
                panel.setInputOverride(input.toFile());
                ((CheckBox) panel.lookup("#transform-profile")).setSelected(true);
                panel.transform();
            });
            doc.waitUntilFx(120, () -> doc.findType(ProfileView.class) != null
                    && doc.findType(ProfileView.class).getRowCount() > 0);
            doc.settle(800);
            ProfileView view = doc.findType(ProfileView.class);
            doc.hideOutputDock(); // the profile, not the result, is the subject
            doc.onFx(() -> host.selectTabShowing(view));
            doc.settle(800);
            doc.shot("profile-table");

            doc.onFx(() -> ((TabPane) view.lookup("#profile-tabs")).getSelectionModel().select(2));
            doc.settle(1200);
            doc.shot("profile-flame-graph");
            doc.onFx(() -> ((CheckBox) panel.lookup("#transform-profile")).setSelected(false));
            doc.closeAllTabs();
        });

        // --- an XSpec run: four tests pass, one fails (Expected and Actual side by side) ---
        doc.scene("xspec-result", () -> {
            Path utils = dir.resolve("order-utils.xsl");
            Path xspec = dir.resolve("order-utils.xspec");
            Files.writeString(utils, UTILS);
            Files.writeString(xspec, XSPEC);
            doc.open(xspec, "x:description");
            doc.activity(Activity.EXPLORER);
            doc.onFx(shell::runXSpec);
            doc.waitUntilFx(120, () -> shell.lookup("#xspec-summary") instanceof Label summary
                    && summary.getText() != null && summary.getText().matches(".*\\d.*"));
            doc.onFx(() -> {
                // the passed scenarios open too, so every test shows
                javafx.scene.control.TreeTableView<?> tree =
                        (javafx.scene.control.TreeTableView<?>) shell.lookup("#xspec-tree");
                tree.getRoot().getChildren().forEach(scenario -> scenario.setExpanded(true));
            });
            doc.settle(1200);
            doc.shot("xspec-result");
        });
    }

    /** Orders of a handful of customers, each with one to four items (synthetic). */
    private static String orders(int count) {
        String[] countries = {"AT", "DE", "CH", "IT", "NL", "FR"};
        String[] skus = {"BK-101", "BK-207", "BK-318", "FU-1001", "LI-2001", "AC-4001"};
        double[] prices = {24.90, 39.50, 12.00, 349.00, 79.90, 59.00};
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<orders>\n");
        for (int i = 1; i <= count; i++) {
            xml.append("  <order id=\"PO-").append(10000 + i).append("\" customer=\"C-").append(100 + i * 7 % 40)
                    .append("\" country=\"").append(countries[i * 5 % countries.length]).append("\">\n");
            for (int j = 0; j <= i % 4; j++) {
                int k = (i + j * 3) % skus.length;
                xml.append("    <item sku=\"").append(skus[k]).append("\" quantity=\"").append(1 + (i + j) % 5)
                        .append("\" price=\"").append(String.format(java.util.Locale.ROOT, "%.2f", prices[k]))
                        .append("\"/>\n");
            }
            xml.append("  </order>\n");
        }
        return xml.append("</orders>\n").toString();
    }
}
