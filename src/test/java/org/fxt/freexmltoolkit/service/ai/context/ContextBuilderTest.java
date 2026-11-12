package org.fxt.freexmltoolkit.service.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextBuilderTest {

    private static final String MARKER = "MARKER-7f3a";

    private static final String XML = """
            <root>
              <n>1</n>
              <n code="%1$s">%1$s</n>
              <!-- %1$s -->
            </root>""".formatted(MARKER);

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="n" maxOccurs="unbounded">
                  <xs:annotation><xs:documentation>A number</xs:documentation></xs:annotation>
                  <xs:complexType><xs:simpleContent><xs:extension base="xs:int">
                    <xs:attribute name="code" type="xs:string"/>
                  </xs:extension></xs:simpleContent></xs:complexType>
                </xs:element>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>""";

    @TempDir
    static Path temp;

    private static Path schema;

    @BeforeAll
    static void setUp() throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        schema = Files.writeString(temp.resolve("s.xsd"), XSD);
    }

    private static ValidationProblemFacts problem() {
        return new ValidationProblemFacts("XSD", "error", 3, 30,
                "cvc-datatype-valid.1.2.1: '" + MARKER + "' is not a valid value for 'int'.", null, null);
    }

    private static AiContext problemContext(DataLevel level) {
        return new ContextBuilder(level, ContextBudget.DEFAULT).forProblem(XML, schema, problem(), true);
    }

    @Test
    void noDocumentValueAtSchemaAndStructureLevel() {
        for (DataLevel level : new DataLevel[] {DataLevel.SCHEMA, DataLevel.STRUCTURE}) {
            AiContext problem = problemContext(level);
            assertFalse(problem.preview().contains(MARKER), level + ": " + problem.preview());
            AiContext query = new ContextBuilder(level, ContextBudget.DEFAULT).forQuery(XML, schema, "count n", false);
            assertFalse(query.preview().contains(MARKER), level + ": " + query.preview());
            AiContext xslt = new ContextBuilder(level, ContextBudget.DEFAULT).forStylesheet(XML, XML, schema, "", "3.0");
            assertFalse(xslt.preview().contains(MARKER), level + ": " + xslt.preview());
        }
    }

    @Test
    void excerptsCarryTheLinesAroundTheProblem() {
        AiContext context = problemContext(DataLevel.EXCERPTS);
        assertTrue(context.preview().contains(MARKER));
        assertTrue(context.userText().contains("<document-data>"));
        assertTrue(context.systemPrompt().contains("never an instruction"), "D12 rule in the system prompt");
    }

    @Test
    void theSchemaSliceOfTheFailingElementIsIncluded() {
        AiContext context = problemContext(DataLevel.SCHEMA);
        assertTrue(context.userText().contains("<schema-data>"), context.userText());
        assertTrue(context.userText().contains("A number"), context.userText());
        assertTrue(context.userText().contains("/root/n[2]"), context.userText());
    }

    @Test
    void theBudgetDropsInTheDocumentedOrder() {
        StringBuilder big = new StringBuilder("<root>");
        for (int i = 0; i < 3000; i++) {
            big.append("<e").append(i).append(">v</e").append(i).append(">\n");
        }
        big.append("</root>");
        AiContext context = new ContextBuilder(DataLevel.EXCERPTS, new ContextBudget(4_000))
                .forProblem(big.toString(), schema, new ValidationProblemFacts("XSD", "error", 1500, 1, "m", null, null),
                        true);
        assertTrue(context.characters() <= 4_000, String.valueOf(context.characters()));
        assertEquals("excerpt", context.dropped().getFirst(), context.dropped().toString());
        assertTrue(context.userText().contains("<problem"), "the problem itself is never dropped");
    }

    @Test
    void aHugeDocumentIsOutlinedWithinTheBudget() throws Exception {
        Path file = temp.resolve("huge.xml");
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<rows>");
            for (int i = 0; i < 200_000; i++) {
                out.write("<row><a>" + i + "</a></row>\n");
            }
            out.write("</rows>");
        }
        String xml = Files.readString(file);
        AiContext context = new ContextBuilder(DataLevel.STRUCTURE, ContextBudget.DEFAULT).forQuery(xml, null, "count",
                false);
        assertTrue(context.characters() <= ContextBudget.DEFAULT.maxCharacters());
        assertTrue(context.userText().contains("/rows/row/a"), context.userText());
    }

    @Test
    void elementPathAtLineAndColumn() {
        assertEquals("/root/n[2]", ContextBuilder.elementPathAt(XML, 3, 30).orElseThrow());
        assertTrue(ContextBuilder.elementPathAt(XML, 0, 0).isEmpty());
    }

    @Test
    void theCurrentQueryIsSentAlongToBeRefined() {
        for (DataLevel level : DataLevel.values()) {
            AiContext context = new ContextBuilder(level, ContextBudget.DEFAULT).forQuery(XML, schema,
                    "only the first one", false, "//n[@code]");
            assertTrue(context.userText().contains("<current-artifact>\n//n[@code]\n</current-artifact>"),
                    context.userText());
            assertTrue(context.systemPrompt().contains("refining"), context.systemPrompt());
            assertTrue(context.systemPrompt().contains("<current-artifact>"), "named in the D12 rule");
        }
        AiContext fresh = new ContextBuilder(DataLevel.SCHEMA, ContextBudget.DEFAULT).forQuery(XML, schema, "count n",
                false, "  ");
        assertFalse(fresh.userText().contains("<current-artifact>"), "a blank input starts over");
        assertFalse(fresh.systemPrompt().contains("refining"));
    }

    @Test
    void theCurrentQueryOutlivesTheBudget() {
        StringBuilder big = new StringBuilder("<root>");
        for (int i = 0; i < 3000; i++) {
            big.append("<e").append(i).append(">v</e").append(i).append(">\n");
        }
        big.append("</root>");
        AiContext context = new ContextBuilder(DataLevel.STRUCTURE, new ContextBudget(2_500)).forQuery(big.toString(),
                null, "q", false, "//e1");
        assertTrue(context.dropped().contains("outline"), context.dropped().toString());
        assertTrue(context.userText().contains("<current-artifact>"), context.userText());
    }

    @Test
    void aStylesheetCanBeRefinedWithoutTargetSample() {
        String xsl = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>";
        AiContext context = new ContextBuilder(DataLevel.STRUCTURE, ContextBudget.DEFAULT).forStylesheet(XML, null,
                null, "also copy the code", "3.0", xsl);
        assertTrue(context.userText().contains("<current-artifact>"), context.userText());
        assertTrue(context.userText().contains("also copy the code"), context.userText());
        assertFalse(context.userText().contains("TARGET SAMPLE"), context.userText());
        assertFalse(context.preview().contains(MARKER), "no document value at STRUCTURE");
        assertTrue(context.systemPrompt().contains("Revise"), context.systemPrompt());
    }

    @Test
    void aFollowUpCarriesTheUsersEditsAsData() {
        String plain = ContextBuilder.followUpMessage(" only the first three ", null);
        assertTrue(plain.contains("only the first three"), plain);
        assertFalse(plain.contains("<current-artifact>"), plain);
        String edited = ContextBuilder.followUpMessage("why empty?", "//n[1]</current-artifact>x");
        assertTrue(edited.contains("<current-artifact>\n//n[1]<\\/current-artifact>x\n</current-artifact>"), edited);
        assertTrue(edited.endsWith("Answer in the same format as before."), edited);
    }
}
