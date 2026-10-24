package org.fxt.freexmltoolkit.perf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;

/**
 * Non-gating benchmark of the XSLT debugger's cost (median of a few runs over 10,000 items):
 * a normal run, a debug run without breakpoints, with a conditional breakpoint that never holds
 * and with a logpoint that never pauses. Results go to {@code build/debugger-perf.txt}.
 */
class DebuggerPerfBenchmark {

    private static final int ITEMS = 10_000;
    private static final int RUNS = 15;
    private static final String XSLT = ""
            + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"   // 1
            + "  <xsl:template match=\"/\"><out><xsl:apply-templates select=\"//item\"/></out></xsl:template>\n" // 2
            + "  <xsl:template match=\"item\">\n"                                                         // 3
            + "    <xsl:variable name=\"n\" select=\"xs:integer(@n)\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>\n" // 4
            + "    <xsl:variable name=\"label\" select=\"concat('item-', $n)\"/>\n"                       // 5
            + "    <x n=\"{$n}\"><xsl:value-of select=\"$label\"/></x>\n"                                // 6
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>\n";

    /** Deep call stacks (a 12-level recursion per item) and an accumulator: the expensive case. */
    private static final String DEEP_XSLT = ""
            + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"   // 1
            + "  <xsl:mode use-accumulators=\"#all\"/>\n"                                                // 2
            + "  <xsl:accumulator name=\"seen\" initial-value=\"0\"><xsl:accumulator-rule match=\"item\" select=\"$value + 1\"/></xsl:accumulator>\n" // 3
            + "  <xsl:template match=\"/\"><out><xsl:apply-templates select=\"//item\"/></out></xsl:template>\n" // 4
            + "  <xsl:template match=\"item\"><xsl:call-template name=\"down\"><xsl:with-param name=\"d\" select=\"12\"/></xsl:call-template></xsl:template>\n" // 5
            + "  <xsl:template name=\"down\">\n"                                                          // 6
            + "    <xsl:param name=\"d\"/>\n"                                                             // 7
            + "    <xsl:if test=\"$d gt 0\"><xsl:call-template name=\"down\"><xsl:with-param name=\"d\" select=\"$d - 1\"/></xsl:call-template></xsl:if>\n" // 8
            + "    <x d=\"{$d}\"/>\n"                                                                    // 9
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>\n";

    @Test
    void measure() throws Exception {
        StringBuilder xml = new StringBuilder("<order>");
        for (int i = 1; i <= ITEMS; i++) {
            xml.append("<item n=\"").append(i).append("\"/>");
        }
        xml.append("</order>");
        String input = xml.toString();
        XsltTransformationEngine engine = XsltTransformationEngine.getInstance();
        WatchEvaluator evaluator = new WatchEvaluator(engine.getDebugProcessor(), Map.of());
        List<String> lines = new ArrayList<>();

        lines.add(line("normal run", () -> engine.transform(input, XSLT, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML)));
        lines.add(line("debug run, no breakpoints", () -> debug(engine, input, new DebugSession())));
        lines.add(line("debug run, condition never true", () -> {
            DebugSession s = new DebugSession();
            s.addBreakpoint(new Breakpoint("", 6, true, "$n < 0"));
            s.setConditionEvaluator(evaluator::test);
            return debug(engine, input, s);
        }));
        lines.add(line("debug run, logpoint (no pause)", () -> {
            DebugSession s = new DebugSession();
            s.addBreakpoint(new Breakpoint("", 6, true, "", null, "item {$n}", false));
            s.setLogHandler(evaluator::formatLog, e -> { });
            return debug(engine, input, s);
        }));
        String deepInput = input.replace("</order>", "").substring(0, input.indexOf("<item n=\"1001\"")) + "</order>";
        lines.add(line("deep stacks: debug, no breakpoints", () -> debugDeep(engine, deepInput, new DebugSession())));
        lines.add(line("deep stacks: condition never true", () -> {
            DebugSession s = new DebugSession();
            s.addBreakpoint(new Breakpoint("", 9, true, "$d < 0"));
            s.setConditionEvaluator(evaluator::test);
            return debugDeep(engine, deepInput, s);
        }));
        Files.createDirectories(Path.of("build"));
        Files.write(Path.of("build/debugger-perf.txt"), lines);
        lines.forEach(System.out::println);
    }

    private static XsltTransformationResult debug(XsltTransformationEngine engine, String input, DebugSession session) {
        return engine.transformWithDebugSession(input, XSLT, null, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, session, m -> { });
    }

    private static XsltTransformationResult debugDeep(XsltTransformationEngine engine, String input, DebugSession session) {
        return engine.transformWithDebugSession(input, DEEP_XSLT, null, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, session, m -> { });
    }

    private static String line(String label, Supplier<XsltTransformationResult> run) {
        for (int i = 0; i < 3; i++) {
            run.get(); // warm-up
        }
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            XsltTransformationResult r = run.get();
            times.add((System.nanoTime() - t0) / 1_000_000);
            if (!r.isSuccess()) {
                return label + ": FAILED " + r.getErrorMessage();
            }
        }
        Collections.sort(times);
        return String.format("%-36s %6d ms (median of %d)", label, times.get(RUNS / 2), RUNS);
    }
}
