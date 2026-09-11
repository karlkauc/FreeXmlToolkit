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
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Abstract elements and types (work package E of the sample XML plan). An instance may contain neither an abstract
 * element nor an element whose type is abstract without {@code xsi:type}. Modelled on KML and SIRI (abstract heads of
 * substitution groups, also as roots), Garmin TCX, rim and UCI (elements of abstract types) and INSPIRE (heads and
 * types of GML, members in a third namespace).
 */
class SampleXmlAbstractContentTest {

    private static final String DRAWING = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:draw" targetNamespace="urn:draw"
                       elementFormDefault="qualified">
              <xs:element name="shape" type="ShapeType" abstract="true"/>
              <xs:element name="drawing">
                <xs:complexType>
                  <xs:sequence><xs:element ref="shape" maxOccurs="unbounded"/></xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="polygon" type="ShapeType" abstract="true" substitutionGroup="shape"/>
              <xs:element name="square" substitutionGroup="polygon">
                <xs:complexType>
                  <xs:complexContent>
                    <xs:extension base="ShapeType">
                      <xs:sequence><xs:element name="side" type="xs:decimal"/></xs:sequence>
                    </xs:extension>
                  </xs:complexContent>
                </xs:complexType>
              </xs:element>
              <xs:element name="group" type="GroupType" substitutionGroup="shape"/>
              <xs:complexType name="ShapeType">
                <xs:attribute name="label" type="xs:string"/>
              </xs:complexType>
              <xs:complexType name="GroupType">
                <xs:complexContent>
                  <xs:extension base="ShapeType">
                    <xs:sequence><xs:element ref="shape" minOccurs="0" maxOccurs="unbounded"/></xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

    private static final String SOURCES = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:src="urn:src" targetNamespace="urn:src"
                    elementFormDefault="qualified">
              <element name="activity">
                <complexType>
                  <sequence><element name="creator" type="src:AbstractSource"/></sequence>
                </complexType>
              </element>
              <element name="action" type="src:ActionType"/>
              <complexType name="AbstractSource" abstract="true">
                <sequence><element name="name" type="string"/></sequence>
              </complexType>
              <complexType name="Device">
                <complexContent>
                  <extension base="src:AbstractSource">
                    <sequence><element name="unitId" type="unsignedInt"/></sequence>
                  </extension>
                </complexContent>
              </complexType>
              <complexType name="ActionType" abstract="true">
                <attribute name="kind" type="string"/>
              </complexType>
              <complexType name="IntermediateAction" abstract="true">
                <complexContent><extension base="src:ActionType"/></complexContent>
              </complexType>
              <complexType name="NotifyAction">
                <complexContent>
                  <extension base="src:IntermediateAction">
                    <attribute name="to" type="string" use="required"/>
                  </extension>
                </complexContent>
              </complexType>
            </schema>
            """;

    private static final String ADDRESS = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:ad="urn:ad" xmlns:gml="urn:gml"
                    targetNamespace="urn:ad" elementFormDefault="qualified">
              <import namespace="urn:gml" schemaLocation="gml/gml.xsd"/>
              <import namespace="urn:bu" schemaLocation="bu/bu.xsd"/>
              <element name="Address" type="ad:AddressType"/>
              <complexType name="AddressType">
                <sequence>
                  <element name="position">
                    <complexType><sequence><element ref="gml:AbstractGeometry"/></sequence></complexType>
                  </element>
                  <element name="building">
                    <complexType><sequence><element ref="gml:AbstractFeature"/></sequence></complexType>
                  </element>
                  <element name="outline" type="gml:AbstractShapeType"/>
                </sequence>
              </complexType>
            </schema>
            """;

    private static final String GML = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:gml="urn:gml" targetNamespace="urn:gml"
                    elementFormDefault="qualified">
              <element name="AbstractGeometry" type="gml:AbstractGeometryType" abstract="true"/>
              <element name="Point" type="gml:PointType" substitutionGroup="gml:AbstractGeometry"/>
              <element name="AbstractFeature" type="gml:AbstractFeatureType" abstract="true"/>
              <complexType name="AbstractGeometryType" abstract="true">
                <attribute name="srsName" type="anyURI"/>
              </complexType>
              <complexType name="PointType">
                <complexContent>
                  <extension base="gml:AbstractGeometryType">
                    <sequence><element name="pos" type="string"/></sequence>
                  </extension>
                </complexContent>
              </complexType>
              <complexType name="AbstractFeatureType" abstract="true"><sequence/></complexType>
              <complexType name="AbstractShapeType" abstract="true"><sequence/></complexType>
              <complexType name="CircleType">
                <complexContent>
                  <extension base="gml:AbstractShapeType">
                    <attribute name="radius" type="decimal" use="required"/>
                  </extension>
                </complexContent>
              </complexType>
            </schema>
            """;

    private static final String BU = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:bu="urn:bu" xmlns:gml="urn:gml"
                    targetNamespace="urn:bu" elementFormDefault="qualified">
              <import namespace="urn:gml" schemaLocation="../gml/gml.xsd"/>
              <element name="Building" type="bu:BuildingType" substitutionGroup="gml:AbstractFeature"/>
              <complexType name="BuildingType">
                <complexContent>
                  <extension base="gml:AbstractFeatureType">
                    <sequence><element name="height" type="decimal"/></sequence>
                  </extension>
                </complexContent>
              </complexType>
            </schema>
            """;

    /** UCI {@code StoreLoadoutConfiguration}: the concrete type of an abstract type contains itself. */
    private static final String LOADOUT = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:load" targetNamespace="urn:load"
                       elementFormDefault="qualified">
              <xs:element name="configuration">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="hardpoint" type="ItemPET" minOccurs="0" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="ItemPET" abstract="true"/>
              <xs:complexType name="ItemType">
                <xs:complexContent>
                  <xs:extension base="ItemPET">
                    <xs:sequence>
                      <xs:element name="location" type="xs:int"/>
                      <xs:element name="possibleStore" type="StoreType" minOccurs="0" maxOccurs="unbounded"/>
                    </xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
              <xs:complexType name="StoreType">
                <xs:sequence><xs:element name="carriage" type="CarriageType"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="CarriageType">
                <xs:sequence><xs:element name="storeList" type="ItemPET" maxOccurs="unbounded"/></xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @Test
    void abstractGlobalElementsAreNotOfferedAsRoots() throws Exception {
        Path xsd = write("drawing.xsd", DRAWING);

        XsdDocumentationService service = service(xsd);
        assertEquals(List.of("drawing", "square", "group"), service.getRootElementNames());
        assertEquals("drawing", service.getDefaultRootElementName());
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void abstractParticlesAreReplacedByConcreteSubstitutionGroupMembers(boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = write("drawing.xsd", DRAWING);

        for (String xml : List.of(SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic),
                generate(xsd, "group", realistic, mandatoryOnly))) {
            assertFalse(xml.contains("<shape") || xml.contains("<polygon"), "abstract element emitted:\n" + xml);
            assertTrue(xml.contains("<square") || xml.contains("<group"), "no substitution group member:\n" + xml);
            assertValid(xsd, xml);
        }
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void elementsOfAbstractTypesGetAConcreteXsiType(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = write("sources.xsd", SOURCES);

        String activity = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);
        assertTrue(activity.contains("<creator xsi:type=\"Device\""), activity);
        assertTrue(activity.contains("<unitId>"), activity);
        assertValid(xsd, activity);

        String action = generate(xsd, "action", realistic, mandatoryOnly);
        assertTrue(action.contains("xsi:type=\"NotifyAction\""), action);
        assertTrue(action.contains(" to=\""), action);
        assertValid(xsd, action);
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void foreignHeadsMembersAndTypesAreQualified(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = write("ad.xsd", ADDRESS);
        Files.createDirectories(dir.resolve("gml"));
        Files.createDirectories(dir.resolve("bu"));
        write("gml/gml.xsd", GML);
        write("bu/bu.xsd", BU);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);

        assertTrue(xml.contains("<gml:Point"), xml);
        assertTrue(xml.contains("<bu:Building"), xml);
        assertTrue(xml.contains("xmlns:bu=\"urn:bu\""), xml);
        assertTrue(xml.contains("xsi:type=\"gml:CircleType\""), xml);
        assertValid(xsd, xml);
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void aConcreteTypeThatContainsItselfKeepsItsRequiredContent(boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = write("loadout.xsd", LOADOUT);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);

        assertFalse(xml.contains("<storeList xsi:type=\"ItemType\"/>"), "required location missing:\n" + xml);
        assertValid(xsd, xml);
    }

    @Test
    void theDocumentationModelKeepsAbstractDeclarations() throws Exception {
        Path xsd = write("sources.xsd", SOURCES);

        XsdDocumentationService service = service(xsd);
        service.processXsd(Boolean.FALSE);

        XsdExtendedElement creator = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "creator".equals(e.getElementName()))
                .findFirst().orElseThrow();
        assertEquals("src:AbstractSource", creator.getElementType());
        assertEquals(null, creator.getXsiType());
    }

    private Path write(String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static XsdDocumentationService service(Path xsd) {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        return service;
    }

    /** A sample for the named root, as the audit harness generates it for each generator. */
    private static String generate(Path xsd, String root, boolean realistic, boolean mandatoryOnly) throws Exception {
        XsdDocumentationService service = service(xsd);
        if (!realistic) {
            return service.generateSampleXml(root, mandatoryOnly, 2);
        }
        service.loadSchema(XsdDocumentationService.MarkdownMode.ALL);
        service.expandForSample(root, mandatoryOnly, XsdDocumentationService.MarkdownMode.ALL);
        GenerationProfile profile = new GenerationProfile("Realistic");
        profile.setMandatoryOnly(mandatoryOnly);
        profile.setMaxOccurrences(2);
        return new ProfiledXmlGeneratorService().generateRealistic(profile, service.xsdDocumentationData,
                xsd.toString(), root);
    }

    private static void assertValid(Path xsd, String xml) throws Exception {
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }
}
