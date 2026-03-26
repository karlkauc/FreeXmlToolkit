package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for XML comment parsing at various nesting levels in XsdNodeFactory.
 *
 * @since 2.0
 */
class XsdNodeFactoryCommentParsingTest {

    @Test
    @DisplayName("Should parse comments at schema level")
    void testSchemaLevelComments() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <!-- Schema-level comment -->
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        long commentCount = schema.getChildren().stream()
                .filter(c -> c instanceof XsdComment).count();
        assertEquals(1, commentCount, "Should have 1 schema-level comment");

        XsdComment comment = schema.getChildren().stream()
                .filter(c -> c instanceof XsdComment)
                .map(c -> (XsdComment) c)
                .findFirst().orElseThrow();
        assertTrue(comment.getContent().contains("Schema-level comment"));
    }

    @Test
    @DisplayName("Should parse comments inside xs:sequence")
    void testSequenceComments() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <!-- Comment before name -->
                                <xs:element name="name" type="xs:string"/>
                                <!-- Comment before age -->
                                <xs:element name="age" type="xs:int"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        // Navigate to sequence
        XsdElement root = (XsdElement) schema.getChildren().stream()
                .filter(c -> c instanceof XsdElement).findFirst().orElseThrow();
        XsdComplexType ct = (XsdComplexType) root.getChildren().stream()
                .filter(c -> c instanceof XsdComplexType).findFirst().orElseThrow();
        XsdSequence seq = (XsdSequence) ct.getChildren().stream()
                .filter(c -> c instanceof XsdSequence).findFirst().orElseThrow();

        long commentCount = seq.getChildren().stream()
                .filter(c -> c instanceof XsdComment).count();
        assertEquals(2, commentCount, "Should have 2 comments in sequence");
    }

    @Test
    @DisplayName("Should parse comments inside xs:choice")
    void testChoiceComments() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:choice>
                                <!-- Option A -->
                                <xs:element name="a" type="xs:string"/>
                                <!-- Option B -->
                                <xs:element name="b" type="xs:int"/>
                            </xs:choice>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdElement root = (XsdElement) schema.getChildren().stream()
                .filter(c -> c instanceof XsdElement).findFirst().orElseThrow();
        XsdComplexType ct = (XsdComplexType) root.getChildren().stream()
                .filter(c -> c instanceof XsdComplexType).findFirst().orElseThrow();
        XsdChoice choice = (XsdChoice) ct.getChildren().stream()
                .filter(c -> c instanceof XsdChoice).findFirst().orElseThrow();

        long commentCount = choice.getChildren().stream()
                .filter(c -> c instanceof XsdComment).count();
        assertEquals(2, commentCount, "Should have 2 comments in choice");
    }

    @Test
    @DisplayName("Should parse comments inside xs:complexType")
    void testComplexTypeComments() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:complexType name="PersonType">
                        <!-- Attributes section -->
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                        </xs:sequence>
                        <!-- End of PersonType -->
                    </xs:complexType>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdComplexType ct = (XsdComplexType) schema.getChildren().stream()
                .filter(c -> c instanceof XsdComplexType).findFirst().orElseThrow();

        long commentCount = ct.getChildren().stream()
                .filter(c -> c instanceof XsdComment).count();
        assertEquals(2, commentCount, "Should have 2 comments in complexType");
    }

    @Test
    @DisplayName("Should parse leading comments before schema element")
    void testLeadingComments() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- Leading comment before schema -->
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        assertFalse(schema.getLeadingComments().isEmpty(),
                "Should have at least 1 leading comment");
        assertTrue(schema.getLeadingComments().get(0).contains("Leading comment"));
    }

    @Test
    @DisplayName("Should parse comments inside xs:all")
    void testAllComments() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:all>
                                <!-- Comment in all -->
                                <xs:element name="a" type="xs:string"/>
                            </xs:all>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        XsdElement root = (XsdElement) schema.getChildren().stream()
                .filter(c -> c instanceof XsdElement).findFirst().orElseThrow();
        XsdComplexType ct = (XsdComplexType) root.getChildren().stream()
                .filter(c -> c instanceof XsdComplexType).findFirst().orElseThrow();
        XsdAll all = (XsdAll) ct.getChildren().stream()
                .filter(c -> c instanceof XsdAll).findFirst().orElseThrow();

        long commentCount = all.getChildren().stream()
                .filter(c -> c instanceof XsdComment).count();
        assertEquals(1, commentCount, "Should have 1 comment in all");
    }
}
