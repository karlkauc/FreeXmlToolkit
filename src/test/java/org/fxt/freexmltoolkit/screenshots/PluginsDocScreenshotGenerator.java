package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.plugins.findatex.FinDatExTemplates;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the screenshots of the industry plugin pages ({@code docs/plugin-*.md}) into
 * {@code docs/img/plugin-*.png}: each plugin's side panel with a sample document and the result of
 * <em>Check Active Document</em>.
 *
 * <p>Switches the four industry plugins on (they are off by default) before the shell is built, and
 * restores the settings afterwards. Run via the {@code docScreenshots} Gradle task on a real display:
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class PluginsDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private static final List<String> PLUGINS = List.of("iso20022", "ebinterface", "findatex", "xbrl",
            "kag-meldewesen", "esma-reporting");

    private final Map<String, String> previous = new java.util.HashMap<>();
    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        ServiceRegistry.initialize();
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        for (String id : PLUGINS) {
            previous.put(id, props.get(PluginManager.enabledKey(id)));
            props.set(PluginManager.enabledKey(id), "true");
        }
        PluginManager.getInstance().resetForTesting();
        PluginManager.getInstance().loadAll();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @AfterEach
    void restore() {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        for (String id : PLUGINS) {
            String old = previous.get(id);
            props.set(PluginManager.enabledKey(id), old == null ? "" : old);
        }
        PluginManager.getInstance().resetForTesting();
    }

    @Test
    void generatePluginScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle(600);

        // --- ISO 20022: the SEPA credit transfer sample, checked; the identifier checker in use ---
        String pain001 = resource("/plugins/iso20022/pain.001.001.09.xml");
        open(host, pain001, EditorFileType.XML, "pain.001.001.09-sample.xml");
        IndustryPanel iso = showActivity("iso20022");
        identifier(iso, "AT61 1904 3002 3457 3202");
        check(iso);
        shot("plugin-iso20022");

        // with problems: a wrong control sum, a non-SEPA charge bearer, a broken IBAN
        String brokenPain = pain001
                .replaceFirst("<CtrlSum>1234.50</CtrlSum>", "<CtrlSum>1300.00</CtrlSum>")
                .replaceFirst("<ChrgBr>SLEV</ChrgBr>", "<ChrgBr>SHAR</ChrgBr>")
                .replaceFirst("(<IBAN>[A-Z]{2}\\d{2}[A-Z0-9]*?)\\d(</IBAN>)", "$10$2")
                .replaceFirst("<EndToEndId>([^<]*)</EndToEndId>", "<EndToEndId>Rechnung Nr. 4711 – März</EndToEndId>");
        open(host, brokenPain, EditorFileType.XML, "pain.001-with-errors.xml");
        iso = showActivity("iso20022");
        identifier(iso, "");
        check(iso);
        shot("plugin-iso20022-problems");

        // --- ebInterface: the sample invoice, checked ---
        String invoice = resource("/plugins/ebinterface/invoice-6p1.xml");
        open(host, invoice, EditorFileType.XML, "ebinterface-6p1-invoice.xml");
        IndustryPanel eb = showActivity("ebinterface");
        identifier(eb, "ATU13585627");
        check(eb);
        shot("plugin-ebinterface");

        String brokenInvoice = invoice
                .replaceFirst("<LineItemAmount>([^<]*)</LineItemAmount>", "<LineItemAmount>99.00</LineItemAmount>")
                .replaceFirst("<PayableAmount>147.50</PayableAmount>", "<PayableAmount>140.00</PayableAmount>")
                .replaceFirst("<VATIdentificationNumber>ATU13585627</VATIdentificationNumber>",
                        "<VATIdentificationNumber>ATU13585628</VATIdentificationNumber>");
        open(host, brokenInvoice, EditorFileType.XML, "ebinterface-with-errors.xml");
        eb = showActivity("ebinterface");
        identifier(eb, "");
        check(eb);
        shot("plugin-ebinterface-problems");

        // --- FinDatEx: a TPT V8.0 file, one instrument per row, with typical mistakes ---
        open(host, tptSample(), EditorFileType.OTHER, "TPT-V8.0-sample.csv");
        IndustryPanel findatex = showActivity("findatex");
        check(findatex);
        shot("plugin-findatex");

        // --- XBRL: a small instance; Arelle is not installed here, the panel says so ---
        open(host, XBRL_INSTANCE, EditorFileType.XML, "report.xbrl");
        showActivity("xbrl");
        shot("plugin-xbrl");

        // --- KAG-Meldewesen: the fictitious AIF file, checked; then with a broken LEI and period ---
        String aif = resource("/plugins/kagmeldewesen/examples/aif-example.xml");
        open(host, aif, EditorFileType.XML, "aif-example.xml");
        IndustryPanel kag = showActivity("kag-meldewesen");
        check(kag);
        shot("plugin-kag-meldewesen");

        String brokenAif = aif
                .replaceFirst("<AIFIdentifierLEI>5299009FXTAIF0000045</AIFIdentifierLEI>",
                        "<AIFIdentifierLEI>5299009FXTAIF0000046</AIFIdentifierLEI>")
                .replaceFirst("<ReportingPeriodEndDate>2025-12-31</ReportingPeriodEndDate>",
                        "<ReportingPeriodEndDate>2024-12-31</ReportingPeriodEndDate>");
        open(host, brokenAif, EditorFileType.XML, "aif-with-errors.xml");
        kag = showActivity("kag-meldewesen");
        check(kag);
        shot("plugin-kag-meldewesen-problems");

        // --- ESMA Reporting: the fictitious EMIR error report, checked; then with a broken LEI ---
        String emir = resource("/plugins/esmareporting/examples/emir-eror-example.xml");
        open(host, emir, EditorFileType.XML, "emir-eror-example.xml");
        IndustryPanel esma = showActivity("esma-reporting");
        check(esma);
        shot("plugin-esma-reporting");

        String brokenEmir = emir.replace("<LEI>5299009FXTBANK000080</LEI>", "<LEI>5299009FXTBANK000081</LEI>");
        open(host, brokenEmir, EditorFileType.XML, "emir-with-errors.xml");
        esma = showActivity("esma-reporting");
        check(esma);
        shot("plugin-esma-reporting-problems");
    }

    /** A TPT V8.0 file with two instruments; the second has a German date, a short CIC and a bad LEI. */
    private static String tptSample() {
        FinDatExTemplates.Template tpt = FinDatExTemplates.all().stream()
                .filter(t -> t.template.equals("TPT") && t.version.equals("V8.0")).findFirst().orElseThrow();
        List<FinDatExTemplates.Field> fields = tpt.fields.stream()
                .filter(f -> f.isGenerallyMandatory() || f.code.startsWith("12_") || f.code.startsWith("32_"))
                .toList();
        StringBuilder csv = new StringBuilder(String.join(";", fields.stream().map(f -> f.code).toList())).append('\n');
        csv.append(row(fields, "DE31", "DE0001102341", "2026-09-30", "529900T8BM49AURSDO55")).append('\n');
        csv.append(row(fields, "D31", "XS2462605671", "30.09.2026", "529900T8BM49AURSDO56")).append('\n');
        return csv.toString();
    }

    private static String row(List<FinDatExTemplates.Field> fields, String cic, String isin, String date, String lei) {
        List<String> values = new ArrayList<>();
        for (FinDatExTemplates.Field f : fields) {
            values.add(switch (f.type == null ? "text" : f.type) {
                case "version" -> "V8.0";
                case "date" -> f.code.startsWith("6_") ? date : "2026-09-30";
                case "datetime" -> "2026-10-05 09:00:00";
                case "currency" -> "EUR";
                case "country" -> "AT";
                case "number" -> "1250000.50";
                case "integer" -> f.min != null ? String.valueOf(f.min) : "1";
                case "enum" -> f.values == null || f.values.isEmpty() ? "" : f.values.getFirst();
                case "identifierType" -> "1";
                case "identifier" -> f.code.startsWith("1_") ? "LU0274208692" : "lei".equals(f.type1) ? lei : isin;
                case "lei" -> lei;
                case "cic" -> cic;
                default -> f.code.startsWith("123_") ? "AT0" : "Example Bond Fund";
            });
        }
        return String.join(";", values);
    }

    private static final String XBRL_INSTANCE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance"
                        xmlns:link="http://www.xbrl.org/2003/linkbase"
                        xmlns:xlink="http://www.w3.org/1999/xlink"
                        xmlns:iso4217="http://www.xbrl.org/2003/iso4217"
                        xmlns:ifrs-full="https://xbrl.ifrs.org/taxonomy/2024-03-27/ifrs-full">
              <link:schemaRef xlink:type="simple"
                  xlink:href="https://xbrl.ifrs.org/taxonomy/2024-03-27/full_ifrs_entry_point_2024-03-27.xsd"/>
              <xbrli:context id="FY2025">
                <xbrli:entity><xbrli:identifier scheme="http://standards.iso.org/iso/17442">529900T8BM49AURSDO55</xbrli:identifier></xbrli:entity>
                <xbrli:period><xbrli:startDate>2025-01-01</xbrli:startDate><xbrli:endDate>2025-12-31</xbrli:endDate></xbrli:period>
              </xbrli:context>
              <xbrli:context id="FY2025-end">
                <xbrli:entity><xbrli:identifier scheme="http://standards.iso.org/iso/17442">529900T8BM49AURSDO55</xbrli:identifier></xbrli:entity>
                <xbrli:period><xbrli:instant>2025-12-31</xbrli:instant></xbrli:period>
              </xbrli:context>
              <xbrli:unit id="EUR"><xbrli:measure>iso4217:EUR</xbrli:measure></xbrli:unit>
              <ifrs-full:Revenue contextRef="FY2025" unitRef="EUR" decimals="-3">125400000</ifrs-full:Revenue>
              <ifrs-full:ProfitLoss contextRef="FY2025" unitRef="EUR" decimals="-3">8200000</ifrs-full:ProfitLoss>
              <ifrs-full:Assets contextRef="FY2025-end" unitRef="EUR" decimals="-3">310000000</ifrs-full:Assets>
              <ifrs-full:Equity contextRef="FY2025-end" unitRef="EUR" decimals="-3">95000000</ifrs-full:Equity>
            </xbrli:xbrl>
            """;

    private static String resource(String path) throws IOException {
        try (InputStream in = PluginsDocScreenshotGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void open(EditorHost host, String text, EditorFileType type, String name) {
        onFx(() -> host.openGeneratedDocument(text, type, name));
        settle(800);
    }

    /** Selects the plugin's activity and returns its (visible) side panel. */
    private IndustryPanel showActivity(String id) throws Exception {
        onFx(() -> shell.getSelectionModel().select(Activity.fromId(id).orElseThrow()));
        settle(600);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> visiblePanel() != null);
        return visiblePanel();
    }

    private IndustryPanel visiblePanel() {
        return ScreenshotFx.call("plugin-screenshot", () -> {
            for (Node node : root.lookupAll("*")) {
                if (node instanceof IndustryPanel panel && shown(panel)) {
                    return panel;
                }
            }
            return null;
        });
    }

    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    private void identifier(IndustryPanel panel, String value) {
        onFx(() -> ((TextField) panel.lookup("#industry-identifier")).setText(value));
    }

    private void check(IndustryPanel panel) throws Exception {
        onFx(panel::onCheck);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> ScreenshotFx.call("plugin-screenshot", () -> {
            Node result = panel.lookup("#industry-check-result");
            return result instanceof javafx.scene.control.Label label && !label.getText().startsWith("Checking");
        }));
        settle(400);
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("plugin-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = ScreenshotFx.call("plugin-screenshot", () -> shell.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[plugin-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }
}
