package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
