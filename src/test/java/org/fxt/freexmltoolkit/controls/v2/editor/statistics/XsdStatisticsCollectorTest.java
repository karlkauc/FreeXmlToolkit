package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for XsdStatisticsCollector.
 *
 * @since 2.0
 */
class XsdStatisticsCollectorTest {

    private XsdSchema schema;
    private XsdStatisticsCollector collector;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");
        schema.setElementFormDefault("qualified");
        schema.setAttributeFormDefault("unqualified");
        collector = new XsdStatisticsCollector(schema);
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw NullPointerException for null schema")
    void testConstructorNullSchema() {
        assertThrows(NullPointerException.class, () -> new XsdStatisticsCollector(null));
    }

    @Test
    @DisplayName("constructor should accept valid schema")
    void testConstructorValidSchema() {
        assertDoesNotThrow(() -> new XsdStatisticsCollector(schema));
    }

    // ========== Schema Info Tests ==========

    @Nested
    @DisplayName("Schema Information Collection")
    class SchemaInfoTests {

        @Test
        @DisplayName("should collect target namespace")
        void testCollectTargetNamespace() {
            XsdStatistics stats = collector.collect();
            assertEquals("http://example.com/test", stats.targetNamespace());
        }

        @Test
        @DisplayName("should collect element form default")
        void testCollectElementFormDefault() {
            XsdStatistics stats = collector.collect();
            assertEquals("qualified", stats.elementFormDefault());
        }

        @Test
        @DisplayName("should collect attribute form default")
        void testCollectAttributeFormDefault() {
            XsdStatistics stats = collector.collect();
            assertEquals("unqualified", stats.attributeFormDefault());
        }

        @Test
        @DisplayName("should default XSD version to 1.0")
        void testDefaultXsdVersion() {
            XsdStatistics stats = collector.collect();
            assertEquals("1.0", stats.xsdVersion());
        }
    }

    // ========== Node Count Tests ==========

    @Nested
    @DisplayName("Node Count Collection")
    class NodeCountTests {

        @Test
        @DisplayName("should count elements")
        void testCountElements() {
            XsdElement element1 = new XsdElement();
            element1.setName("Element1");
            XsdElement element2 = new XsdElement();
            element2.setName("Element2");

            schema.addChild(element1);
            schema.addChild(element2);

            XsdStatistics stats = collector.collect();
            assertEquals(2, stats.getElementCount());
        }

        @Test
        @DisplayName("should count complex types")
        void testCountComplexTypes() {
            XsdComplexType complexType = new XsdComplexType("PersonType");
            schema.addChild(complexType);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.getComplexTypeCount());
        }

        @Test
        @DisplayName("should count simple types")
        void testCountSimpleTypes() {
            XsdSimpleType simpleType1 = new XsdSimpleType();
            simpleType1.setName("StringType");
            XsdSimpleType simpleType2 = new XsdSimpleType();
            simpleType2.setName("IntType");

            schema.addChild(simpleType1);
            schema.addChild(simpleType2);

            XsdStatistics stats = collector.collect();
            assertEquals(2, stats.getSimpleTypeCount());
        }

        @Test
        @DisplayName("should count sequences")
        void testCountSequences() {
            XsdComplexType complexType = new XsdComplexType("PersonType");
            XsdSequence sequence = new XsdSequence();
            complexType.addChild(sequence);
            schema.addChild(complexType);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.getNodeCount(XsdNodeType.SEQUENCE));
        }

        @Test
        @DisplayName("should count nested elements")
        void testCountNestedElements() {
            XsdComplexType complexType = new XsdComplexType("PersonType");

            XsdSequence sequence = new XsdSequence();
            XsdElement nestedElement = new XsdElement();
            nestedElement.setName("Name");
            sequence.addChild(nestedElement);

            complexType.addChild(sequence);
            schema.addChild(complexType);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.getElementCount());
            assertEquals(1, stats.getComplexTypeCount());
            assertEquals(1, stats.getNodeCount(XsdNodeType.SEQUENCE));
        }

        @Test
        @DisplayName("should calculate total nodes correctly")
        void testTotalNodeCount() {
            XsdElement element = new XsdElement();
            element.setName("Element1");
            XsdComplexType complexType = new XsdComplexType("Type1");

            schema.addChild(element);
            schema.addChild(complexType);

            XsdStatistics stats = collector.collect();
            // 1 schema + 1 element + 1 complexType = 3
            assertEquals(3, stats.totalNodeCount());
        }
    }

    // ========== Documentation Statistics Tests ==========

    @Nested
    @DisplayName("Documentation Statistics Collection")
    class DocumentationTests {

        @Test
        @DisplayName("should count nodes with documentation")
        void testCountNodesWithDocumentation() {
            XsdElement element = new XsdElement();
            element.setName("DocumentedElement");
            element.setDocumentation("This is a documented element");
            schema.addChild(element);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.nodesWithDocumentation());
        }

        @Test
        @DisplayName("should count nodes with appinfo")
        void testCountNodesWithAppInfo() {
            XsdElement element = new XsdElement();
            element.setName("AnnotatedElement");

            XsdAppInfo appInfo = new XsdAppInfo();
            appInfo.addEntry(null, "@since 1.0");
            element.setAppinfo(appInfo);

            schema.addChild(element);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.nodesWithAppInfo());
        }

        @Test
        @DisplayName("should count appinfo tags")
        void testCountAppInfoTags() {
            XsdElement element = new XsdElement();
            element.setName("AnnotatedElement");

            XsdAppInfo appInfo = new XsdAppInfo();
            appInfo.addEntry(null, "@since 1.0");
            appInfo.addEntry(null, "@deprecated Use NewElement instead");
            element.setAppinfo(appInfo);

            schema.addChild(element);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.appInfoTagCounts().getOrDefault("@since", 0));
            assertEquals(1, stats.appInfoTagCounts().getOrDefault("@deprecated", 0));
        }

        @Test
        @DisplayName("should calculate documentation coverage")
        void testDocumentationCoverage() {
            XsdElement documented = new XsdElement();
            documented.setName("DocumentedElement");
            documented.setDocumentation("Has docs");

            XsdElement undocumented = new XsdElement();
            undocumented.setName("UndocumentedElement");

            schema.addChild(documented);
            schema.addChild(undocumented);

            XsdStatistics stats = collector.collect();
            // 1 out of 2 elements documented = 50%
            assertEquals(50.0, stats.documentationCoveragePercent(), 0.1);
        }
    }

    // ========== Cardinality Statistics Tests ==========

    @Nested
    @DisplayName("Cardinality Statistics Collection")
    class CardinalityTests {

        @Test
        @DisplayName("should count optional elements (minOccurs=0)")
        void testCountOptionalElements() {
            XsdElement optional = new XsdElement();
            optional.setName("OptionalElement");
            optional.setMinOccurs(0);

            schema.addChild(optional);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.optionalElements());
        }

        @Test
        @DisplayName("should count required elements (minOccurs>=1)")
        void testCountRequiredElements() {
            XsdElement required = new XsdElement();
            required.setName("RequiredElement");
            required.setMinOccurs(1);

            schema.addChild(required);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.requiredElements());
        }

        @Test
        @DisplayName("should count unbounded elements (maxOccurs=-1)")
        void testCountUnboundedElements() {
            XsdElement unbounded = new XsdElement();
            unbounded.setName("UnboundedElement");
            unbounded.setMaxOccurs(-1); // UNBOUNDED

            schema.addChild(unbounded);

            XsdStatistics stats = collector.collect();
            assertEquals(1, stats.unboundedElements());
        }
    }

    // ========== Empty Schema Tests ==========

    @Nested
    @DisplayName("Empty Schema Handling")
    class EmptySchemaTests {

        @Test
        @DisplayName("should handle empty schema")
        void testEmptySchema() {
            XsdStatistics stats = collector.collect();

            // Only the schema node itself
            assertEquals(1, stats.totalNodeCount());
            assertEquals(0, stats.getElementCount());
            assertEquals(0, stats.getComplexTypeCount());
            assertEquals(0, stats.getSimpleTypeCount());
        }

        @Test
        @DisplayName("should have zero documentation coverage for empty schema")
        void testEmptySchemaDocumentationCoverage() {
            XsdStatistics stats = collector.collect();
            assertEquals(0.0, stats.documentationCoveragePercent(), 0.1);
        }
    }

    // ========== Metadata Tests ==========

    @Nested
    @DisplayName("Metadata Collection")
    class MetadataTests {

        @Test
        @DisplayName("should set collection timestamp")
        void testCollectionTimestamp() {
            XsdStatistics stats = collector.collect();
            assertNotNull(stats.collectedAt());
        }
    }

    // ========== Component usage & complexity (reference graph) ==========

    @Nested
    @DisplayName("Component Usage and Complexity")
    class ComponentUsageAndComplexityTests {

        private static final String XSD = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tns="urn:t" targetNamespace="urn:t">
                  <xs:element name="Root" type="tns:RootType" abstract="false"/>
                  <xs:element name="Head" type="xs:string" abstract="true"/>
                  <xs:element name="Member" type="xs:string" substitutionGroup="tns:Head"/>
                  <xs:complexType name="RootType" mixed="true">
                    <xs:sequence>
                      <xs:element name="a" type="tns:Once"/>
                      <xs:element name="b">
                        <xs:complexType><xs:sequence>
                          <xs:element name="c"><xs:complexType><xs:sequence><xs:element name="d" type="xs:string"/></xs:sequence></xs:complexType></xs:element>
                        </xs:sequence></xs:complexType>
                      </xs:element>
                      <xs:group ref="tns:UsedGroup"/>
                    </xs:sequence>
                    <xs:attribute name="id" type="xs:ID"/>
                  </xs:complexType>
                  <xs:complexType name="Base" abstract="true"><xs:sequence><xs:element name="x" type="xs:int"/></xs:sequence></xs:complexType>
                  <xs:complexType name="Level1"><xs:complexContent><xs:extension base="tns:Base"/></xs:complexContent></xs:complexType>
                  <xs:complexType name="Level2"><xs:complexContent><xs:extension base="tns:Level1"/></xs:complexContent></xs:complexType>
                  <xs:element name="Deep" type="tns:Level2"/>
                  <xs:simpleType name="Once"><xs:restriction base="xs:string"><xs:enumeration value="A"/><xs:enumeration value="B"/><xs:maxLength value="3"/></xs:restriction></xs:simpleType>
                  <xs:simpleType name="Orphan"><xs:restriction base="xs:string"><xs:pattern value="[a-z]+"/></xs:restriction></xs:simpleType>
                  <xs:group name="UsedGroup"><xs:sequence><xs:element name="g" type="xs:string"/></xs:sequence></xs:group>
                  <xs:group name="UnusedGroup"><xs:sequence><xs:element name="u" type="xs:string"/></xs:sequence></xs:group>
                  <xs:attributeGroup name="UnusedAG"><xs:attribute name="ua" type="xs:string"/></xs:attributeGroup>
                  <xs:complexType name="Node"><xs:sequence><xs:element name="child" type="tns:Node" minOccurs="0"/></xs:sequence></xs:complexType>
                </xs:schema>
                """;

        private XsdStatistics stats;

        @BeforeEach
        void collect() throws Exception {
            XsdSchema parsed = new XsdNodeFactory().fromString(XSD);
            stats = new XsdStatisticsCollector(parsed).collect();
        }

        @Test
        @DisplayName("reports unused groups, attribute groups and the cascading unreachable set")
        void componentUsage() {
            XsdStatistics.ComponentUsage usage = stats.componentUsage();
            assertEquals(java.util.Set.of("UnusedGroup"), usage.unusedGroups());
            assertEquals(java.util.Set.of("UnusedAG"), usage.unusedAttributeGroups());
            assertTrue(usage.unreachableComponents().contains("Simple type 'Orphan'"), usage.unreachableComponents().toString());
            assertTrue(usage.unreachableComponents().contains("Complex type 'Node'"));
            assertTrue(usage.unreachableComponents().contains("Group 'UnusedGroup'"));
            assertFalse(usage.unreachableComponents().contains("Complex type 'RootType'"));
            assertEquals(2, usage.unusedGroupCount());
            assertTrue(stats.unusedTypes().contains("Orphan"));
            assertFalse(stats.unusedTypes().contains("Node"), "self-referencing type counts as referenced");
        }

        @Test
        @DisplayName("lists single-use types and containment cycles")
        void singleUseAndCycles() {
            XsdStatistics.ComponentUsage usage = stats.componentUsage();
            assertTrue(usage.singleUseTypes().contains("Once"));
            assertTrue(usage.singleUseTypes().contains("Level2"));
            assertFalse(usage.singleUseTypes().contains("Node"), "self reference is not a use");
            assertEquals(java.util.List.of(java.util.List.of("Complex type 'Node'")), usage.containmentCycles());
            assertTrue(usage.derivationCycles().isEmpty());
        }

        @Test
        @DisplayName("computes nesting depth, derivation depth, abstract/substitution counts and facets")
        void complexity() {
            XsdStatistics.ComplexityMetrics c = stats.complexity();
            assertEquals(3, c.maxElementNestingDepth(), "Root? no — b > c > d is the deepest lexical chain");
            assertTrue(c.deepestElementXPath().endsWith("xs:element[@name='d']"), c.deepestElementXPath());
            assertEquals(2, c.maxTypeDerivationDepth(), "Level2 → Level1 → Base = two derivation steps");
            assertEquals(1, c.abstractTypes());
            assertEquals(1, c.abstractElements());
            assertEquals(1, c.substitutionGroupHeads());
            assertEquals(1, c.substitutionGroupMembers());
            assertEquals(2, c.anonymousComplexTypes());
            assertEquals(0, c.anonymousSimpleTypes());
            assertEquals(1, c.mixedContentTypes());
            assertEquals(2, c.extensions());
            assertEquals(2, c.restrictions());
            assertEquals(2, c.enumerationValues());
            assertEquals(2, c.facetCounts().get(XsdFacetType.ENUMERATION));
            assertEquals(1, c.facetCounts().get(XsdFacetType.MAX_LENGTH));
            assertEquals(1, c.facetCounts().get(XsdFacetType.PATTERN));
            assertEquals(4, c.totalFacets());
            assertEquals(0, c.xsd11Features());
            // RootType: a, b, group ref (not a declaration), id → 3 declarations; widest overall
            assertEquals(3, c.maxChildrenPerComplexType());
            assertEquals("RootType", c.widestComplexType());
            assertTrue(c.avgChildrenPerComplexType() > 0);
        }

        @Test
        @DisplayName("empty schema yields EMPTY records")
        void emptySchema() {
            XsdStatistics empty = new XsdStatisticsCollector(new XsdSchema()).collect();
            assertEquals(XsdStatistics.ComponentUsage.EMPTY, empty.componentUsage());
            assertEquals(0, empty.complexity().maxElementNestingDepth());
            assertEquals(0, empty.complexity().totalFacets());
        }
    }
}
