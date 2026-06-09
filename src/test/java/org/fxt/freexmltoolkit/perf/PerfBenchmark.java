package org.fxt.freexmltoolkit.perf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

/** Non-gating perf benchmark: prints median timings for the two hotspots on the FundsXML inputs. */
class PerfBenchmark {

    private static final Path BIG_XSD = Path.of("src/test/resources/FundsXML_428.xsd");
    private static final Path BIG_XML = Path.of("src/test/resources/FundsXML_428.xml");
    private static final int RUNS = 5;

    @Test
    void benchmarkHotspots() throws Exception {
        // Hotspot 1: processXsd
        time("processXsd", () -> {
            XsdDocumentationService svc = new XsdDocumentationService();
            svc.setXsdFilePath(BIG_XSD.toAbsolutePath().toString());
            try {
                svc.processXsd(Boolean.FALSE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Hotspot 2: ContextAnalyzer.analyze at end-of-document (worst case)
        String xml = Files.readString(BIG_XML);
        int caret = xml.length();
        time("analyze@eod", () -> ContextAnalyzer.analyze(xml, caret));
    }

    /**
     * Sustained-typing benchmark: simulates the user editing through the LAST ~200KB of the
     * document, so every {@link ContextAnalyzer#analyze} call carries a large prefix. This is
     * where the #2 optimization (no per-keystroke {@code substring(0, caret)} allocation) pays
     * off: the single-shot wall-clock is flat, but sustained editing shows the GC-pressure win.
     * Non-gating: prints a total and asserts nothing.
     */
    @Test
    void benchmarkSustainedTyping() throws Exception {
        final String xml = Files.readString(BIG_XML);
        final int iters = 2000;
        final int len = xml.length();
        final int windowStart = Math.max(0, len - 200_000); // last ~200KB
        final int span = len - windowStart;

        Runnable loop = () -> {
            for (int i = 0; i < iters; i++) {
                int caret = windowStart + (int) ((long) i * span / iters);
                ContextAnalyzer.analyze(xml, caret);
            }
        };

        loop.run(); // warm-up loop
        List<Long> totals = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            long t0 = System.nanoTime();
            loop.run();
            totals.add((System.nanoTime() - t0) / 1_000_000);
        }
        totals.sort(Long::compareTo);
        long median = totals.get(totals.size() / 2);
        System.out.println("PERF sustainedAnalyze total=" + median + "ms iters=" + iters + " runs=" + totals);
    }

    private static void time(String label, Runnable op) {
        op.run(); // warm-up
        List<Long> ms = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            op.run();
            ms.add((System.nanoTime() - t0) / 1_000_000);
        }
        ms.sort(Long::compareTo);
        long median = ms.get(ms.size() / 2);
        System.out.println("PERF " + label + " median=" + median + "ms runs=" + ms);
    }
}
