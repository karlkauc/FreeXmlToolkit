package org.fxt.freexmltoolkit.service.fo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.service.FopEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalFoRendererTest {

    private static final String FO = """
            <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format"><fo:layout-master-set/></fo:root>
            """;

    @TempDir
    Path dir;

    private ExternalFoRenderer.Request request(Path pdf, boolean pdfA) {
        return new ExternalFoRenderer.Request(FO, null, pdf, null, dir.resolve("style sheet.xsl"),
                "My Title", "", "", pdfA);
    }

    @Test
    void rendersAndCollectsTheProgramOutput() throws Exception {
        Path pdf = dir.resolve("out dir/result.pdf");
        FoProcessorConfig config = FakeProcessors.config("Fake", "ok",
                "[-title ${title}] [-author ${author}] [--pdfa ${pdfa}] ${baseDir}", 60);
        List<FopEvent> events = ExternalFoRenderer.render(config, request(pdf, true));

        String written = Files.readString(pdf, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("%PDF"));
        assertTrue(written.contains("\n-title\nMy Title\n"), written);
        assertFalse(written.contains("-author"), "empty author: its group is left out");
        assertTrue(written.contains("\n--pdfa\n"), written);
        assertTrue(written.endsWith(dir.toString()), "base folder = the stylesheet's folder: " + written);
        assertTrue(events.stream().anyMatch(e -> "WARN".equals(e.severity()) && e.message().contains("substituted")));
        assertTrue(events.stream().allMatch(e -> "Fake".equals(e.id())));
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".fo")), "the temporary FO is removed");
        }
    }

    @Test
    void nonZeroExitFailsWithTheErrorLines() {
        Path pdf = dir.resolve("x.pdf");
        FoProcessorException e = assertThrows(FoProcessorException.class,
                () -> ExternalFoRenderer.render(FakeProcessors.config("Fake", "fail", null, 60), request(pdf, false)));
        assertTrue(e.getMessage().contains("exit code 3"), e.getMessage());
        assertTrue(e.getMessage().contains("Unknown property"), e.getMessage());
        assertEquals("ERROR", e.events().get(0).severity());
    }

    @Test
    void anOldOutputDoesNotPassForANewOne() throws Exception {
        Path pdf = dir.resolve("old.pdf");
        Files.writeString(pdf, "%PDF old");
        FoProcessorException e = assertThrows(FoProcessorException.class,
                () -> ExternalFoRenderer.render(FakeProcessors.config("Fake", "nooutput", null, 60), request(pdf, false)));
        assertTrue(e.getMessage().contains("without writing"), e.getMessage());
        assertFalse(Files.exists(pdf));
    }

    @Test
    void aHangingProgramIsStopped() {
        long t0 = System.nanoTime();
        FoProcessorException e = assertThrows(FoProcessorException.class,
                () -> ExternalFoRenderer.render(FakeProcessors.config("Fake", "hang", null, 2), request(dir.resolve("h.pdf"), false)));
        assertTrue(e.getMessage().contains("did not finish within 2 seconds"), e.getMessage());
        assertTrue((System.nanoTime() - t0) / 1_000_000_000L < 30);
    }

    @Test
    void aMissingProgramIsExplained() {
        FoProcessorConfig config = new FoProcessorConfig(null, "Ghost", FoProcessorPreset.XEP,
                dir.resolve("no/such/xep").toString(), null, "", 0);
        FoProcessorException e = assertThrows(FoProcessorException.class,
                () -> ExternalFoRenderer.render(config, request(dir.resolve("g.pdf"), false)));
        assertTrue(e.getMessage().contains("program not found"), e.getMessage());
    }

    @Test
    void anFoFileIsRenderedInPlace() throws Exception {
        Path fo = dir.resolve("doc.fo");
        Files.writeString(fo, FO);
        Path pdf = dir.resolve("doc.pdf");
        ExternalFoRenderer.render(FakeProcessors.config("Fake", "ok", null, 60),
                new ExternalFoRenderer.Request(null, fo, pdf, null, null, "", "", "", false));
        assertTrue(Files.readString(pdf).contains(fo.toString()));
        assertTrue(Files.exists(fo), "the user's FO file is kept");
    }
}
