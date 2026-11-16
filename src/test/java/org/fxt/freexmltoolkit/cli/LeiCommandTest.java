package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fxt-cli lei} and {@code validate --gleif} on a saved GLEIF answer (no network). */
class LeiCommandTest {

    private static final String GLEIF_LEI = "506700GE1G29325QX363";
    private static final String UNKNOWN = "5299009FXTMMFCP00069";

    @TempDir
    Path dir;
    private String out;
    private String err;

    @BeforeEach
    void fakeGleif() {
        Gleif.setCache(new LeiCache(new GleifClient(uri -> {
            try (InputStream in = LeiCommandTest.class.getResourceAsStream("/gleif/lei-records.json")) {
                return new GleifClient.Response(200, in.readAllBytes());
            }
        }, Clock.systemUTC()), null, Clock.systemUTC(), Duration.ofHours(1)));
    }

    @AfterEach
    void realGleif() {
        Gleif.setCache(null);
    }

    private int run(String... args) {
        StringWriter o = new StringWriter();
        StringWriter e = new StringWriter();
        int code = FxtCli.newCommandLine(new PrintWriter(o, true), new PrintWriter(e, true)).execute(args);
        out = o.toString();
        err = e.toString();
        return code;
    }

    @Test
    void looksLeisUp() {
        assertEquals(ExitCodes.OK, run("lei", GLEIF_LEI), err);
        assertTrue(out.contains("Global Legal Entity Identifier Foundation (CH) - ISSUED"), out);

        assertEquals(ExitCodes.FAILED, run("lei", GLEIF_LEI, UNKNOWN, "529900XXXXXXXXXXXX00"));
        assertTrue(out.contains(UNKNOWN + ": not registered at GLEIF"), out);
        assertTrue(out.contains("wrong check digits"), out);

        assertEquals(ExitCodes.FAILED, run("lei", UNKNOWN, "-f", "json"));
        assertTrue(out.contains("\"found\": false"), out);
        assertEquals(ExitCodes.USAGE, run("lei"));
    }

    @Test
    void findsLeisInFilesAndChecksThemDuringValidation() throws Exception {
        Path xml = dir.resolve("report.xml");
        Files.writeString(xml, "<r>\n  <LEI>" + GLEIF_LEI + "</LEI>\n  <LEI>" + UNKNOWN + "</LEI>\n</r>\n", StandardCharsets.UTF_8);
        assertEquals(ExitCodes.FAILED, run("lei", "--file", xml.toString()));
        assertTrue(out.contains(GLEIF_LEI) && out.contains(UNKNOWN), out);

        assertEquals(ExitCodes.OK, run("validate", xml.toString()), out + err);
        assertFalse(out.contains("GLEIF"), "without --gleif no lookup: " + out);
        assertEquals(ExitCodes.FAILED, run("validate", xml.toString(), "--gleif"));
        assertTrue(out.contains("not registered at GLEIF"), out);
        assertTrue(out.contains(":3"), "line of the finding: " + out);
    }
}
