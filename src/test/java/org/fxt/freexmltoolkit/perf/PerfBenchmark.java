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
