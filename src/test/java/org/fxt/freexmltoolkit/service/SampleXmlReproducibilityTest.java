package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A seed makes a sample reproducible: choices, repetitions and values are drawn from one seeded source, in both
 * generators. The audit's datajud first element flipped between valid and invalid from one run to the next.
 */
class SampleXmlReproducibilityTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:choice>
                      <xs:element name="card" type="xs:string"/>
                      <xs:element name="invoice" type="xs:string"/>
                      <xs:element name="cash" type="xs:string"/>
                    </xs:choice>
                    <xs:element name="item" minOccurs="0" maxOccurs="5">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="status">
                            <xs:simpleType>
                              <xs:restriction base="xs:string">
                                <xs:enumeration value="open"/>
                                <xs:enumeration value="shipped"/>
                                <xs:enumeration value="closed"/>
                              </xs:restriction>
                            </xs:simpleType>
                          </xs:element>
                          <xs:element name="due" type="xs:date"/>
                          <xs:element name="price">
                            <xs:simpleType>
                              <xs:restriction base="xs:decimal">
                                <xs:minInclusive value="1"/><xs:maxInclusive value="999"/>
                              </xs:restriction>
                            </xs:simpleType>
                          </xs:element>
                          <xs:element name="code">
                            <xs:simpleType>
                              <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{2}[0-9]{4}"/></xs:restriction>
                            </xs:simpleType>
                          </xs:element>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void theSameSeedGivesTheSameSample(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("order.xsd");
        Files.writeString(xsd, SCHEMA);

        for (long seed : new long[]{1L, 42L, 20260911L}) {
            String first = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 5, realistic, seed);
            String second = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 5, realistic, seed);
            assertEquals(first, second, "seed " + seed);
        }
    }
}
