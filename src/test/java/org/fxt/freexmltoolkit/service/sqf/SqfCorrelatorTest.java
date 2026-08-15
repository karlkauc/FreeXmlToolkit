package org.fxt.freexmltoolkit.service.sqf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.fxt.freexmltoolkit.service.SchematronService;
import org.fxt.freexmltoolkit.service.SchematronServiceImpl;
import org.fxt.freexmltoolkit.service.sqf.SqfCorrelator.SqfFinding;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Correlates a real ph-schematron SVRL report of the person fixture against the
 * parsed SQF catalog and checks that every finding carries exactly the expected
 * fix suggestions.
 */
class SqfCorrelatorTest {

    private static String svrl;
    private static SqfCatalog catalog;

    @BeforeAll
    static void validateFixture() throws Exception {
        File sch = new File("src/test/resources/sqf/person-fixes.sch");
        String xml = Files.readString(new File("src/test/resources/sqf/person-invalid.xml").toPath());
        SchematronService.SchematronReport report =
                new SchematronServiceImpl().validateXmlWithSvrl(xml, sch);
        svrl = report.svrl();
        assertNotNull(svrl);
        catalog = SqfParser.parse(sch);
    }

    private static SqfFinding findingWithTest(List<SqfFinding> findings, String test) {
        return findings.stream()
                .filter(f -> test.equals(f.test()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no finding with test '" + test + "' in " + findings));
    }

    @Test
    void everyFindingCarriesItsFixes() {
        List<SqfFinding> findings = SqfCorrelator.correlate(svrl, catalog);
        assertEquals(6, findings.size(), "person 1: 4 asserts + 1 report; company: 1 assert");

        // the two "@id" findings are disambiguated by their fired-rule context
        List<SqfFinding> idFindings = findings.stream().filter(f -> "@id".equals(f.test())).toList();
        assertEquals(2, idFindings.size());
        List<String> idFixIds = idFindings.stream()
                .map(f -> f.fixes().get(0).fixId()).sorted().toList();
        assertEquals(List.of("addCompanyMarker", "addId"), idFixIds);

        SqfFinding deprecated = findingWithTest(findings, "deprecated");
        assertTrue(deprecated.report(), "sch:report finding must be flagged as report");
        assertEquals(List.of("removeDeprecated"),
                deprecated.fixes().stream().map(SqfFixSuggestion::fixId).toList());

        SqfFinding note = findingWithTest(findings, "note or fullName");
        assertEquals(List.of("addEmptyNote", "addNote"),
                note.fixes().stream().map(SqfFixSuggestion::fixId).toList(),
                "default fix first");

        for (SqfFinding finding : findings) {
            assertNotNull(finding.location(), "SVRL location must be carried: " + finding);
            for (SqfFixSuggestion fix : finding.fixes()) {
                assertEquals(finding.location(), fix.svrlLocation());
                assertNotNull(fix.title());
            }
        }
    }

    @Test
    void suggestionsCarryCatalogMetadata() {
        List<SqfFinding> findings = SqfCorrelator.correlate(svrl, catalog);
        SqfFixSuggestion addId = findingWithTest(findings, "@id".intern()).fixes().stream()
                .filter(f -> f.fixId().equals("addId") || f.fixId().equals("addCompanyMarker"))
                .findFirst().orElseThrow();
        assertNotNull(addId.fixKey());
        assertNotNull(catalog.fixesByKey().get(addId.fixKey()), "fixKey must resolve in the catalog");
    }

    @Test
    void validDocumentYieldsNoFindings() throws Exception {
        File sch = new File("src/test/resources/sqf/person-fixes.sch");
        String xml = Files.readString(new File("src/test/resources/sqf/person-valid.xml").toPath());
        SchematronService.SchematronReport report =
                new SchematronServiceImpl().validateXmlWithSvrl(xml, sch);
        List<SqfFinding> findings = SqfCorrelator.correlate(report.svrl(), catalog);
        assertTrue(findings.isEmpty(), "valid document: " + findings);
    }
}
