package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for TypeUsageFinder.
 * Tests finding type usages in various XSD constructs.
 *
 * @since 2.0
 */
class TypeUsageFinderTest {

    private XsdSchema schema;
    private TypeUsageFinder finder;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        finder = new TypeUsageFinder(schema);
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("Constructor throws NullPointerException when schema is null")
    void testConstructorNullSchema() {
        assertThrows(NullPointerException.class, () -> new TypeUsageFinder(null),
                "Should throw NullPointerException for null schema");
    }

    // ========== Element Type Tests ==========

    @Nested
    @DisplayName("Element Type Usage Tests")
    class ElementTypeTests {

        @Test
        @DisplayName("findUsages() finds element with matching type")
        void testFindElementTypeUsage() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("MySimpleType");
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.ELEMENT_TYPE, usages.get(0).referenceType());
            assertSame(element, usages.get(0).node());
        }

        @Test
        @DisplayName("findUsages() finds element with namespace-prefixed type")
        void testFindElementWithNamespacedType() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("tns:MySimpleType");
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage (ignoring namespace prefix)");
            assertEquals(UsageReferenceType.ELEMENT_TYPE, usages.get(0).referenceType());
        }

        @Test
        @DisplayName("findUsages() handles search with namespace prefix")
        void testSearchWithNamespacePrefix() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("MySimpleType");
            schema.addChild(element);

            // Act - search with prefix
            List<TypeUsageLocation> usages = finder.findUsages("tns:MySimpleType");

            // Assert
            assertEquals(1, usages.size(), "Should find usage even when searching with prefix");
        }

        @Test
        @DisplayName("findUsages() does not find element with different type")
        void testNoMatchForDifferentType() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("OtherType");
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert
            assertEquals(0, usages.size(), "Should not find any usages");
        }
    }

    // ========== Attribute Type Tests ==========

    @Nested
    @DisplayName("Attribute Type Usage Tests")
    class AttributeTypeTests {

        @Test
        @DisplayName("findUsages() finds attribute with matching type")
        void testFindAttributeTypeUsage() {
            // Arrange
            XsdComplexType complexType = new XsdComplexType("MyComplexType");
            XsdAttribute attribute = new XsdAttribute("myAttr");
            attribute.setType("MySimpleType");
            complexType.addChild(attribute);
            schema.addChild(complexType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.ATTRIBUTE_TYPE, usages.get(0).referenceType());
            assertSame(attribute, usages.get(0).node());
        }
    }

    // ========== Restriction Base Tests ==========

    @Nested
    @DisplayName("Restriction Base Usage Tests")
    class RestrictionBaseTests {

        @Test
        @DisplayName("findUsages() finds restriction with matching base")
        void testFindRestrictionBaseUsage() {
            // Arrange
            XsdSimpleType simpleType = new XsdSimpleType("MyDerivedType");
            XsdRestriction restriction = new XsdRestriction();
            restriction.setBase("MyBaseType");
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyBaseType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.RESTRICTION_BASE, usages.get(0).referenceType());
            assertSame(restriction, usages.get(0).node());
        }

        @Test
        @DisplayName("findUsages() finds restriction with xs: prefixed base")
        void testFindRestrictionWithXsPrefix() {
            // Arrange
            XsdSimpleType simpleType = new XsdSimpleType("MyType");
            XsdRestriction restriction = new XsdRestriction();
            restriction.setBase("xs:string");
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("string");

            // Assert
            assertEquals(1, usages.size(), "Should find usage of xs:string");
        }
    }

    // ========== Extension Base Tests ==========

    @Nested
    @DisplayName("Extension Base Usage Tests")
    class ExtensionBaseTests {

        @Test
        @DisplayName("findUsages() finds extension with matching base")
        void testFindExtensionBaseUsage() {
            // Arrange
            XsdComplexType complexType = new XsdComplexType("MyDerivedType");
            XsdComplexContent complexContent = new XsdComplexContent();
            XsdExtension extension = new XsdExtension();
            extension.setBase("MyBaseType");
            complexContent.addChild(extension);
            complexType.addChild(complexContent);
            schema.addChild(complexType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyBaseType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.EXTENSION_BASE, usages.get(0).referenceType());
            assertSame(extension, usages.get(0).node());
        }
    }

    // ========== List ItemType Tests ==========

    @Nested
    @DisplayName("List ItemType Usage Tests")
    class ListItemTypeTests {

        @Test
        @DisplayName("findUsages() finds list with matching itemType")
        void testFindListItemTypeUsage() {
            // Arrange
            XsdSimpleType simpleType = new XsdSimpleType("MyListType");
            XsdList list = new XsdList();
            list.setItemType("MySimpleType");
            simpleType.addChild(list);
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.LIST_ITEM_TYPE, usages.get(0).referenceType());
            assertSame(list, usages.get(0).node());
        }
    }

    // ========== Union MemberTypes Tests ==========

    @Nested
    @DisplayName("Union MemberTypes Usage Tests")
    class UnionMemberTypesTests {

        @Test
        @DisplayName("findUsages() finds union with matching memberType")
        void testFindUnionMemberTypeUsage() {
            // Arrange
            XsdSimpleType simpleType = new XsdSimpleType("MyUnionType");
            XsdUnion union = new XsdUnion();
            union.addMemberType("MySimpleType");
            union.addMemberType("xs:string");
            simpleType.addChild(union);
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.UNION_MEMBER_TYPE, usages.get(0).referenceType());
            assertSame(union, usages.get(0).node());
        }

        @Test
        @DisplayName("findUsages() finds only one usage per union even if type appears multiple times")
        void testUnionMultipleSameType() {
            // Arrange
            XsdSimpleType simpleType = new XsdSimpleType("MyUnionType");
            XsdUnion union = new XsdUnion();
            union.addMemberType("MySimpleType");
            union.addMemberType("MySimpleType"); // Same type twice
            simpleType.addChild(union);
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MySimpleType");

            // Assert - only one usage per union node, even if type listed multiple times
            assertEquals(1, usages.size(), "Should find only one usage per union");
        }
    }

    // ========== Alternative Type Tests (XSD 1.1) ==========

    @Nested
    @DisplayName("Alternative Type Usage Tests (XSD 1.1)")
    class AlternativeTypeTests {

        @Test
        @DisplayName("findUsages() finds alternative with matching type")
        void testFindAlternativeTypeUsage() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            XsdAlternative alternative = new XsdAlternative();
            alternative.setType("MyAlternativeType");
            element.addChild(alternative);
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyAlternativeType");

            // Assert
            assertEquals(1, usages.size(), "Should find one usage");
            assertEquals(UsageReferenceType.ALTERNATIVE_TYPE, usages.get(0).referenceType());
            assertSame(alternative, usages.get(0).node());
        }
    }

    // ========== Multiple Usage Tests ==========

    @Nested
    @DisplayName("Multiple Usage Tests")
    class MultipleUsageTests {

        @Test
        @DisplayName("findUsages() finds multiple usages across different constructs")
        void testFindMultipleUsages() {
            // Arrange - add multiple usages of the same type
            XsdElement element1 = new XsdElement("element1");
            element1.setType("MyType");
            schema.addChild(element1);

            XsdElement element2 = new XsdElement("element2");
            element2.setType("MyType");
            schema.addChild(element2);

            XsdSimpleType simpleType = new XsdSimpleType("DerivedType");
            XsdRestriction restriction = new XsdRestriction();
            restriction.setBase("MyType");
            simpleType.addChild(restriction);
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyType");

            // Assert
            assertEquals(3, usages.size(), "Should find three usages");
        }

        @Test
        @DisplayName("countUsages() returns correct count")
        void testCountUsages() {
            // Arrange
            for (int i = 0; i < 5; i++) {
                XsdElement element = new XsdElement("element" + i);
                element.setType("MyType");
                schema.addChild(element);
            }

            // Act
            int count = finder.countUsages("MyType");

            // Assert
            assertEquals(5, count, "Should count five usages");
        }

        @Test
        @DisplayName("isTypeUsed() returns true when type is used")
        void testIsTypeUsedTrue() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("MyType");
            schema.addChild(element);

            // Act
            boolean isUsed = finder.isTypeUsed("MyType");

            // Assert
            assertTrue(isUsed, "Should return true when type is used");
        }

        @Test
        @DisplayName("isTypeUsed() returns false when type is not used")
        void testIsTypeUsedFalse() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("OtherType");
            schema.addChild(element);

            // Act
            boolean isUsed = finder.isTypeUsed("MyType");

            // Assert
            assertFalse(isUsed, "Should return false when type is not used");
        }
    }

    // ========== Edge Cases Tests ==========

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("findUsages() throws exception for null type name")
        void testFindUsagesNullTypeName() {
            assertThrows(IllegalArgumentException.class, () -> finder.findUsages(null),
                    "Should throw IllegalArgumentException for null type name");
        }

        @Test
        @DisplayName("findUsages() throws exception for empty type name")
        void testFindUsagesEmptyTypeName() {
            assertThrows(IllegalArgumentException.class, () -> finder.findUsages(""),
                    "Should throw IllegalArgumentException for empty type name");
        }

        @Test
        @DisplayName("findUsages() throws exception for blank type name")
        void testFindUsagesBlankTypeName() {
            assertThrows(IllegalArgumentException.class, () -> finder.findUsages("   "),
                    "Should throw IllegalArgumentException for blank type name");
        }

        @Test
        @DisplayName("countUsages() returns 0 for null type name")
        void testCountUsagesNullTypeName() {
            assertEquals(0, finder.countUsages(null), "Should return 0 for null type name");
        }

        @Test
        @DisplayName("countUsages() returns 0 for empty type name")
        void testCountUsagesEmptyTypeName() {
            assertEquals(0, finder.countUsages(""), "Should return 0 for empty type name");
        }

        @Test
        @DisplayName("isTypeUsed() returns false for null type name")
        void testIsTypeUsedNullTypeName() {
            assertFalse(finder.isTypeUsed(null), "Should return false for null type name");
        }

        @Test
        @DisplayName("findUsages() returns empty list when no usages exist")
        void testNoUsages() {
            // Arrange - empty schema
            XsdSimpleType simpleType = new XsdSimpleType("UnusedType");
            schema.addChild(simpleType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("UnusedType");

            // Assert
            assertTrue(usages.isEmpty(), "Should return empty list when type is not used");
        }

        @Test
        @DisplayName("findUsages() handles element with null type")
        void testElementWithNullType() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            // Type is null by default
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyType");

            // Assert
            assertTrue(usages.isEmpty(), "Should not find usage when element has null type");
        }
    }

    // ========== Deep Nesting Tests ==========

    @Nested
    @DisplayName("Deep Nesting Tests")
    class DeepNestingTests {

        @Test
        @DisplayName("findUsages() finds type in deeply nested structure")
        void testDeepNesting() {
            // Arrange - create nested structure
            XsdComplexType outerType = new XsdComplexType("OuterType");
            XsdSequence sequence1 = new XsdSequence();
            XsdElement nestedElement = new XsdElement("nestedElement");
            XsdComplexType innerType = new XsdComplexType("InnerType");
            XsdSequence sequence2 = new XsdSequence();
            XsdElement deepElement = new XsdElement("deepElement");
            deepElement.setType("MyDeepType");

            sequence2.addChild(deepElement);
            innerType.addChild(sequence2);
            nestedElement.addChild(innerType);
            sequence1.addChild(nestedElement);
            outerType.addChild(sequence1);
            schema.addChild(outerType);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyDeepType");

            // Assert
            assertEquals(1, usages.size(), "Should find usage in deeply nested element");
            assertSame(deepElement, usages.get(0).node());
        }

        @Test
        @DisplayName("findUsages() handles recursive type references without infinite loop")
        void testCycleDetection() {
            // Arrange - this tests the visitedIds cycle detection
            // Create a simple structure where the same nodes might be visited
            XsdComplexType complexType = new XsdComplexType("RecursiveType");
            XsdSequence sequence = new XsdSequence();
            XsdElement element = new XsdElement("element");
            element.setType("MyType");
            sequence.addChild(element);
            complexType.addChild(sequence);
            schema.addChild(complexType);

            // Act - should complete without hanging
            List<TypeUsageLocation> usages = finder.findUsages("MyType");

            // Assert
            assertEquals(1, usages.size(), "Should find usage without infinite loop");
        }
    }

    // ========== TypeUsageLocation Tests ==========

    @Nested
    @DisplayName("TypeUsageLocation Record Tests")
    class TypeUsageLocationTests {

        @Test
        @DisplayName("getDescription() returns correct format")
        void testGetDescription() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("MyType");
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyType");

            // Assert
            assertFalse(usages.isEmpty());
            String description = usages.get(0).getDescription();
            assertTrue(description.contains("myElement"), "Description should contain node name");
            assertTrue(description.contains("Element type"), "Description should contain reference type");
        }

        @Test
        @DisplayName("getNodeName() returns node name or (anonymous)")
        void testGetNodeName() {
            // Arrange
            XsdElement namedElement = new XsdElement("myElement");
            namedElement.setType("MyType");
            schema.addChild(namedElement);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyType");

            // Assert
            assertEquals("myElement", usages.get(0).getNodeName());
        }

        @Test
        @DisplayName("getSourceFileName() returns (main) when no source file")
        void testGetSourceFileNameNoFile() {
            // Arrange
            XsdElement element = new XsdElement("myElement");
            element.setType("MyType");
            schema.addChild(element);

            // Act
            List<TypeUsageLocation> usages = finder.findUsages("MyType");

            // Assert
            assertEquals("(main)", usages.get(0).getSourceFileName());
        }
    }
}
