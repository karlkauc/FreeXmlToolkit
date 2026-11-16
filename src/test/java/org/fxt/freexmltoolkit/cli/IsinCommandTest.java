package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.finance.OnlineFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fxt-cli isin} and {@code validate --isin} on saved answers (no network). */
class IsinCommandTest {

    @TempDir
    Path dir;
    private String out;
    private String err;

    @BeforeEach
    void fakes() {
        OnlineFakes.install();
    }

    @AfterEach
    void real() {
        OnlineFakes.reset();
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
    void looksIsinsUp() {
        assertEquals(ExitCodes.OK, run("isin", "IE00B4L5Y983"), err);
        assertTrue(out.contains("iShares Core MSCI World UCITS ETF - CFI CEOGES"), out);
        assertTrue(out.contains("traded on 51 of 87 EU venues"), out);

        assertEquals(ExitCodes.FAILED, run("isin", "AT000FXTST04", "DE0000000001"));
        assertTrue(out.contains("unknown to ESMA FIRDS and OpenFIGI"), out);
        assertTrue(out.contains("wrong check digit"), out);

        assertEquals(ExitCodes.OK, run("isin", "AT000FXTST04", "--no-openfigi"), "without OpenFIGI only a hint");
        assertTrue(out.contains("not in ESMA FIRDS"), out);
        assertEquals(ExitCodes.OK, run("isin", "US0378331005", "-f", "json"));
        assertTrue(out.contains("\"issuerLei\": \"HWUPKR0MPOU8FGXBT394\""), out);
        assertEquals(ExitCodes.USAGE, run("isin"));
    }

    @Test
    void validateChecksLeisAndIsinsOnRequest() throws Exception {
        Path xml = dir.resolve("holdings.xml");
        Files.writeString(xml, "<r>\n  <LEI>5299009FXTMMFCP00069</LEI>\n  <ISIN>AT000FXTST04</ISIN>\n  <ISIN>IE00B4L5Y983</ISIN>\n</r>\n",
                StandardCharsets.UTF_8);
        assertEquals(ExitCodes.OK, run("validate", xml.toString()), out + err);
        assertFalse(out.contains("ISIN"), out);
        assertEquals(ExitCodes.OK, run("validate", xml.toString(), "--isin"), "an unknown ISIN is a warning: " + out);
        assertTrue(out.contains("known neither to ESMA FIRDS nor to OpenFIGI"), out);
        assertEquals(ExitCodes.FAILED, run("validate", xml.toString(), "--isin", "--fail-on-warning"));
        assertEquals(ExitCodes.FAILED, run("validate", xml.toString(), "--isin", "--gleif"));
        assertTrue(out.contains("not registered at GLEIF") && out.contains(":3"), out);
    }
}
