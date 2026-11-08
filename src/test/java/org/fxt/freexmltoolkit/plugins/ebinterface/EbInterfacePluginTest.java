package org.fxt.freexmltoolkit.plugins.ebinterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.fxt.freexmltoolkit.plugin.DocumentValidator.Problem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ebInterface plugin")
class EbInterfacePluginTest {

    private final EbInterfaceValidator validator = new EbInterfaceValidator();

    private static String sample() throws IOException {
        try (InputStream in = EbInterfacePluginTest.class.getResourceAsStream("/plugins/ebinterface/invoice-6p1.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> messages(String xml) {
        return validator.validate(xml, null).stream().map(Problem::message).toList();
    }

    @Test
    @DisplayName("the sample invoice passes; other documents are not checked")
    void samplePasses() throws Exception {
        String xml = sample();
        assertTrue(validator.accepts(xml, null));
        assertEquals(List.of(), validator.validate(xml, null));
        assertFalse(validator.accepts("<Invoice xmlns='urn:oasis:names:specification:ubl:schema:xsd:Invoice-2'/>", null));
        assertEquals("6p1", EbInterfaceValidator.versionOf(xml).orElseThrow());
    }

    @Test
    @DisplayName("UID, IBAN, line, tax, totals, payable amount and due date")
    void checks() throws Exception {
        String xml = sample();
        assertEquals(List.of("Austrian VAT id ATU13585628 has a wrong check digit."),
                messages(xml.replace("ATU13585627", "ATU13585628")));
        assertTrue(messages(xml.replace("AT611904300234573201", "AT611904300234573202")).getFirst().contains("check digits"));

        List<String> line = messages(xml.replace("<UnitPrice>25.00</UnitPrice>", "<UnitPrice>26.00</UnitPrice>"));
        assertEquals(List.of("LineItemAmount 100.00 is not quantity × unit price (4.00 × 26.00 = 104.00)."), line);

        List<String> tax = messages(xml.replaceFirst("<TaxAmount>20.00</TaxAmount>", "<TaxAmount>21.00</TaxAmount>"));
        assertEquals("TaxAmount 21.00 is not 20 % of 100.00 (20.00).", tax.getFirst());

        List<String> summary = messages(xml.replace("<TaxableAmount>25.00</TaxableAmount>\n      <TaxPercent TaxCategoryCode=\"AA\">10</TaxPercent>\n      <TaxAmount>2.50</TaxAmount>\n    </TaxItem>\n  </Tax>",
                "<TaxableAmount>30.00</TaxableAmount>\n      <TaxPercent TaxCategoryCode=\"AA\">10</TaxPercent>\n      <TaxAmount>3.00</TaxAmount>\n    </TaxItem>\n  </Tax>"));
        assertTrue(summary.contains("The tax summary's taxable amounts add up to 130.00, the lines to 125.00."), summary.toString());
        assertTrue(summary.contains("TotalGrossAmount 147.50 is not taxable amounts plus taxes of the tax summary (153.00)."), summary.toString());

        assertEquals(List.of("PayableAmount 140.00 is not TotalGrossAmount − PrepaidAmount + RoundingAmount (147.50)."),
                messages(xml.replace("<PayableAmount>147.50</PayableAmount>", "<PayableAmount>140.00</PayableAmount>")));
        assertEquals(List.of(), messages(xml.replace("<PayableAmount>147.50</PayableAmount>",
                "<PrepaidAmount>7.50</PrepaidAmount>\n  <PayableAmount>140.00</PayableAmount>")), "prepaid counts");
        assertEquals(List.of("The due date 2026-11-01 is before the invoice date 2026-11-08."),
                messages(xml.replace("2026-11-22", "2026-11-01")));
    }

    @Test
    @DisplayName("describes the invoice")
    void describe() throws Exception {
        assertEquals("ebInterface 6.1 · Invoice 2026-0042 of 2026-11-08\nBiller: Beispiel Handels GmbH · 2 lines · payable 147.50 EUR",
                EbInterfacePlugin.describe(sample()));
        assertNull(EbInterfacePlugin.describe("<Invoice/>"));
        assertEquals("https://raw.githubusercontent.com/austriapro/ebinterface-standards/master/schemas/ebInterface6p1/Invoice.xsd",
                EbInterfacePlugin.schemaUrl("6p1"));
    }
}
