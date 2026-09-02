package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Unit tests (no JavaFX) for {@link SchemaAnalysisRunner}: one parse feeds all four analysis
 * engines (statistics incl. unused types, quality, identity constraints, XPath validation).
 */
class SchemaAnalysisRunnerTest {

    static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                </xs:sequence>
                <xs:attribute name="id" type="xs:string"/>
              </xs:complexType>
              <xs:complexType name="OrphanType">
                <xs:sequence>
                  <xs:element name="unused" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="person" type="PersonType" maxOccurs="unbounded"/>
                    <xs:element name="ref" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:attribute name="pid" type="xs:string"/>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
                <xs:key name="personKey">
                  <xs:selector xpath="person"/>
                  <xs:field xpath="@id"/>
                </xs:key>
                <xs:keyref name="personRef" refer="personKey">
                  <xs:selector xpath="ref"/>
                  <xs:field xpath="@pid"/>
                </xs:keyref>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void analyzesAllFourAspectsFromOneParse() throws Exception {
        SchemaAnalysisData data = SchemaAnalysisRunner.analyze(XSD, "test.xsd", null);

        assertEquals("test.xsd", data.documentName());
        assertNotNull(data.schema());
        assertTrue(data.statistics().unusedTypes().contains("OrphanType"), data.statistics().unusedTypes().toString());
        assertFalse(data.statistics().unusedTypes().contains("PersonType"));
        assertTrue(data.statistics().getComplexTypeCount() >= 2, "named + anonymous complex types");

        int score = data.quality().score();
        assertTrue(score >= 0 && score <= 100, "score " + score);

        assertEquals(1, data.constraints().keys().size());
        assertEquals(1, data.constraints().keyRefs().size());
        assertEquals("personKey", data.constraints().keys().getFirst().name());

        // The validator only records XPaths with findings; well-formed selectors/fields yield none.
        assertTrue(data.xpath().isAllValid(), data.xpath().issues().toString());
        assertEquals(0, data.xpath().errorCount());
    }

    @Test
    void invalidContentThrows() {
        assertThrows(Exception.class, () -> SchemaAnalysisRunner.analyze("not xml", "x.xsd", null));
    }

    /**
     * Issue #36 regression (ported from the retired text-report runner): with the document's
     * path the relative {@code xs:import schemaLocation}s of xsheet-master.xsd resolve from
     * disk, so the imported schemas' elements and complex types are counted too.
     */
    @Test
    void pathResolvesRelativeImportsFromDisk() throws Exception {
        Path xsheet = Path.of("src/test/resources/xsd/xsheet/xsheet-master.xsd");
        String text = Files.readString(xsheet);

        SchemaAnalysisData without = SchemaAnalysisRunner.analyze(text, "xsheet-master.xsd", null);
        SchemaAnalysisData with = SchemaAnalysisRunner.analyze(text, "xsheet-master.xsd", xsheet);

        assertTrue(with.statistics().getComplexTypeCount() > 0);
        assertTrue(with.statistics().getElementCount() > without.statistics().getElementCount());
        assertTrue(with.statistics().getComplexTypeCount() > without.statistics().getComplexTypeCount());
    }
}
