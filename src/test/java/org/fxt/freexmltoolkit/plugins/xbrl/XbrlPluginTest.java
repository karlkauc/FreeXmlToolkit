package org.fxt.freexmltoolkit.plugins.xbrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.fxt.freexmltoolkit.plugin.DocumentValidator.Problem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("XBRL plugin")
class XbrlPluginTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = XbrlPluginTest.class.getResourceAsStream("/plugins/xbrl/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Arelle's JSON log becomes problems: errors and warnings with their line, no information")
    void parseLog() throws Exception {
        List<Problem> problems = ArelleRunner.parseLog(resource("arelle-log.json"), "bad.xbrl");
        assertEquals(1, problems.size(), problems.toString());
        Problem problem = problems.getFirst();
        assertEquals("error", problem.severity());
        assertEquals(11, problem.line());
        assertTrue(problem.message().startsWith("[xbrl.4.6.2:numericUnit] Fact ex:Revenue context c1 is numeric"),
                problem.message());
        assertEquals(List.of(), ArelleRunner.parseLog("{\"log\":[]}", "x.xbrl"));
    }

    @Test
    @DisplayName("describes instances and inline XBRL")
    void describe() throws Exception {
        String text = XbrlPlugin.describe(resource("ok.xbrl"));
        assertTrue(text.startsWith("XBRL instance · 1 fact · 1 context · 1 unit\n"), text);
        String inline = """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:ix="http://www.xbrl.org/2013/inlineXBRL">
                  <body><ix:nonFraction name="ex:Revenue" contextRef="c1" unitRef="eur" decimals="0">1000</ix:nonFraction>
                  <ix:nonNumeric name="ex:Name" contextRef="c1">ACME</ix:nonNumeric></body></html>
                """;
        assertTrue(XbrlPlugin.describe(inline).startsWith("Inline XBRL report · 2 facts"), XbrlPlugin.describe(inline));
        assertNull(XbrlPlugin.describe("<html xmlns='http://www.w3.org/1999/xhtml'/>"));
        assertNull(XbrlPlugin.describe("<order/>"));
    }

    @Test
    @DisplayName("the check needs a saved file and Arelle")
    void checkNeedsFile() {
        assertThrows(IllegalStateException.class, () -> XbrlPlugin.check("<x/>", null, "x"));
        assertTrue(ArelleRunner.findCommand("/no/such/arelleCmdLine").isEmpty());
    }

    @Test
    @DisplayName("with Arelle installed (-Dfxt.test.arelle=…): a missing unit is reported at its line")
    void realArelle(@TempDir Path dir) throws Exception {
        String command = System.getProperty("fxt.test.arelle");
        Assumptions.assumeTrue(command != null && Files.isExecutable(Path.of(command)), "Arelle not available");
        Files.writeString(dir.resolve("tax.xsd"), resource("tax.xsd"));
        Path ok = Files.writeString(dir.resolve("ok.xbrl"), resource("ok.xbrl"));
        Path bad = Files.writeString(dir.resolve("bad.xbrl"), resource("ok.xbrl").replace("unitRef=\"eur\"", "unitRef=\"usd\""));
        assertEquals(List.of(), ArelleRunner.validate(Path.of(command), ok, Duration.ofMinutes(3)));
        List<Problem> problems = ArelleRunner.validate(Path.of(command), bad, Duration.ofMinutes(3));
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("xbrl.4.6.2:numericUnit") && p.line() == 11),
                problems.toString());
    }
}
