package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.sf.saxon.s9api.QName;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;

/** The debugger on an XQuery: breakpoints, variables, functions, trace(), errors, stop. */
class XQueryDebuggerIntegrationTest {

    private static final String XML = "<order><item n=\"1\"/><item n=\"2\"/></order>";
    private static final String QUERY = ""
            + "declare function local:double($x) {\n"          // 1
            + "  $x * 2\n"                                      // 2
            + "};\n"                                            // 3
            + "let $total := count(//item)\n"                  // 4
            + "for $i in //item\n"                              // 5
            + "let $n := xs:integer($i/@n)\n"                  // 6
            + "return\n"                                        // 7
            + "  <r t=\"{$total}\">{ local:double($n), trace($n, 'n') }</r>\n"; // 8
    private static final String SYSTEM_ID = "file:/tmp/debug-test.xq";

    private static XsltTransformationResult run(String query, DebugSession session, List<String> messages) {
        return XsltTransformationEngine.getInstance().transformXQueryWithDebugSession(XML, null, query, SYSTEM_ID,
                new HashMap<>(), XsltTransformationEngine.OutputFormat.XML, session,
                m -> messages.add(m.getLevel() + " " + m.getMessage()));
    }

    @Test
    void pausesAtBreakpointsWithTheQueryVariablesAndRunsToTheEnd() {
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint(SYSTEM_ID, 8, true));
        session.addBreakpoint(new Breakpoint(SYSTEM_ID, 1, true)); // the function
        List<String> pauses = new CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                PausedSnapshot snap = session.getPausedSnapshot();
                pauses.add(snap.lineNumber() + " " + snap.localVariables().keySet().stream()
                        .map(QName::getLocalName).filter(n -> !n.startsWith("v")).sorted().toList());
                session.requestContinue();
            }
        });
        List<String> messages = new CopyOnWriteArrayList<>();
        XsltTransformationResult result = run(QUERY, session, messages);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(result.getOutputContent().contains("<r t=\"2\">4"), result.getOutputContent());
        assertTrue(pauses.contains("8 [i, n, total]"), "the return line, with the FLWOR variables: " + pauses);
        assertTrue(pauses.stream().anyMatch(p -> p.startsWith("1 [x]")), "inside the function: " + pauses);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("INFO") && m.contains("n")), "trace() output: " + messages);
    }

    @Test
    void pauseOnEntryStepsThroughTheQuery() {
        DebugSession session = new DebugSession();
        session.startSession(DebugSession.StepMode.STEP_INTO);
        List<Integer> lines = new CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                lines.add(session.getPausedSnapshot().lineNumber());
                session.requestStepInto();
            }
        });
        assertTrue(run(QUERY, session, new CopyOnWriteArrayList<>()).isSuccess());
        assertTrue(lines.size() >= 3 && lines.contains(4) && lines.contains(8), "stepped through: " + lines);
    }

    @Test
    void staticAndDynamicErrorsAreReported() {
        XsltTransformationResult broken = run("let $x := (1 + \nreturn $x", new DebugSession(), new CopyOnWriteArrayList<>());
        assertFalse(broken.isSuccess());
        assertTrue(broken.getErrorMessage().startsWith("XQuery compilation failed"), broken.getErrorMessage());

        DebugSession session = new DebugSession();
        session.setBreakOnErrors(true);
        List<String> notes = new CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                notes.add(session.getPauseNote());
                session.requestContinue();
            }
        });
        XsltTransformationResult failed = run("for $i in //item\nreturn <x>{ 10 idiv (xs:integer($i/@n) - 1) }</x>",
                session, new CopyOnWriteArrayList<>());
        assertFalse(failed.isSuccess());
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("error FOAR0001")), "break on errors: " + notes);
    }

    @Test
    void stopEndsTheRun() {
        DebugSession session = new DebugSession();
        session.startSession(DebugSession.StepMode.STEP_INTO);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                session.requestStop();
            }
        });
        XsltTransformationResult result = run(QUERY, session, new CopyOnWriteArrayList<>());
        assertFalse(result.isSuccess());
        assertEquals("Debug session stopped", result.getErrorMessage());
    }

    @Test
    void queryPrefixesAndFunctionsAreKnown() {
        String query = "xquery version \"3.1\";\ndeclare namespace f = \"urn:f\";\n"
                + "declare %private function f:total($x) { $x };\ndeclare function local:g() { 1 };\nf:total(1)";
        assertEquals("urn:f", WatchEvaluator.namespacesOf(query).get("f"));
        assertEquals("http://www.w3.org/2005/xquery-local-functions", WatchEvaluator.namespacesOf(query).get("local"));
        assertEquals(List.of("f:total", "local:g"),
                StylesheetSymbols.scan(query).get(SymbolBreakpoint.Kind.FUNCTION));
    }
}
