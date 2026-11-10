package org.fxt.freexmltoolkit.service.ai.tool;

/** Schemas shared by {@code SchemaToolsTest} and {@code SchemaSliceTest}. */
public final class SchemaToolsTestSchemas {

    private SchemaToolsTestSchemas() {
    }

    public static final String MAIN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:o="urn:o" xmlns="urn:m"
                       targetNamespace="urn:m" elementFormDefault="qualified">
              <xs:import namespace="urn:o" schemaLocation="other.xsd"/>
              <xs:element name="Order">
                <xs:annotation>
                  <xs:documentation xml:lang="en">An order</xs:documentation>
                  <xs:documentation xml:lang="de">Eine Bestellung</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="Id" type="IdType"/>
                    <xs:element name="Status" type="StatusType" minOccurs="0"/>
                    <xs:element ref="o:Note" minOccurs="0" maxOccurs="unbounded"/>
                    <xs:element name="Folder" type="FolderType" minOccurs="0"/>
                    <xs:choice>
                      <xs:element name="Cash" type="xs:decimal"/>
                      <xs:element name="Card" type="xs:string"/>
                    </xs:choice>
                  </xs:sequence>
                  <xs:attribute name="version" type="xs:string" use="required"/>
                </xs:complexType>
              </xs:element>
              <xs:simpleType name="IdType">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="10"/>
                  <xs:pattern value="[A-Z]+"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="StatusType">
                <xs:restriction base="xs:token">
                  <xs:enumeration value="OPEN"/>
                  <xs:enumeration value="CLOSED"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:complexType name="FolderType">
                <xs:sequence>
                  <xs:element name="Name" type="xs:string"/>
                  <xs:element name="Folder" type="FolderType" minOccurs="0" maxOccurs="unbounded"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    public static final String OTHER = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:o" elementFormDefault="qualified">
              <xs:element name="Note" type="xs:string">
                <xs:annotation><xs:documentation>A free text note</xs:documentation></xs:annotation>
              </xs:element>
            </xs:schema>
            """;

}
