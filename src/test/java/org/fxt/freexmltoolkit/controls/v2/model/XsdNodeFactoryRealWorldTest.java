package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-world integration tests for XsdNodeFactory using actual XSD files.
 *
 * @since 2.0
 */
class XsdNodeFactoryRealWorldTest {

    private static final Path XSD_DIR = Paths.get("release/examples/xsd");

    @Test
    void testParsePurchaseOrderSchema() throws Exception {
        File xsdFile = XSD_DIR.resolve("purchageOrder.xsd").toFile();
        assertTrue(xsdFile.exists(), "Test XSD file not found");

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdFile);

        assertNotNull(schema);
        assertEquals("http://tempuri.org", schema.getTargetNamespace());
        assertEquals("qualified", schema.getElementFormDefault());

        // Schema should contain documentation
        assertEquals("ship to documentation", schema.getDocumentation());

        // Find PurchaseOrder element (should be first child)
        XsdElement purchaseOrderElement = (XsdElement) schema.getChildren().stream()
                .filter(n -> n instanceof XsdElement && "PurchaseOrder".equals(((XsdElement) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(purchaseOrderElement, "PurchaseOrder element not found");
        assertEquals("PurchaseOrderType", purchaseOrderElement.getType());

        // Find PurchaseOrderType complexType
        XsdComplexType purchaseOrderType = (XsdComplexType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType && "PurchaseOrderType".equals(((XsdComplexType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(purchaseOrderType, "PurchaseOrderType not found");

        // Check for sequence in PurchaseOrderType
        XsdSequence sequence = (XsdSequence) purchaseOrderType.getChildren().stream()
                .filter(n -> n instanceof XsdSequence)
                .findFirst()
                .orElse(null);

        assertNotNull(sequence, "Sequence not found in PurchaseOrderType");
        assertTrue(sequence.getChildren().size() >= 3, "Sequence should have at least 3 children");

        // Check for attribute in PurchaseOrderType
        XsdAttribute orderDateAttr = (XsdAttribute) purchaseOrderType.getChildren().stream()
                .filter(n -> n instanceof XsdAttribute && "OrderDate".equals(((XsdAttribute) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(orderDateAttr, "OrderDate attribute not found");
        assertEquals("xsd:date", orderDateAttr.getType());

        // Find Address complexType (should be abstract)
        XsdComplexType addressType = (XsdComplexType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType && "Address".equals(((XsdComplexType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(addressType, "Address complexType not found");
        assertTrue(addressType.isAbstract(), "Address should be abstract");

        // Find USAddress complexType with extension
        XsdComplexType usAddressType = (XsdComplexType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdComplexType && "USAddress".equals(((XsdComplexType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(usAddressType, "USAddress complexType not found");

        // Check for complexContent and extension
        XsdComplexContent complexContent = (XsdComplexContent) usAddressType.getChildren().stream()
                .filter(n -> n instanceof XsdComplexContent)
                .findFirst()
                .orElse(null);

        assertNotNull(complexContent, "ComplexContent not found in USAddress");

        XsdExtension extension = (XsdExtension) complexContent.getChildren().stream()
                .filter(n -> n instanceof XsdExtension)
                .findFirst()
                .orElse(null);

        assertNotNull(extension, "Extension not found in ComplexContent");
        assertEquals("Address", extension.getBase());

        // Check attribute with fixed value
        XsdAttribute countryAttr = (XsdAttribute) usAddressType.getChildren().stream()
                .flatMap(n -> n.getChildren().stream())  // Go deeper into structure
                .filter(n -> n instanceof XsdAttribute && "country".equals(((XsdAttribute) n).getName()))
                .findFirst()
                .orElse(null);

        if (countryAttr != null) {
            assertEquals("US", countryAttr.getFixed());
        }

        // Find stateType simpleType with enumeration
        XsdSimpleType stateType = (XsdSimpleType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType && "stateType".equals(((XsdSimpleType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(stateType, "stateType simpleType not found");

        XsdRestriction stateRestriction = (XsdRestriction) stateType.getChildren().stream()
                .filter(n -> n instanceof XsdRestriction)
                .findFirst()
                .orElse(null);

        assertNotNull(stateRestriction, "Restriction not found in stateType");
        assertTrue(stateRestriction.hasFacet(XsdFacetType.ENUMERATION), "stateType should have enumeration facets");

        // Find zipIntType with minInclusive and maxExclusive
        XsdSimpleType zipIntType = (XsdSimpleType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType && "zipIntType".equals(((XsdSimpleType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(zipIntType, "zipIntType simpleType not found");

        XsdRestriction zipRestriction = (XsdRestriction) zipIntType.getChildren().stream()
                .filter(n -> n instanceof XsdRestriction)
                .findFirst()
                .orElse(null);

        assertNotNull(zipRestriction, "Restriction not found in zipIntType");
        assertTrue(zipRestriction.hasFacet(XsdFacetType.MIN_INCLUSIVE), "zipIntType should have minInclusive");
        assertTrue(zipRestriction.hasFacet(XsdFacetType.MAX_EXCLUSIVE), "zipIntType should have maxExclusive");

        // Find ItemsList with list
        XsdSimpleType itemsListType = (XsdSimpleType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType && "ItemsList".equals(((XsdSimpleType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(itemsListType, "ItemsList simpleType not found");

        XsdList list = (XsdList) itemsListType.getChildren().stream()
                .filter(n -> n instanceof XsdList)
                .findFirst()
                .orElse(null);

        assertNotNull(list, "List not found in ItemsList");

        // Find zipUnion with union
        XsdSimpleType zipUnionType = (XsdSimpleType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType && "zipUnion".equals(((XsdSimpleType) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(zipUnionType, "zipUnion simpleType not found");

        XsdUnion union = (XsdUnion) zipUnionType.getChildren().stream()
                .filter(n -> n instanceof XsdUnion)
                .findFirst()
                .orElse(null);

        assertNotNull(union, "Union not found in zipUnion");
        assertEquals(2, union.getMemberTypes().size(), "Union should have 2 member types");
        assertTrue(union.getMemberTypes().contains("stateType"), "Union should contain stateType");
        assertTrue(union.getMemberTypes().contains("zipIntType"), "Union should contain zipIntType");

        System.out.println("✅ Successfully parsed purchaseOrder.xsd with " + schema.getChildren().size() + " top-level components");
    }

    @Test
    void testParseContextSensitiveDemo() throws Exception {
        File xsdFile = XSD_DIR.resolve("context-sensitive-demo.xsd").toFile();
        assertTrue(xsdFile.exists(), "Test XSD file not found");

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdFile);

        assertNotNull(schema);
        assertFalse(schema.getChildren().isEmpty(), "Schema should have children");

        System.out.println("✅ Successfully parsed context-sensitive-demo.xsd with " + schema.getChildren().size() + " top-level components");
    }

    @Test
    void testParseXmlDsigCoreSchema() throws Exception {
        File xsdFile = XSD_DIR.resolve("xmldsig-core-schema.xsd").toFile();
        assertTrue(xsdFile.exists(), "Test XSD file not found");

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdFile);

        assertNotNull(schema);
        assertEquals("http://www.w3.org/2000/09/xmldsig#", schema.getTargetNamespace());
        assertFalse(schema.getChildren().isEmpty(), "Schema should have children");

        System.out.println("✅ Successfully parsed xmldsig-core-schema.xsd with " + schema.getChildren().size() + " top-level components");
    }

    @Test
    void testParseFundsXML4WithImport() throws Exception {
        File xsdFile = XSD_DIR.resolve("FundsXML4.xsd").toFile();
        assertTrue(xsdFile.exists(), "FundsXML4.xsd file not found");

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdFile);

        assertNotNull(schema, "Schema should be parsed");
        assertEquals("qualified", schema.getElementFormDefault());
        assertEquals("unqualified", schema.getAttributeFormDefault());

        // Find the xs:import statement
        XsdImport xmldsigImport = (XsdImport) schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .findFirst()
                .orElse(null);

        assertNotNull(xmldsigImport, "xs:import should be found in FundsXML4.xsd");
        assertEquals("http://www.w3.org/2000/09/xmldsig#", xmldsigImport.getNamespace(),
                    "Import namespace should be xmldsig");
        assertEquals("xmldsig-core-schema.xsd", xmldsigImport.getSchemaLocation(),
                    "Import schemaLocation should be xmldsig-core-schema.xsd");

        // Find FundsXML4 root element
        XsdElement fundsXml4Element = (XsdElement) schema.getChildren().stream()
                .filter(n -> n instanceof XsdElement && "FundsXML4".equals(((XsdElement) n).getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(fundsXml4Element, "FundsXML4 element should be found");

        System.out.println("✅ Successfully parsed FundsXML4.xsd with import statement");
        System.out.println("   - Import namespace: " + xmldsigImport.getNamespace());
        System.out.println("   - Import schemaLocation: " + xmldsigImport.getSchemaLocation());
        System.out.println("   - Total components: " + schema.getChildren().size());
    }
}
