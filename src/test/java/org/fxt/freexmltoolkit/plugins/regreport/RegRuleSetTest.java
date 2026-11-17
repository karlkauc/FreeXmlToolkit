package org.fxt.freexmltoolkit.plugins.regreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.ElementDescriber;
import org.junit.jupiter.api.Test;

/** The shared reporting core: records, rules with lines, the validator and the field descriptions. */
class RegRuleSetTest {

    private static final String XML = """
            <?xml version="1.0"?>
            <Rpt Member="AT" Version="1.2">
              <Rec>
                <Kind>A</Kind>
                <Amount>100</Amount>
                <Parts><Part>60</Part><Part>30</Part></Parts>
              </Rec>
              <Other>ignored</Other>
              <Rec>
                <Kind>B</Kind>
                <Amount>x</Amount>
              </Rec>
            </Rpt>
            """;

    @Test
    void recordsAreReadWithTheirLines() throws Exception {
        List<RecordNode> records = new ArrayList<>();
        RecordNode[] header = new RecordNode[1];
        RecordReader.read(XML, Set.of("Rpt/Rec"), h -> header[0] = h, records::add);
        assertEquals("Rpt", header[0].name());
        assertEquals(Map.of("Member", "AT", "Version", "1.2"), header[0].attributes());
        assertEquals(2, records.size());
        RecordNode first = records.getFirst();
        assertEquals(3, first.line());
        assertEquals("A", first.text("Kind"));
        assertEquals(new BigDecimal("100"), first.decimal("Amount"));
        assertEquals(2, first.all("Parts/Part").size());
        assertEquals(6, first.lineOf("Parts/Part"));
        assertEquals("Rec/Parts/Part", first.first("Parts/Part").path());
        assertTrue(first.filled("Parts"));
        assertFalse(first.has("Missing"));
        assertNull(records.get(1).decimal("Amount"), "not a number");
        assertEquals(2, first.all("*").size() - 1, "Kind, Amount, Parts");
    }

    private static RegRuleSet rules() {
        return new RegRuleSet("Rpt/Rec")
                .header((root, ctx, out) -> {
                    if (!"DE".equals(root.attributes().get("Member"))) {
                        out.warning("H-1", root.line(), "Member is " + root.attributes().get("Member"));
                    }
                })
                .record((rec, ctx, out) -> {
                    BigDecimal amount = rec.decimal("Amount");
                    if (amount == null) {
                        out.error("R-1", rec.lineOf("Amount"), "Amount is no number");
                        return;
                    }
                    BigDecimal parts = rec.all("Parts/Part").stream().map(p -> new BigDecimal(p.text()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (parts.compareTo(amount) != 0) {
                        out.error("R-2", rec.lineOf("Parts"), "Parts " + parts + " ≠ amount " + amount);
                    }
                });
    }

    @Test
    void rulesFindProblemsOnTheirLinesInDocumentOrder() throws Exception {
        List<RegRuleSet.Finding> findings = rules().run(XML, "x.xml");
        assertEquals(List.of("H-1", "R-2", "R-1"), findings.stream().map(RegRuleSet.Finding::ruleId).toList());
        assertEquals(List.of(2, 6, 11), findings.stream().map(RegRuleSet.Finding::line).toList());
        assertThrows(Exception.class, () -> rules().run("<Rpt><Rec>", "x.xml"));
    }

    @Test
    void validatorChoosesTheRulesByTheRootElement() {
        RegValidator validator = new RegValidator("Test", () -> true,
                root -> root.localName().equals("Rpt") ? rules() : null);
        assertTrue(validator.accepts(XML, "x.xml"));
        assertFalse(validator.accepts("<Other/>", "x.xml"));
        assertFalse(new RegValidator("Test", () -> false, root -> rules()).accepts(XML, "x.xml"));
        List<DocumentValidator.Problem> problems = validator.validate(XML, "x.xml");
        assertEquals(3, problems.size());
        assertEquals("warning", problems.getFirst().severity());
        assertEquals("H-1 Member is AT", problems.getFirst().message());
        assertEquals("urn:x", RegValidator.rootOf("<a:Doc xmlns:a='urn:x'/>").namespace());
        assertNull(RegValidator.rootOf("not xml"));
    }

    @Test
    void catalogDescribesFieldsByTheirPath() {
        FieldCatalog catalog = FieldCatalog.load("/regreport/test-catalog.json");
        assertEquals("Test format", catalog.format());
        assertEquals("Field 2 · Amount", catalog.label("2"));
        assertEquals("Field 9", catalog.label("9"));
        CatalogDescriber describer = new CatalogDescriber(() -> true, List.of(() -> catalog));
        String text = describer.describe(new ElementDescriber.ElementInfo(List.of("Rpt", "Rec", "Amount"),
                Map.of(), Map.of(), Map.of(), "100", List.of()));
        assertTrue(text.startsWith("Field 2 · Amount\nM/C/O/F: M\nRule: Must equal the sum of the parts"), text);
        assertNull(describer.describe(new ElementDescriber.ElementInfo(List.of("Rpt", "Other"),
                Map.of(), Map.of(), Map.of(), "", List.of())));
        assertNull(new CatalogDescriber(() -> false, List.of(() -> catalog)).describe(
                new ElementDescriber.ElementInfo(List.of("Rpt", "Rec", "Amount"), Map.of(), Map.of(), Map.of(), "", List.of())));
    }

    @Test
    void longRunsReportTheRecordsAndStopWhenCancelled() throws Exception {
        StringBuilder xml = new StringBuilder("<Rpt>\n");
        for (int i = 0; i < 1000; i++) {
            xml.append("<Rec><Id>").append(i).append("</Id></Rec>\n");
        }
        xml.append("</Rpt>");
        java.util.List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
        long[] steps = new long[2];
        boolean[] cancel = {false};
        boolean[] armed = {true}; // the first run is cancelled at record 400, the second runs through
        org.fxt.freexmltoolkit.service.progress.Progress progress = new org.fxt.freexmltoolkit.service.progress.Progress() {
            @Override
            public void message(String text) {
                messages.add(text);
                if (armed[0] && text.contains("record 400")) {
                    cancel[0] = true;
                }
            }

            @Override
            public void progress(long done, long total) {
                steps[0] = done;
                steps[1] = total;
            }

            @Override
            public boolean isCancelled() {
                return cancel[0];
            }
        };
        assertThrows(org.fxt.freexmltoolkit.service.progress.CancelledException.class,
                () -> new RegRuleSet("Rpt/Rec").run(xml.toString(), "x.xml", progress));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Rules: record 200 · line")), messages.toString());
        assertEquals(1002, steps[1], "progress by line of the file");
        assertTrue(messages.stream().noneMatch(m -> m.contains("record 600")), "stopped after Cancel");

        cancel[0] = false;
        armed[0] = false;
        messages.clear();
        assertEquals(java.util.List.of(), new RegRuleSet("Rpt/Rec").run(xml.toString(), "x.xml", progress));
        assertTrue(messages.getLast().startsWith("Rules across the whole file (1000 records)"), messages.toString());
    }
}
