package org.fxt.freexmltoolkit.plugins.ebinterface;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
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
 * ebInterface, the Austrian e-invoice standard: binds the official schemas of versions 4.1–6.1
 * (from the standard's GitHub repository), checks invoices beyond the schema
 * ({@link EbInterfaceValidator}) and offers a sample invoice and a UID/IBAN checker. Off by default.
 */
public final class EbInterfacePlugin implements FxtPlugin {

    public static final String ID = "ebinterface";

    /** The versions whose schema is registered. */
    static final List<String> VERSIONS = List.of("4p1", "4p2", "4p3", "5p0", "6p0", "6p1");

    @Override public String id() { return ID; }
    @Override public String name() { return "ebInterface (Austrian e-invoice)"; }
    @Override public String version() { return "1.0"; }
    @Override public boolean enabledByDefault() { return false; }

    @Override
    public String description() {
        return "Schemas 4.1–6.1, checks (UID, IBAN, line and tax amounts, totals) and a sample invoice.";
    }

    @Override
    public void register(PluginContext context) {
        for (String version : VERSIONS) {
            context.addSchema(new PluginSchema(EbInterfaceValidator.NAMESPACE_PREFIX + version + "/", schemaUrl(version),
                    "ebInterface " + version.replace('p', '.') + " invoice", null));
        }
        EbInterfaceValidator validator = new EbInterfaceValidator();
        context.addValidator(validator);
        context.addActivity(new PluginActivity(ID, "ebInterface", "bi-receipt", Workflow.VALIDATION,
                ui -> new IndustryPanel(ui, "EBINTERFACE",
                        "Checks ebInterface invoices beyond their schema: Austrian VAT ids (UID), IBAN and BIC, "
                                + "line amounts (quantity × unit price), tax amounts, the tax summary and the totals, "
                                + "the payable amount and the due date. The checks also run with every validation. "
                                + "The schemas of versions 4.1 to 6.1 are bound automatically.",
                        List.of(new IndustryPanel.Action("ebinterface-new", "New ebInterface 6.1 Invoice",
                                "bi-file-earmark-plus", ActionColor.CREATE, EbInterfacePlugin::openSample)),
                        EbInterfacePlugin::describe, validator,
                        List.of(IdentifierKind.AT_UID, IdentifierKind.IBAN, IdentifierKind.BIC)),
                null));
    }

    /** @return the schema of a version in the standard's GitHub repository */
    static String schemaUrl(String version) {
        return "https://raw.githubusercontent.com/austriapro/ebinterface-standards/master/schemas/ebInterface"
                + version + "/Invoice.xsd";
    }

    /** @return what the invoice is, or {@code null} when it is no ebInterface invoice */
    static String describe(String content) {
        String version = EbInterfaceValidator.versionOf(content).orElse(null);
        if (version == null) {
            return null;
        }
        StringBuilder text = new StringBuilder("ebInterface ").append(version.replace('p', '.'));
        try {
            XmlScan scan = XmlScan.of(content);
            XmlScan.Element root = scan.root().orElseThrow();
            text.append(" · ").append(root.attributes().getOrDefault("DocumentType", "Invoice"));
            first(scan, "InvoiceNumber", 1).ifPresent(n -> text.append(' ').append(n));
            first(scan, "InvoiceDate", 1).ifPresent(d -> text.append(" of ").append(d));
            text.append('\n');
            scan.elements().stream().filter(e -> e.path().endsWith("Biller/Address/Name")).findFirst()
                    .ifPresent(e -> text.append("Biller: ").append(e.text()).append(" · "));
            long lines = scan.named("ListLineItem").size();
            text.append(lines).append(lines == 1 ? " line" : " lines");
            first(scan, "PayableAmount", 1).ifPresent(p -> text.append(" · payable ").append(p).append(' ')
                    .append(root.attributes().getOrDefault("InvoiceCurrency", "")));
        } catch (XMLStreamException | java.util.NoSuchElementException e) {
            text.append("\n(not well-formed)");
        }
        return text.toString().strip();
    }

    private static java.util.Optional<String> first(XmlScan scan, String name, int depth) {
        return scan.elements().stream().filter(e -> e.depth() == depth && e.localName().equals(name))
                .map(XmlScan.Element::text).findFirst();
    }

    static void openSample(PluginUi ui) {
        try (InputStream in = EbInterfacePlugin.class.getResourceAsStream("/plugins/ebinterface/invoice-6p1.xml")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ui.editorHost().openGeneratedDocument(text, EditorFileType.XML, "ebinterface-6p1-invoice.xml");
        } catch (IOException | NullPointerException e) {
            DialogHelper.showError("ebInterface", "The sample could not be opened.", String.valueOf(e.getMessage()));
        }
    }
}
