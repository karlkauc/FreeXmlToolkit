package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sample generation must stay within bounded memory: it expands only the chosen root, skips optional particles in
 * mandatory-only mode, and answers with an XML comment instead of running out of memory when a schema expands beyond
 * the node limit or the sample beyond the character limit (UBL 2.1, OASIS UCI, A-GRA in the real-world corpus).
 */
class SampleXmlExpansionLimitTest {

    private static final String NODE_LIMIT = "fxt.sampleXml.maxExpandedNodes";
    private static final String CHAR_LIMIT = "fxt.sampleXml.maxOutputChars";

    @TempDir
    Path dir;

    @AfterEach
    void clearLimits() {
        System.clearProperty(NODE_LIMIT);
        System.clearProperty(CHAR_LIMIT);
    }

    @Test
    void expansionAboveTheNodeLimitReturnsAnErrorComment() throws Exception {
        System.setProperty(NODE_LIMIT, "5000");
        Path xsd = write("""
                  <xs:element name="huge" type="L0"/>
                %s""".formatted(binaryTree(16)));

        String plain = service(xsd).generateSampleXml(false, 2);
        assertLimitComment(plain, NODE_LIMIT, "huge");

        String realistic = SampleXmlRunner.generate(xsd.toFile(), false, 2, true);
        assertLimitComment(realistic, NODE_LIMIT, "huge");
    }

    @Test
    void onlyTheChosenRootIsExpanded() throws Exception {
        System.setProperty(NODE_LIMIT, "5000");
        Path xsd = write("""
                  <xs:element name="small">
                    <xs:complexType><xs:sequence><xs:element name="text" type="xs:string"/></xs:sequence></xs:complexType>
                  </xs:element>
                  <xs:element name="huge" type="L0"/>
                %s""".formatted(binaryTree(16)));

        XsdDocumentationService service = service(xsd);
        assertEquals(List.of("small", "huge"), service.getRootElementNames());
        String small = service.generateSampleXml("small", false, 2);
        assertTrue(small.contains("<small"), small);
        assertValid(xsd, small);
        assertLimitComment(service.generateSampleXml("huge", false, 2), NODE_LIMIT, "huge");

        String realisticFirstRoot = SampleXmlRunner.generate(xsd.toFile(), false, 2, true);
        assertTrue(realisticFirstRoot.contains("<small"), realisticFirstRoot);
        assertValid(xsd, realisticFirstRoot);
    }

    @Test
    void mandatoryOnlyModeDoesNotExpandOptionalParticles() throws Exception {
        System.setProperty(NODE_LIMIT, "5000");
        Path xsd = write("""
                  <xs:element name="doc">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="id" type="xs:string"/>
                        <xs:element name="extra" type="L0" minOccurs="0"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                %s""".formatted(binaryTree(16)));

        String required = service(xsd).generateSampleXml("doc", true, 2);
        assertTrue(required.contains("<id>"), required);
        assertValid(xsd, required);
        assertLimitComment(service(xsd).generateSampleXml("doc", false, 2), NODE_LIMIT, "doc");

        String realisticRequired = SampleXmlRunner.generate(xsd.toFile(), true, 2, true);
        assertTrue(realisticRequired.contains("<id>"), realisticRequired);
        assertValid(xsd, realisticRequired);
    }

    @Test
    void sampleAboveTheCharacterLimitReturnsAnErrorComment() throws Exception {
        System.setProperty(CHAR_LIMIT, "100000");
        StringBuilder types = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            types.append("""
                      <xs:complexType name="N%d">
                        <xs:sequence><xs:element name="n%d" type="%s" maxOccurs="unbounded"/></xs:sequence>
                      </xs:complexType>
                    """.formatted(i, i, i == 17 ? "xs:string" : "N" + (i + 1)));
        }
        Path xsd = write("""
                  <xs:element name="deep" type="N0"/>
                %s""".formatted(types));

        assertLimitComment(service(xsd).generateSampleXml("deep", false, 2), CHAR_LIMIT, null);
        assertLimitComment(SampleXmlRunner.generate(xsd.toFile(), false, 2, true), CHAR_LIMIT, null);
    }

    /** Complex types L0..L{depth-1}, each a sequence of two elements of the next level: 2^depth leaves. */
    private static String binaryTree(int depth) {
        StringBuilder types = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            String next = i == depth - 1 ? "xs:string" : "L" + (i + 1);
            types.append("""
                      <xs:complexType name="L%d">
                        <xs:sequence>
                          <xs:element name="a" type="%s"/>
                          <xs:element name="b" type="%s"/>
                        </xs:sequence>
                      </xs:complexType>
                    """.formatted(i, next, next));
        }
        return types.toString();
    }

    private Path write(String content) throws Exception {
        Path xsd = dir.resolve("schema.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" + content + "</xs:schema>\n");
        return xsd;
    }

    private static XsdDocumentationService service(Path xsd) {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        return service;
    }

    private static void assertLimitComment(String xml, String limitProperty, String root) {
        assertTrue(xml.startsWith("<!--"), () -> "expected a limit comment, got: " + xml.substring(0, Math.min(200, xml.length())));
        assertTrue(xml.contains(limitProperty), xml);
        if (root != null) {
            assertTrue(xml.contains("'" + root + "'"), xml);
        }
        assertFalse(xml.length() > 1000, "the comment must not carry the partial sample");
    }

    private static void assertValid(Path xsd, String xml) throws Exception {
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
