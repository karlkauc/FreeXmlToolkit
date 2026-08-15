package org.fxt.freexmltoolkit.service.sqf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.SchematronServiceImpl;
import org.fxt.freexmltoolkit.service.sqf.SqfCorrelator.SqfFinding;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfEditPlan;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfTextEdit;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Golden tests: apply one fix to the person fixture and compare the whole file
 * against the expected {@code person-invalid.fixed-<fixId>.xml}. Full-file equality
 * proves both the fix semantics and that untouched regions stay byte-identical
 * (formatting preservation).
 */
class SqfExecutionEngineTest {

    private static String xml;
    private static SqfCatalog catalog;
    private static List<SqfFinding> findings;

    @BeforeAll
    static void validateFixture() throws Exception {
        File sch = new File("src/test/resources/sqf/person-fixes.sch");
        xml = Files.readString(new File("src/test/resources/sqf/person-invalid.xml").toPath());
        String svrl = new SchematronServiceImpl().validateXmlWithSvrl(xml, sch).svrl();
        catalog = SqfParser.parse(sch);
        findings = SqfCorrelator.correlate(svrl, catalog);
    }

    private static SqfFixSuggestion suggestion(String fixId) {
        return findings.stream()
                .flatMap(f -> f.fixes().stream())
                .filter(f -> fixId.equals(f.fixId()))
                .findFirst().orElseThrow(() -> new AssertionError("no suggestion " + fixId));
    }

    static String apply(String text, SqfEditPlan plan) {
        StringBuilder sb = new StringBuilder(text);
        for (SqfTextEdit edit : plan.edits()) { // sorted descending by start
            sb.replace(edit.start(), edit.end(), edit.replacement());
        }
        return sb.toString();
    }

    private static void assertGolden(String fixId) throws Exception {
        SqfEditPlan plan = SqfExecutionEngine.computeEdit(xml, catalog, suggestion(fixId), Map.of());
        String actual = apply(xml, plan);
        String expected = Files.readString(
                new File("src/test/resources/sqf/person-invalid.fixed-" + fixId + ".xml").toPath());
        assertEquals(expected, actual, "golden mismatch for fix " + fixId);
    }

    @Test
    void addAttributeWithDynamicContent() throws Exception {
        assertGolden("addId"); // sqf:add node-type=attribute, xsl:value-of over sch:let
    }

    @Test
    void deleteElementSwallowsItsLine() throws Exception {
        assertGolden("removeDeprecated"); // sqf:delete
    }

    @Test
    void replaceElementKeepingText() throws Exception {
        assertGolden("renameNameToFullName"); // sqf:replace node-type=element with value-of
    }

    @Test
    void stringReplaceAppliesRegex() throws Exception {
        assertGolden("normalizePhone"); // sqf:stringReplace ^00 → +
    }

    @Test
    void addElementAsLastChild() throws Exception {
        assertGolden("addNote"); // sqf:add node-type=element position=last-child
    }

    @Test
    void addAttributeFromGlobalFix() throws Exception {
        assertGolden("addCompanyMarker"); // global sqf:fixes definition
    }

    @Test
    void staleLocationIsRejected() {
        SqfFixSuggestion stale = suggestion("addId");
        String changed = xml.replace("<person>", "<human>").replace("</person>", "</human>");
        SqfExecutionException ex = assertThrows(SqfExecutionException.class,
                () -> SqfExecutionEngine.computeEdit(changed, catalog, stale, Map.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("valid"),
                "message should point to re-validation: " + ex.getMessage());
    }

    @Test
    void useWhenFalseIsRejected() throws Exception {
        // a fix whose use-when never holds must refuse to run
        java.nio.file.Path dir = Files.createTempDirectory("sqf-usewhen");
        java.nio.file.Path sch = dir.resolve("usewhen.sch");
        Files.writeString(sch, """
                <?xml version="1.0"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
                            xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
                    <sch:pattern>
                        <sch:rule context="a">
                            <sch:assert test="@b" sqf:fix="never">b missing</sch:assert>
                            <sqf:fix id="never" use-when="false()">
                                <sqf:description><sqf:title>Never applicable</sqf:title></sqf:description>
                                <sqf:delete match="."/>
                            </sqf:fix>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>
                """);
        String doc = "<a/>";
        String svrl = new SchematronServiceImpl().validateXmlWithSvrl(doc, sch.toFile()).svrl();
        SqfCatalog cat = SqfParser.parse(sch.toFile());
        SqfFixSuggestion s = SqfCorrelator.correlate(svrl, cat).get(0).fixes().get(0);
        assertThrows(SqfExecutionException.class,
                () -> SqfExecutionEngine.computeEdit(doc, cat, s, Map.of()));
    }

    @Test
    void multiMatchDeleteProducesMultipleEdits() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("sqf-multi");
        java.nio.file.Path sch = dir.resolve("multi.sch");
        Files.writeString(sch, """
                <?xml version="1.0"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
                            xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
                    <sch:pattern>
                        <sch:rule context="list">
                            <sch:report test="obsolete" sqf:fix="dropAll">obsolete entries</sch:report>
                            <sqf:fix id="dropAll">
                                <sqf:description><sqf:title>Drop all obsolete</sqf:title></sqf:description>
                                <sqf:delete match="obsolete"/>
                            </sqf:fix>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>
                """);
        String doc = "<list><obsolete/><keep/><obsolete/></list>";
        String svrl = new SchematronServiceImpl().validateXmlWithSvrl(doc, sch.toFile()).svrl();
        SqfCatalog cat = SqfParser.parse(sch.toFile());
        SqfFixSuggestion s = SqfCorrelator.correlate(svrl, cat).get(0).fixes().get(0);
        SqfEditPlan plan = SqfExecutionEngine.computeEdit(doc, cat, s, Map.of());
        assertEquals(2, plan.edits().size());
        assertEquals("<list><keep/></list>", apply(doc, plan));
    }
}
