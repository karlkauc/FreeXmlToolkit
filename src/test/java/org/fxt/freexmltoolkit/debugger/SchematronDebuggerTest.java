package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


import org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController;
import org.fxt.freexmltoolkit.service.SchematronDebugCompiler;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Debugging a Schematron through the XSLT SchXslt compiles from it, with lines mapped back to the .sch. */
class SchematronDebuggerTest {

    private static final String SCH = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"                                              // 1
            + "<sch:schema xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\" queryBinding=\"xslt2\"\n"   // 2
            + "            xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:f=\"urn:f\">\n"        // 3
            + "  <sch:ns prefix=\"f\" uri=\"urn:f\"/>\n"                                                   // 4
            + "  <sch:let name=\"max\" value=\"10\"/>\n"                                                   // 5
            + "  <xsl:function name=\"f:twice\">\n"                                                        // 6
            + "    <xsl:param name=\"x\"/>\n"                                                              // 7
            + "    <xsl:sequence select=\"$x * 2\"/>\n"                                                    // 8
            + "  </xsl:function>\n"                                                                       // 9
            + "  <sch:pattern id=\"p1\">\n"                                                                // 10
            + "    <sch:rule context=\"item\">\n"                                                          // 11
            + "      <sch:let name=\"n\" value=\"xs:integer(@n)\"/>\n"                                    // 12
            + "      <sch:assert test=\"@n\" id=\"a1\">An item needs @n</sch:assert>\n"                     // 13
            + "      <sch:assert test=\"f:twice($n) le $max\">Item <sch:value-of select=\"$n\"/> is too big</sch:assert>\n" // 14
            + "      <sch:report test=\"$n = 2\">Item two</sch:report>\n"                                  // 15
            + "    </sch:rule>\n"                                                                         // 16
            + "    <sch:rule abstract=\"true\" id=\"named\">\n"                                            // 17
            + "      <sch:assert test=\"@name\">Needs a name</sch:assert>\n"                               // 18
            + "    </sch:rule>\n"                                                                         // 19
            + "    <sch:rule context=\"customer\">\n"                                                      // 20
            + "      <sch:extends rule=\"named\"/>\n"                                                      // 21
            + "    </sch:rule>\n"                                                                         // 22
            + "  </sch:pattern>\n"                                                                        // 23
            + "  <sch:include href=\"more.sch\"/>\n"                                                       // 24
            + "</sch:schema>\n";
    private static final String MORE = ""
            + "<sch:pattern xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\" id=\"p2\">\n"              // 1
            + "  <sch:rule context=\"order\">\n"                                                           // 2
            + "    <sch:assert test=\"count(item) ge 1\">An order needs items</sch:assert>\n"              // 3
            + "  </sch:rule>\n"                                                                           // 4
            + "</sch:pattern>\n";
    private static final String XML = "<order><item n=\"2\"/><item n=\"7\"/><item/><customer/></order>";

    @TempDir
    static Path dir;
    private static File sch;
    private static String schUri;
    private static String moreUri;
    private static SchematronDebugCompiler.Compiled compiled;

    @BeforeAll
    static void compile() throws Exception {
        sch = dir.resolve("rules.sch").toFile();
        Files.writeString(sch.toPath(), SCH);
        Files.writeString(dir.resolve("more.sch"), MORE);
        schUri = sch.toURI().toString();
        moreUri = dir.resolve("more.sch").toUri().toString();
        compiled = SchematronDebugCompiler.compile(sch);
    }

    @Test
    void everyRuleCheckLetAndFunctionLineIsMappedToTheSchematron() {
        SchematronSourceMap map = compiled.sourceMap();
        List<String> found = map.items().stream()
                .filter(i -> map.mappedLines(i) > 0)
                .map(i -> i.kind() + " " + new File(java.net.URI.create(i.systemId())).getName() + ":" + i.line())
                .toList();
        assertTrue(found.containsAll(List.of("FUNCTION rules.sch:6", "FUNCTION rules.sch:7", "FUNCTION rules.sch:8",
                "LET rules.sch:5", "RULE rules.sch:11", "LET rules.sch:12", "ASSERT rules.sch:13", "ASSERT rules.sch:14",
                "REPORT rules.sch:15", "ASSERT rules.sch:18", "RULE rules.sch:20",
                "RULE more.sch:2", "ASSERT more.sch:3")), "mapped: " + found);
        // SchXslt's own driver code has no Schematron counterpart
        String[] lines = compiled.xslt().split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("match=\"root()\"")) {
                assertEquals(-1, map.map(schUri, i + 1).line(), "the driver template is unmapped");
            }
        }
        // other files pass through
        assertEquals(new SourceMapping.Mapped("file:/x.xsl", 3), map.map("file:/x.xsl", 3));
    }

    @Test
    void aBreakpointOnAnAssertPausesPerContextNodeWithTheLets() {
        DebugSession session = new DebugSession();
        session.setSourceMapping(compiled.sourceMap());
        session.addBreakpoint(new Breakpoint(schUri, 14, true));
        session.addBreakpoint(new Breakpoint(moreUri, 3, true)); // in the included file
        List<String> pauses = new CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                PausedSnapshot snap = session.getPausedSnapshot();
                String n = snap.localVariables().entrySet().stream()
                        .filter(e -> e.getKey().getLocalName().equals("n"))
                        .map(e -> e.getValue().toString()).findFirst().orElse("-");
                pauses.add(SourceLocations.fileName(snap.systemId()) + ":" + snap.lineNumber() + " n=" + n);
                session.requestContinue();
            }
        });
        XsltTransformationResult result = run(session);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(List.of("more.sch:3 n=-", "rules.sch:14 n=2", "rules.sch:14 n=7", "rules.sch:14 n="), pauses);
        assertTrue(result.getOutputContent().contains("failed-assert"), result.getOutputContent());
    }

    @Test
    void steppingStopsOnlyAtSchematronLines() {
        DebugSession session = new DebugSession();
        session.setSourceMapping(compiled.sourceMap());
        session.startSession(DebugSession.StepMode.STEP_INTO);
        List<String> stops = new CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                PausedSnapshot snap = session.getPausedSnapshot();
                stops.add(SourceLocations.fileName(snap.systemId()) + ":" + snap.lineNumber());
                session.requestStepInto();
            }
        });
        assertTrue(run(session).isSuccess());
        assertFalse(stops.isEmpty());
        List<String> allowed = List.of("rules.sch:5", "rules.sch:6", "rules.sch:7", "rules.sch:8", "rules.sch:11",
                "rules.sch:12", "rules.sch:13", "rules.sch:14", "rules.sch:15", "rules.sch:18", "rules.sch:20",
                "more.sch:2", "more.sch:3");
        assertTrue(allowed.containsAll(stops), "stops: " + stops);
        assertTrue(stops.contains("rules.sch:11") && stops.contains("rules.sch:13") && stops.contains("rules.sch:8"),
                "rule, assert and the function body: " + stops);
    }

    @Test
    void theControllerReportsTheFindingsAtTheirSchematronLines() throws Exception {
        XsltDebugController controller = new XsltDebugController(Runnable::run);
        controller.setLanguage(XsltDebugController.Language.SCHEMATRON);
        List<XsltTransformationResult.TransformationMessage> messages = new CopyOnWriteArrayList<>();
        controller.addMessageListener(messages::add);
        controller.start(XML, null, () -> SCH, schUri, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML);
        assertTrue(controller.getLastResult().isSuccess(), controller.getLastResult().getErrorMessage());
        List<String> rows = messages.stream().map(m -> m.getLevel() + " " + m.getLineNumber() + " " + m.getMessage()).toList();
        assertTrue(rows.contains("WARNING 14 Failed assert: Item 7 is too big - /order[1]/item[2]"), "rows: " + rows);
        assertTrue(rows.contains("WARNING 13 Failed assert: An item needs @n - /order[1]/item[3]"), "rows: " + rows);
        assertTrue(rows.contains("WARNING 18 Failed assert: Needs a name - /order[1]/customer[1]"), "rows: " + rows);
        assertTrue(rows.contains("INFO 15 Report: Item two - /order[1]/item[1]"), "rows: " + rows);
        assertEquals("rules.sch:14", messages.stream().filter(m -> m.getLineNumber() == 14).findFirst().orElseThrow()
                .getLocation());
        // the session's own source text is the .sch (breakpoints in multi-line tags map on it)
        assertEquals(SCH, controller.getSession().sourceText(SourceLocations.normalize(schUri)));
    }

    @Test
    void aSchematronThatDoesNotCompileFailsTheRunWithAMessage() throws Exception {
        Path broken = dir.resolve("broken.sch");
        Files.writeString(broken, "<sch:schema xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\"><sch:pattern>");
        XsltDebugController controller = new XsltDebugController(Runnable::run);
        controller.setLanguage(XsltDebugController.Language.SCHEMATRON);
        controller.start(XML, null, () -> "", broken.toUri().toString(), new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML);
        assertFalse(controller.getLastResult().isSuccess());
        assertNotNull(controller.getLastResult().getErrorMessage());
        assertFalse(controller.isRunning());
    }

    private static XsltTransformationResult run(DebugSession session) {
        return XsltTransformationEngine.getInstance().transformWithDebugSession(XML, null, compiled.xslt(),
                compiled.systemId(), new HashMap<>(), XsltTransformationEngine.OutputFormat.XML, session, m -> { });
    }

}
