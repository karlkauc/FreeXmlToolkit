package org.fxt.freexmltoolkit.service.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.service.ai.context.ValidationProblemFacts;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifiersTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="n" type="xs:int" maxOccurs="unbounded"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>""";

    private static final String XML = "<root>\n  <n>1</n>\n  <n>x</n>\n</root>";

    @TempDir
    Path temp;

    private Path schema;
    private ToolContext context;

    @BeforeAll
    static void services() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @BeforeEach
    void setUp() throws IOException {
        schema = Files.writeString(temp.resolve("s.xsd"), XSD);
        context = InAppToolContext.forFiles(List.of(schema.toFile()), List.of(), ToolLimits.DEFAULTS);
    }

    private ProblemFixVerifier fixVerifier() {
        return new ProblemFixVerifier(XML, new ValidationProblemFacts("XSD", "error", 3, 10,
                "cvc-datatype-valid.1.2.1: 'x' is not a valid value for 'integer'.", null, null), schema, null, context);
    }

    @Test
    void aGoodFixIsVerifiedAndPlanned() {
        ProblemFixVerifier verifier = fixVerifier();
        String fix = "{\"xpath\": \"/root/n[2]\", \"mode\": \"SET_VALUE\", \"replacement\": \"2\"}";
        Verification v = verifier.verify(fix, CancelToken.none());
        assertTrue(v.verified(), v.toString());
        assertNotNull(verifier.planFor(fix));
        assertEquals("<root>\n  <n>1</n>\n  <n>2</n>\n</root>", verifier.planFor(fix).applyTo(XML));
    }

    @Test
    void aFixThatDeletesContentIsRejected() {
        String fix = "{\"xpath\": \"/root/n[2]\", \"mode\": \"REPLACE_FRAGMENT\", \"replacement\": \"<!-- gone -->\"}";
        Verification v = fixVerifier().verify(fix, CancelToken.none());
        assertFalse(v.verified(), v.toString());
        assertTrue(v.feedbackForModel().contains("removes"), v.feedbackForModel());
    }

    @Test
    void aFixThatLeavesTheProblemOrAddsOneIsRejected() {
        ProblemFixVerifier verifier = fixVerifier();
        Verification same = verifier.verify("{\"xpath\": \"/root/n[1]\", \"mode\": \"SET_VALUE\", \"replacement\": \"5\"}",
                CancelToken.none());
        assertFalse(same.verified(), same.toString());
        Verification worse = verifier.verify("{\"xpath\": \"/root/n[2]\", \"mode\": \"SET_VALUE\", \"replacement\": \"y\"}",
                CancelToken.none());
        assertFalse(worse.verified(), worse.toString());
        assertNotNull(verifier.planFor("{\"xpath\": \"/root/n[2]\", \"mode\": \"SET_VALUE\", \"replacement\": \"y\"}"),
                "an applicable but unverified fix keeps its plan for \"Apply unverified\"");
        assertNull(verifier.planFor("{\"xpath\": \"/root/q\"}"));
    }

    @Test
    void badProposalsAreFailedRounds() {
        ProblemFixVerifier verifier = fixVerifier();
        assertFalse(verifier.verify("not json", CancelToken.none()).verified());
        assertFalse(verifier.verify("{\"xpath\": \"/root/q\", \"mode\": \"SET_VALUE\", \"replacement\": \"1\"}",
                CancelToken.none()).verified());
        assertFalse(verifier.verify("{\"xpath\": \"/root/n\", \"mode\": \"SET_VALUE\", \"replacement\": \"1\"}",
                CancelToken.none()).feedbackForModel().isEmpty(), "two matches");
        assertFalse(verifier.verify("{\"xpath\": \"/root/n[2]\", \"mode\": \"COMPUTE_VALUE\", \"replacement\": \"1\"}",
                CancelToken.none()).verified());
    }

    @Test
    void xsltVerifierRunsValidatesAndComparesWithoutWritingFiles() throws IOException {
        String source = "<items><i>a</i><i>b</i></items>";
        String target = "<list><entry>a</entry><entry>b</entry></list>";
        Path targetXsd = Files.writeString(temp.resolve("t.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="list"><xs:complexType><xs:sequence>
                    <xs:element name="entry" type="xs:string" maxOccurs="unbounded"/>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>""");
        List<Path> before = files();
        XsltVerifier verifier = new XsltVerifier(source, null, target, targetXsd, context);
        Verification good = verifier.verify("""
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><list><xsl:for-each select="//i"><entry><xsl:value-of select="."/></entry></xsl:for-each></list></xsl:template>
                </xsl:stylesheet>""", CancelToken.none());
        assertTrue(good.verified(), good.toString());
        assertTrue(verifier.lastOutput().contains("<entry>a</entry>"));
        Verification invalid = verifier.verify("""
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><list><wrong/></list></xsl:template>
                </xsl:stylesheet>""", CancelToken.none());
        assertFalse(invalid.verified());
        assertTrue(invalid.feedbackForModel().contains("not valid"), invalid.feedbackForModel());
        Verification broken = verifier.verify("<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:template match=\"/\"><xsl:value-of select=\"//i[\"/></xsl:template></xsl:stylesheet>", CancelToken.none());
        assertFalse(broken.verified());
        assertFalse(broken.checks().getFirst().passed(), "does not compile");
        assertEquals(before, files());
    }

    private List<Path> files() throws IOException {
        try (Stream<Path> files = Files.walk(temp)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
