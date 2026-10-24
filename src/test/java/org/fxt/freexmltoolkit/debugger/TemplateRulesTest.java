package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Template rules, priorities and import precedence for "Explain Match". */
class TemplateRulesTest {

    @Test
    void defaultPrioritiesFollowTheSpec() {
        assertEquals(0, TemplateRules.defaultPriority("item"));
        assertEquals(0, TemplateRules.defaultPriority("@id"));
        assertEquals(0, TemplateRules.defaultPriority("f:item"));
        assertEquals(-0.25, TemplateRules.defaultPriority("f:*"));
        assertEquals(-0.25, TemplateRules.defaultPriority("*:item"));
        assertEquals(-0.5, TemplateRules.defaultPriority("*"));
        assertEquals(-0.5, TemplateRules.defaultPriority("node()"));
        assertEquals(-0.5, TemplateRules.defaultPriority("text()"));
        assertEquals(-0.5, TemplateRules.defaultPriority("/"));
        assertEquals(0.5, TemplateRules.defaultPriority("order/item"));
        assertEquals(0.5, TemplateRules.defaultPriority("item[@n = '2']"));
    }

    @Test
    void unionPatternsSplitAtTheTopLevelOnly() {
        assertEquals(List.of("a", "b[x | y]", "c"), TemplateRules.alternatives("a | b[x | y] union c"));
        assertEquals(List.of("item[@t = 'a|b']"), TemplateRules.alternatives("item[@t = 'a|b']"));
    }

    @Test
    void importsHaveLowerPrecedenceAndIncludesShareTheirs() {
        Map<String, String> texts = Map.of(
                "/s/main.xsl", "<xsl:stylesheet xmlns:xsl='x'>\n<xsl:import href='lib/base.xsl'/>\n"
                        + "<xsl:include href='part.xsl'/>\n<xsl:template match='item'/>\n</xsl:stylesheet>",
                "/s/part.xsl", "<xsl:stylesheet xmlns:xsl='x'>\n<xsl:template match='item' priority='-1'/>\n</xsl:stylesheet>",
                "/s/lib/base.xsl", "<xsl:stylesheet xmlns:xsl='x'>\n<xsl:template match='item' mode='m #default'/>\n"
                        + "<xsl:template name='named'/>\n</xsl:stylesheet>");
        List<TemplateRules.Rule> rules = TemplateRules.collect("/s/main.xsl", texts::get);
        assertEquals(3, rules.size(), "named templates are not rules");
        TemplateRules.Rule base = rules.stream().filter(r -> r.path().endsWith("base.xsl")).findFirst().orElseThrow();
        TemplateRules.Rule main = rules.stream().filter(r -> r.path().endsWith("main.xsl")).findFirst().orElseThrow();
        TemplateRules.Rule part = rules.stream().filter(r -> r.path().endsWith("part.xsl")).findFirst().orElseThrow();
        assertTrue(base.precedence() < main.precedence(), "an import is below its importer");
        assertEquals(main.precedence(), part.precedence(), "an include shares the precedence");
        assertEquals(4, main.line());

        List<TemplateRules.Candidate> byDefault = TemplateRules.candidates(rules, "#default");
        assertEquals(List.of("main.xsl", "part.xsl", "base.xsl"),
                byDefault.stream().map(c -> SourceLocations.fileName(c.rule().path())).toList(),
                "precedence first, then priority (main 0 beats part -1)");
        assertEquals(List.of("base.xsl"), TemplateRules.candidates(rules, "m").stream()
                .map(c -> SourceLocations.fileName(c.rule().path())).toList(), "only rules of the mode");
    }

    @Test
    void explanationsNameTheWinnerAndWhyOthersLose() {
        Map<String, String> texts = Map.of("/s/a.xsl", "<xsl:stylesheet xmlns:xsl='x'>\n"
                + "<xsl:template match='item'/>\n"                 // 2: priority 0
                + "<xsl:template match=\"item[@n='2']\"/>\n"        // 3: 0.5 -> wins
                + "<xsl:template match='*'/>\n"                    // 4: -0.5
                + "<xsl:template match='other'/>\n"                // 5: no match
                + "</xsl:stylesheet>");
        List<TemplateRules.Candidate> candidates = TemplateRules.candidates(
                TemplateRules.collect("/s/a.xsl", texts::get), "#default");
        List<MatchExplanation.Line> lines = MatchExplanation.explain(candidates,
                c -> MatchExplanation.Result.of(!c.alternative().equals("other")));
        assertEquals(List.of(MatchExplanation.Status.WINS, MatchExplanation.Status.LOSES,
                MatchExplanation.Status.LOSES, MatchExplanation.Status.NO_MATCH),
                lines.stream().map(MatchExplanation.Line::status).toList());
        assertEquals("item[@n='2']", lines.get(0).candidate().alternative());
        assertEquals("lower priority", lines.get(1).reason());
        assertTrue(lines.get(0).text().startsWith("✓ wins:  match=\"item[@n='2']\"  ·  priority 0.5 (default)"),
                lines.get(0).text());
        assertTrue(TemplateRules.testExpression("item").contains("(item)"));
    }
}
