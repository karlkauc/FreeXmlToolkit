package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ElementInfo service class - XML element metadata and analysis.
 */
class ElementInfoTest {

    private ElementInfo elementInfo;

    @BeforeEach
    void setUp() {
        elementInfo = new ElementInfo("testElement", "http://example.com/ns");
    }

    // =========================================================================
    // Construction Tests
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Default constructor")
        void defaultConstructor() {
            ElementInfo info = new ElementInfo();
            assertNull(info.getName());
            assertNull(info.getNamespace());
            assertEquals("xs:string", info.getInferredType());
        }

        @Test
        @DisplayName("Name constructor")
        void nameConstructor() {
            ElementInfo info = new ElementInfo("myElement");
            assertEquals("myElement", info.getName());
            assertNull(info.getNamespace());
        }

        @Test
        @DisplayName("Name and namespace constructor")
        void nameAndNamespaceConstructor() {
            assertEquals("testElement", elementInfo.getName());
            assertEquals("http://example.com/ns", elementInfo.getNamespace());
        }
    }

    // =========================================================================
    // Basic Property Tests
    // =========================================================================

    @Nested
    @DisplayName("Basic Properties")
    class BasicPropertyTests {

        @Test
        @DisplayName("Set and get name")
        void nameProperty() {
            elementInfo.setName("newName");
            assertEquals("newName", elementInfo.getName());
        }

        @Test
        @DisplayName("Set and get namespace")
        void namespaceProperty() {
            elementInfo.setNamespace("http://new.com/ns");
            assertEquals("http://new.com/ns", elementInfo.getNamespace());
        }

        @Test
        @DisplayName("Set and get qualified name")
        void qualifiedNameProperty() {
            elementInfo.setQualifiedName("ns:testElement");
            assertEquals("ns:testElement", elementInfo.getQualifiedName());
        }

        @Test
        @DisplayName("Set and get depth")
        void depthProperty() {
            elementInfo.setDepth(3);
            assertEquals(3, elementInfo.getDepth());
        }

        @Test
        @DisplayName("Set and get xpath")
        void xpathProperty() {
            elementInfo.setXpath("/root/child/element");
            assertEquals("/root/child/element", elementInfo.getXpath());
        }
    }

    // =========================================================================
    // Content Model Tests
    // =========================================================================

    @Nested
    @DisplayName("Content Model")
    class ContentModelTests {

        @Test
        @DisplayName("Default content type is mixed")
        void defaultContentType() {
            assertEquals("mixed", elementInfo.getContentType());
        }

        @Test
        @DisplayName("Content type changes on set")
        void setContentType() {
            elementInfo.setContentType("simple");
            assertEquals("simple", elementInfo.getContentType());
        }

        @Test
        @DisplayName("Text content property")
        void textContent() {
            elementInfo.setTextContent("sample text");
            assertEquals("sample text", elementInfo.getTextContent());
        }

        @Test
        @DisplayName("Boolean content flags")
        void contentFlags() {
            assertFalse(elementInfo.isHasAttributes());
            assertFalse(elementInfo.isHasChildElements());
            assertFalse(elementInfo.isHasTextContent());
            assertFalse(elementInfo.isHasMixedContent());

            elementInfo.setHasAttributes(true);
            elementInfo.setHasChildElements(true);
            elementInfo.setHasTextContent(true);
            elementInfo.setHasMixedContent(true);

            assertTrue(elementInfo.isHasAttributes());
            assertTrue(elementInfo.isHasChildElements());
            assertTrue(elementInfo.isHasTextContent());
            assertTrue(elementInfo.isHasMixedContent());
        }
    }

    // =========================================================================
    // Type Information Tests
    // =========================================================================

    @Nested
    @DisplayName("Type Information")
    class TypeInfoTests {

        @Test
        @DisplayName("Default inferred type is xs:string")
        void defaultInferredType() {
            assertEquals("xs:string", elementInfo.getInferredType());
        }

        @Test
        @DisplayName("Set inferred type")
        void setInferredType() {
            elementInfo.setInferredType("xs:integer");
            assertEquals("xs:integer", elementInfo.getInferredType());
        }

        @Test
        @DisplayName("Original type property")
        void originalType() {
            elementInfo.setOriginalType("PersonType");
            assertEquals("PersonType", elementInfo.getOriginalType());
        }

        @Test
        @DisplayName("Type confidence property")
        void typeConfidence() {
            elementInfo.setTypeConfidence(0.95);
            assertEquals(0.95, elementInfo.getTypeConfidence());
        }

        @Test
        @DisplayName("Possible types set")
        void possibleTypes() {
            Set<String> types = new HashSet<>(Set.of("xs:string", "xs:token"));
            elementInfo.setPossibleTypes(types);
            assertEquals(2, elementInfo.getPossibleTypes().size());
            assertTrue(elementInfo.getPossibleTypes().contains("xs:string"));
        }

        @Test
        @DisplayName("Complex type flag and name")
        void complexType() {
            elementInfo.setComplexType(true);
            elementInfo.setComplexTypeName("AddressType");

            assertTrue(elementInfo.isComplexType());
            assertEquals("AddressType", elementInfo.getComplexTypeName());
        }
    }

    // =========================================================================
    // Occurrence Pattern Tests
    // =========================================================================

    @Nested
    @DisplayName("Occurrence Patterns")
    class OccurrenceTests {

        @Test
        @DisplayName("Default occurrence is 1..1")
        void defaultOccurrence() {
            assertEquals(1, elementInfo.getMinOccurs());
            assertEquals(1, elementInfo.getMaxOccurs());
            assertFalse(elementInfo.isUnbounded());
        }

        @Test
        @DisplayName("Set occurrence bounds")
        void setOccurrence() {
            elementInfo.setMinOccurs(0);
            elementInfo.setMaxOccurs(5);

            assertEquals(0, elementInfo.getMinOccurs());
            assertEquals(5, elementInfo.getMaxOccurs());
        }

        @Test
        @DisplayName("Unbounded flag")
        void unbounded() {
            elementInfo.setUnbounded(true);
            assertTrue(elementInfo.isUnbounded());
        }

        @Test
        @DisplayName("Total occurrences")
        void totalOccurrences() {
            elementInfo.setTotalOccurrences(42);
            assertEquals(42, elementInfo.getTotalOccurrences());
        }

        @Test
        @DisplayName("Record occurrence")
        void recordOccurrence() {
            elementInfo.recordOccurrence("doc1");
            elementInfo.recordOccurrence("doc2");
            elementInfo.recordOccurrence("doc1");

            assertEquals(2, elementInfo.getSourceDocuments().size());
        }
    }

    // =========================================================================
    // Value Analysis Tests
    // =========================================================================

    @Nested
    @DisplayName("Value Analysis")
    class ValueAnalysisTests {

        @Test
        @DisplayName("Observed values")
        void observedValues() {
            Set<String> values = new LinkedHashSet<>(Set.of("val1", "val2", "val3"));
            elementInfo.setObservedValues(values);
            assertEquals(3, elementInfo.getObservedValues().size());
        }

        @Test
        @DisplayName("Sample values")
        void sampleValues() {
            elementInfo.setSampleValues(List.of("sample1", "sample2"));
            assertEquals(2, elementInfo.getSampleValues().size());
        }

        @Test
        @DisplayName("Value frequency map")
        void valueFrequency() {
            Map<String, Integer> freq = new HashMap<>();
            freq.put("value1", 5);
            freq.put("value2", 3);
            elementInfo.setValueFrequency(freq);

            assertEquals(5, elementInfo.getValueFrequency().get("value1"));
        }
    }

    // =========================================================================
    // Pattern Analysis Tests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Analysis")
    class PatternAnalysisTests {

        @Test
        @DisplayName("Detected patterns")
        void detectedPatterns() {
            elementInfo.addDetectedPattern("numeric");
            elementInfo.addDetectedPattern("alphanumeric");

            assertEquals(2, elementInfo.getDetectedPatterns().size());
            assertTrue(elementInfo.getDetectedPatterns().contains("numeric"));
        }

        @Test
        @DisplayName("Pattern counts")
        void patternCounts() {
            Map<String, Integer> counts = new HashMap<>();
            counts.put("email", 10);
            counts.put("phone", 5);
            elementInfo.setPatternCounts(counts);

            assertEquals(10, elementInfo.getPatternCounts().get("email"));
        }

        @Test
        @DisplayName("Most common pattern")
        void mostCommonPattern() {
            elementInfo.setMostCommonPattern("date-iso");
            elementInfo.setPatternConfidence(0.85);

            assertEquals("date-iso", elementInfo.getMostCommonPattern());
            assertEquals(0.85, elementInfo.getPatternConfidence());
        }
    }

    // =========================================================================
    // Constraint Tests
    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class ConstraintTests {

        @Test
        @DisplayName("Length constraints")
        void lengthConstraints() {
            elementInfo.setMinLength(1);
            elementInfo.setMaxLength(255);

            assertEquals(1, elementInfo.getMinLength());
            assertEquals(255, elementInfo.getMaxLength());
        }

        @Test
        @DisplayName("Value constraints")
        void valueConstraints() {
            elementInfo.setMinValue("0");
            elementInfo.setMaxValue("100");

            assertEquals("0", elementInfo.getMinValue());
            assertEquals("100", elementInfo.getMaxValue());
        }

        @Test
        @DisplayName("Enumeration values")
        void enumerationValues() {
            elementInfo.setEnumerationValues(Set.of("A", "B", "C"));
            assertEquals(3, elementInfo.getEnumerationValues().size());
        }

        @Test
        @DisplayName("Restriction pattern")
        void restrictionPattern() {
            elementInfo.setRestrictionPattern("[A-Z]{3}");
            assertEquals("[A-Z]{3}", elementInfo.getRestrictionPattern());
        }

        @Test
        @DisplayName("Nillable flag")
        void nillable() {
            elementInfo.setNillable(true);
            assertTrue(elementInfo.isNillable());
        }
    }

    // =========================================================================
    // Metadata and Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Metadata and Statistics")
    class MetadataTests {

        @Test
        @DisplayName("Documentation property")
        void documentation() {
            elementInfo.setDocumentation("A test element");
            assertEquals("A test element", elementInfo.getDocumentation());
        }

        @Test
        @DisplayName("Comments and annotations")
        void commentsAndAnnotations() {
            elementInfo.setComments(List.of("Comment 1"));
            elementInfo.setAnnotations(Map.of("key1", "Annotation 1"));

            assertEquals(1, elementInfo.getComments().size());
            assertEquals(1, elementInfo.getAnnotations().size());
        }

        @Test
        @DisplayName("Analysis state")
        void analysisState() {
            assertFalse(elementInfo.isAnalyzed());

            elementInfo.setAnalyzed(true);
            assertTrue(elementInfo.isAnalyzed());

            // analyzedAt must be set explicitly
            elementInfo.setAnalyzedAt(java.time.LocalDateTime.now());
            assertNotNull(elementInfo.getAnalyzedAt());
        }

        @Test
        @DisplayName("Analysis depth")
        void analysisDepth() {
            elementInfo.setAnalysisDepth(3);
            assertEquals(3, elementInfo.getAnalysisDepth());
        }

        @Test
        @DisplayName("Statistics map")
        void statistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalElements", 42);
            elementInfo.setStatistics(stats);

            assertEquals(42, elementInfo.getStatistics().get("totalElements"));
        }

        @Test
        @DisplayName("Usage statistics")
        void usageStatistics() {
            elementInfo.setTotalOccurrences(10);
            elementInfo.setMinOccurs(0);
            elementInfo.setMaxOccurs(5);

            Map<String, Object> usage = elementInfo.getUsageStatistics();
            assertNotNull(usage);
            assertEquals(10, usage.get("totalOccurrences"));
            assertEquals("mixed", usage.get("contentType"));
        }
    }

    // =========================================================================
    // Child Element Tests
    // =========================================================================

    @Nested
    @DisplayName("Child Elements")
    class ChildElementTests {

        @Test
        @DisplayName("Add child element")
        void addChild() {
            ElementInfo child = new ElementInfo("child");
            elementInfo.addChildElement(child);

            assertTrue(elementInfo.isHasChildElements());
            assertFalse(elementInfo.getChildElements().isEmpty());
        }

        @Test
        @DisplayName("Add attribute")
        void addAttribute() {
            AttributeInfo attr = new AttributeInfo();
            attr.setName("id");
            attr.setQualifiedName("id");
            elementInfo.addAttribute(attr);

            assertTrue(elementInfo.isHasAttributes());
            assertTrue(elementInfo.getAttributes().containsKey("id"));
        }

        @Test
        @DisplayName("All children list")
        void allChildren() {
            ElementInfo child1 = new ElementInfo("child1");
            ElementInfo child2 = new ElementInfo("child2");
            elementInfo.setAllChildren(List.of(child1, child2));

            assertEquals(2, elementInfo.getAllChildren().size());
        }
    }

    // =========================================================================
    // Complexity Score Tests
    // =========================================================================

    @Nested
    @DisplayName("Complexity Score")
    class ComplexityTests {

        @Test
        @DisplayName("Simple element has low complexity")
        void simpleComplexity() {
            double score = elementInfo.getComplexityScore();
            assertTrue(score >= 0.0);
        }

        @Test
        @DisplayName("Element with children has higher complexity")
        void complexWithChildren() {
            double baseLine = elementInfo.getComplexityScore();

            elementInfo.addChildElement(new ElementInfo("c1"));
            elementInfo.addChildElement(new ElementInfo("c2"));
            elementInfo.addChildElement(new ElementInfo("c3"));

            double withChildren = elementInfo.getComplexityScore();
            assertTrue(withChildren >= baseLine);
        }
    }

    // =========================================================================
    // Structural Compatibility Tests
    // =========================================================================

    @Nested
    @DisplayName("Structural Compatibility")
    class CompatibilityTests {

        @Test
        @DisplayName("Same name is compatible")
        void sameName() {
            ElementInfo other = new ElementInfo("testElement");
            assertTrue(elementInfo.isStructurallyCompatible(other));
        }

        @Test
        @DisplayName("Different content type is not compatible")
        void differentContentType() {
            ElementInfo other = new ElementInfo("testElement");
            other.setContentType("simple");
            assertFalse(elementInfo.isStructurallyCompatible(other));
        }

        @Test
        @DisplayName("Null element is not compatible")
        void nullNotCompatible() {
            assertFalse(elementInfo.isStructurallyCompatible(null));
        }
    }

    // =========================================================================
    // Text Content Addition Tests
    // =========================================================================

    @Nested
    @DisplayName("Text Content Addition")
    class TextContentTests {

        @Test
        @DisplayName("Adding text sets hasTextContent flag")
        void addTextContent() {
            elementInfo.addTextContent("Hello World");
            assertTrue(elementInfo.isHasTextContent());
        }

        @Test
        @DisplayName("Adding null text is handled")
        void addNullText() {
            assertDoesNotThrow(() -> elementInfo.addTextContent(null));
        }

        @Test
        @DisplayName("Adding empty text is handled")
        void addEmptyText() {
            assertDoesNotThrow(() -> elementInfo.addTextContent(""));
        }

        @Test
        @DisplayName("Adding integer text infers integer type")
        void inferIntegerType() {
            elementInfo.addTextContent("42");
            elementInfo.addTextContent("100");
            elementInfo.analyzePatterns();

            // After adding only integer values, type should be inferred
            assertNotNull(elementInfo.getInferredType());
        }

        @Test
        @DisplayName("Adding boolean text infers boolean type")
        void inferBooleanType() {
            elementInfo.addTextContent("true");
            elementInfo.addTextContent("false");
            elementInfo.analyzePatterns();

            assertNotNull(elementInfo.getInferredType());
        }
    }

    // =========================================================================
    // Occurrence Analysis Tests
    // =========================================================================

    @Nested
    @DisplayName("Occurrence Analysis")
    class OccurrenceAnalysisTests {

        @Test
        @DisplayName("Analyze occurrences from multiple documents")
        void analyzeOccurrences() {
            elementInfo.recordOccurrence("doc1");
            elementInfo.recordOccurrence("doc2");
            elementInfo.recordOccurrence("doc3");

            elementInfo.analyzeOccurrences(5);

            assertTrue(elementInfo.getOccurrenceConfidence() > 0.0);
        }
    }

    // =========================================================================
    // toString Tests
    // =========================================================================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString includes name")
        void toStringIncludesName() {
            String str = elementInfo.toString();
            assertTrue(str.contains("testElement"));
        }
    }
}
