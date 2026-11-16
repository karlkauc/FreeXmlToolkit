package org.fxt.freexmltoolkit.service.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** List placeholders and parameter templates of the shared command template. */
class CommandTemplateTest {

    private final CommandTemplate template = new CommandTemplate(List.of("xsl", "out", "params"));

    @Test
    void aListPlaceholderBecomesSeveralArguments() {
        Map<String, List<String>> lists = Map.of("params", List.of("--stringparam", "a", "x y"));
        assertEquals(List.of("--stringparam", "a", "x y", "-o", "/o.html", "/s.xsl"),
                template.expand("${params} -o ${out} ${xsl}", Map.of("out", "/o.html", "xsl", "/s.xsl"), lists));
        assertEquals(List.of("/s.xsl"), template.expand("${params} ${xsl}", Map.of("xsl", "/s.xsl"), Map.of("params", List.of())));
        assertEquals(List.of("/s.xsl"), template.expand("[--params ${params}] ${xsl}", Map.of("xsl", "/s.xsl"),
                Map.of("params", List.of())), "an empty list leaves its group out");
        assertThrows(IllegalArgumentException.class, () -> template.expand("${fo}", Map.of()));
    }

    @Test
    void parameterTemplates() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", "Q4 report");
        params.put("n", 3);
        assertEquals(List.of("--stringparam", "title", "Q4 report", "--stringparam", "n", "3"),
                CommandTemplate.expandParameters("--stringparam {name} {value}", params));
        assertEquals(List.of("title=Q4 report", "n=3"), CommandTemplate.expandParameters("{name}={value}", params));
        assertEquals(List.of("--param=title:'Q4 report'", "--param=n:'3'"),
                CommandTemplate.expandParameters("--param={name}:'{value}'", params));
        assertNull(CommandTemplate.validateParameters("{name}={value}"));
        assertNotNull(CommandTemplate.validateParameters("--x {value}"), "the name is required");
        assertNotNull(CommandTemplate.validateParameters("[{name}]"));
    }
}
