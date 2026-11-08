package org.fxt.freexmltoolkit.plugins.iso20022;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginSchema;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugin.XmlScan;
import org.fxt.freexmltoolkit.plugins.IdentifierKind;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * ISO 20022 payments and SEPA: binds the official schemas of the common message versions (downloaded
 * from iso20022.org on first use), checks messages beyond the schema ({@link Iso20022Validator}),
 * and offers SEPA sample messages and an IBAN/BIC/LEI checker in its side panel. Off by default.
 */
public final class Iso20022Plugin implements FxtPlugin {

    public static final String ID = "iso20022";

    /** Message versions whose schema is registered, with their names. */
    static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("pain.001.001.03", "Customer Credit Transfer Initiation"),
            Map.entry("pain.001.001.09", "Customer Credit Transfer Initiation"),
            Map.entry("pain.001.001.12", "Customer Credit Transfer Initiation"),
            Map.entry("pain.002.001.03", "Customer Payment Status Report"),
            Map.entry("pain.002.001.10", "Customer Payment Status Report"),
            Map.entry("pain.007.001.09", "Customer Payment Reversal"),
            Map.entry("pain.008.001.02", "Customer Direct Debit Initiation"),
            Map.entry("pain.008.001.08", "Customer Direct Debit Initiation"),
            Map.entry("pain.008.001.11", "Customer Direct Debit Initiation"),
            Map.entry("pacs.002.001.10", "FI To FI Payment Status Report"),
            Map.entry("pacs.004.001.09", "Payment Return"),
            Map.entry("pacs.008.001.08", "FI To FI Customer Credit Transfer"),
            Map.entry("pacs.008.001.13", "FI To FI Customer Credit Transfer"),
            Map.entry("camt.052.001.08", "Bank To Customer Account Report"),
            Map.entry("camt.053.001.02", "Bank To Customer Statement"),
            Map.entry("camt.053.001.08", "Bank To Customer Statement"),
            Map.entry("camt.054.001.02", "Bank To Customer Debit Credit Notification"),
            Map.entry("camt.054.001.08", "Bank To Customer Debit Credit Notification"),
            Map.entry("camt.056.001.08", "FI To FI Payment Cancellation Request"));

    /** Names of the message families, for versions not listed above. */
    private static final Map<String, String> FAMILIES = Map.ofEntries(
            Map.entry("pain.001", "Customer Credit Transfer Initiation"), Map.entry("pain.002", "Customer Payment Status Report"),
            Map.entry("pain.007", "Customer Payment Reversal"), Map.entry("pain.008", "Customer Direct Debit Initiation"),
            Map.entry("pacs.002", "FI To FI Payment Status Report"), Map.entry("pacs.004", "Payment Return"),
            Map.entry("pacs.008", "FI To FI Customer Credit Transfer"), Map.entry("camt.052", "Bank To Customer Account Report"),
            Map.entry("camt.053", "Bank To Customer Statement"),
            Map.entry("camt.054", "Bank To Customer Debit Credit Notification"),
            Map.entry("camt.056", "FI To FI Payment Cancellation Request"));

    private final Iso20022Validator validator = new Iso20022Validator();

    @Override public String id() { return ID; }
    @Override public String name() { return "ISO 20022 Payments (SEPA)"; }
    @Override public String version() { return "1.0"; }
    @Override public boolean enabledByDefault() { return false; }

    @Override
    public String description() {
        return "Schemas, checks (IBAN, BIC, control sums, EPC SEPA rules) and samples for pain, pacs and camt messages.";
    }

    @Override
    public void register(PluginContext context) {
        for (var entry : MESSAGES.entrySet()) {
            String message = entry.getKey();
            context.addSchema(new PluginSchema(Iso20022Validator.NAMESPACE_PREFIX + message, schemaUrl(message),
                    "ISO 20022 " + message + " " + entry.getValue(), null));
        }
        context.addValidator(validator);
        context.addActivity(new PluginActivity(ID, "ISO 20022", "bi-bank", Workflow.VALIDATION,
                ui -> new IndustryPanel(ui, "ISO 20022 PAYMENTS",
                        "Checks ISO 20022 messages beyond their schema: IBAN, BIC and LEI check digits, the "
                                + "control counts and sums of pain.001, pain.008 and pacs.008, and for SEPA "
                                + "payments the EPC rules (EUR, amount range, character set, SLEV). The checks "
                                + "also run with every validation. The schemas of the common versions are bound "
                                + "automatically (downloaded from iso20022.org on first use).",
                        List.of(new IndustryPanel.Action("iso20022-new-sct", "New SEPA Credit Transfer (pain.001)",
                                        "bi-file-earmark-plus", ActionColor.CREATE,
                                        ui2 -> openSample(ui2, "pain.001.001.09")),
                                new IndustryPanel.Action("iso20022-new-sdd", "New SEPA Direct Debit (pain.008)",
                                        "bi-file-earmark-plus", ActionColor.CREATE,
                                        ui2 -> openSample(ui2, "pain.008.001.08"))),
                        Iso20022Plugin::describe, validator,
                        List.of(IdentifierKind.IBAN, IdentifierKind.BIC, IdentifierKind.LEI)),
                null));
    }

    /** @return the download URL of a message's schema at iso20022.org */
    static String schemaUrl(String message) {
        String area = message.substring(0, message.indexOf('.'));
        return "https://www.iso20022.org/sites/default/files/documents/messages/" + area + "/schemas/" + message + ".xsd";
    }

    /** @return what the document is, or {@code null} when it is no ISO 20022 message */
    static String describe(String content) {
        String message = Iso20022Validator.messageOf(content).orElse(null);
        if (message == null) {
            return null;
        }
        String family = message.length() >= 8 ? message.substring(0, 8) : message;
        String name = MESSAGES.getOrDefault(message, FAMILIES.getOrDefault(family, "ISO 20022 message"));
        StringBuilder text = new StringBuilder(message).append(" · ").append(name);
        try {
            XmlScan scan = XmlScan.of(content);
            List<XmlScan.Element> amounts = scan.elements().stream()
                    .filter(e -> e.localName().equals("InstdAmt") || e.localName().equals("IntrBkSttlmAmt")).toList();
            int groups = scan.named("PmtInf").size();
            if (!amounts.isEmpty()) {
                text.append("\n");
                if (groups > 0) {
                    text.append(groups).append(groups == 1 ? " payment group · " : " payment groups · ");
                }
                text.append(amounts.size()).append(amounts.size() == 1 ? " transaction" : " transactions");
                var currencies = amounts.stream().map(e -> e.attributes().getOrDefault("Ccy", "")).distinct().toList();
                if (currencies.size() == 1) {
                    BigDecimal total = BigDecimal.ZERO;
                    for (XmlScan.Element e : amounts) {
                        try {
                            total = total.add(new BigDecimal(e.text()));
                        } catch (NumberFormatException ignored) {
                            // shown by the checks
                        }
                    }
                    text.append(" · ").append(total.toPlainString()).append(' ').append(currencies.getFirst());
                }
                if (scan.elements().stream().anyMatch(e -> e.path().endsWith("SvcLvl/Cd") && e.text().equals("SEPA"))) {
                    text.append(" · SEPA");
                }
            }
        } catch (javax.xml.stream.XMLStreamException e) {
            text.append("\n(not well-formed)");
        }
        return text.toString();
    }

    /** Opens a bundled sample message as a new document. */
    static void openSample(PluginUi ui, String message) {
        try (InputStream in = Iso20022Plugin.class.getResourceAsStream("/plugins/iso20022/" + message + ".xml")) {
            if (in == null) {
                throw new IOException("The sample " + message + " is missing from the application.");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ui.editorHost().openGeneratedDocument(text, EditorFileType.XML, message + "-sample.xml");
        } catch (IOException e) {
            DialogHelper.showError(I18n.t("Iso20022Plugin.iso-20022"), I18n.t("Iso20022Plugin.the-sample-could-not-be"), String.valueOf(e.getMessage()));
        }
    }
}
