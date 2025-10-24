package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Xsd11BuiltinTypes class
 */
@DisplayName("XSD 1.1 Built-in Types Tests")
class Xsd11BuiltinTypesTest {

    @Test
    @DisplayName("Should identify XSD 1.1 specific types")
    void testIsXsd11Type() {
        assertTrue(Xsd11BuiltinTypes.isXsd11Type("xs:dateTimeStamp"));
        assertTrue(Xsd11BuiltinTypes.isXsd11Type("xs:yearMonthDuration"));
        assertTrue(Xsd11BuiltinTypes.isXsd11Type("xs:dayTimeDuration"));
        assertTrue(Xsd11BuiltinTypes.isXsd11Type("xs:precisionDecimal"));
    }

    @Test
    @DisplayName("Should not identify XSD 1.0 types as XSD 1.1 types")
    void testIsNotXsd11Type() {
        assertFalse(Xsd11BuiltinTypes.isXsd11Type("xs:string"));
        assertFalse(Xsd11BuiltinTypes.isXsd11Type("xs:integer"));
        assertFalse(Xsd11BuiltinTypes.isXsd11Type("xs:dateTime"));
        assertFalse(Xsd11BuiltinTypes.isXsd11Type("xs:duration"));
    }

    @Test
    @DisplayName("Should handle type names with and without prefix")
    void testGetTypeInfoWithAndWithoutPrefix() {
        assertNotNull(Xsd11BuiltinTypes.getTypeInfo("xs:dateTimeStamp"));
        assertNotNull(Xsd11BuiltinTypes.getTypeInfo("dateTimeStamp"));

        Xsd11BuiltinTypes.TypeInfo info1 = Xsd11BuiltinTypes.getTypeInfo("xs:string");
        Xsd11BuiltinTypes.TypeInfo info2 = Xsd11BuiltinTypes.getTypeInfo("string");

        assertNotNull(info1);
        assertNotNull(info2);
        assertEquals(info1.name(), info2.name());
    }

    @Test
    @DisplayName("Should return null for unknown types")
    void testGetTypeInfoUnknown() {
        assertNull(Xsd11BuiltinTypes.getTypeInfo("xs:unknownType"));
        assertNull(Xsd11BuiltinTypes.getTypeInfo("invalidType"));
        assertNull(Xsd11BuiltinTypes.getTypeInfo(null));
        assertNull(Xsd11BuiltinTypes.getTypeInfo(""));
    }

    @Test
    @DisplayName("Should provide complete information for dateTimeStamp")
    void testDateTimeStampInfo() {
        Xsd11BuiltinTypes.TypeInfo info = Xsd11BuiltinTypes.getTypeInfo("xs:dateTimeStamp");

        assertNotNull(info);
        assertEquals("xs:dateTimeStamp", info.name());
        assertEquals("xs:dateTime", info.baseType());
        assertTrue(info.isXsd11Only());
        assertNotNull(info.description());
        assertNotNull(info.example());
        assertTrue(info.description().contains("timezone"));
        assertTrue(info.description().contains("mandatory"));
    }

    @Test
    @DisplayName("Should provide complete information for yearMonthDuration")
    void testYearMonthDurationInfo() {
        Xsd11BuiltinTypes.TypeInfo info = Xsd11BuiltinTypes.getTypeInfo("xs:yearMonthDuration");

        assertNotNull(info);
        assertEquals("xs:yearMonthDuration", info.name());
        assertEquals("xs:duration", info.baseType());
        assertTrue(info.isXsd11Only());
        assertTrue(info.description().contains("year and month"));
    }

    @Test
    @DisplayName("Should provide complete information for dayTimeDuration")
    void testDayTimeDurationInfo() {
        Xsd11BuiltinTypes.TypeInfo info = Xsd11BuiltinTypes.getTypeInfo("xs:dayTimeDuration");

        assertNotNull(info);
        assertEquals("xs:dayTimeDuration", info.name());
        assertEquals("xs:duration", info.baseType());
        assertTrue(info.isXsd11Only());
        assertTrue(info.description().contains("day, hour, minute, and second"));
    }

    @Test
    @DisplayName("Should provide complete information for precisionDecimal")
    void testPrecisionDecimalInfo() {
        Xsd11BuiltinTypes.TypeInfo info = Xsd11BuiltinTypes.getTypeInfo("xs:precisionDecimal");

        assertNotNull(info);
        assertEquals("xs:precisionDecimal", info.name());
        assertEquals("xs:decimal", info.baseType());
        assertTrue(info.isXsd11Only());
        assertTrue(info.description().contains("arbitrary precision"));
        assertTrue(info.description().contains("trailing zeros"));
    }

    @Test
    @DisplayName("Should return all XSD 1.1 type names")
    void testGetXsd11TypeNames() {
        Set<String> xsd11Types = Xsd11BuiltinTypes.getXsd11TypeNames();

        assertEquals(4, xsd11Types.size());
        assertTrue(xsd11Types.contains("xs:dateTimeStamp"));
        assertTrue(xsd11Types.contains("xs:yearMonthDuration"));
        assertTrue(xsd11Types.contains("xs:dayTimeDuration"));
        assertTrue(xsd11Types.contains("xs:precisionDecimal"));
    }

    @Test
    @DisplayName("Should return all type names including XSD 1.0 types")
    void testGetAllTypeNames() {
        Set<String> allTypes = Xsd11BuiltinTypes.getAllTypeNames();

        assertTrue(allTypes.size() > 4); // More than just XSD 1.1 types
        assertTrue(allTypes.contains("xs:string"));
        assertTrue(allTypes.contains("xs:integer"));
        assertTrue(allTypes.contains("xs:dateTimeStamp"));
    }

    @Test
    @DisplayName("Should generate documentation for types")
    void testGenerateDocumentation() {
        String doc = Xsd11BuiltinTypes.generateDocumentation("xs:dateTimeStamp");

        assertNotNull(doc);
        assertTrue(doc.contains("Type: xs:dateTimeStamp"));
        assertTrue(doc.contains("Base Type: xs:dateTime"));
        assertTrue(doc.contains("XSD Version: 1.1 only"));
        assertTrue(doc.contains("Description:"));
        assertTrue(doc.contains("Example:"));
    }

    @Test
    @DisplayName("Should generate documentation for XSD 1.0 types")
    void testGenerateDocumentationForXsd10Type() {
        String doc = Xsd11BuiltinTypes.generateDocumentation("xs:string");

        assertNotNull(doc);
        assertTrue(doc.contains("Type: xs:string"));
        assertTrue(doc.contains("XSD Version: 1.0+"));
    }

    @Test
    @DisplayName("Should handle unknown type in documentation generation")
    void testGenerateDocumentationUnknownType() {
        String doc = Xsd11BuiltinTypes.generateDocumentation("xs:unknownType");

        assertNotNull(doc);
        assertTrue(doc.contains("Unknown type"));
    }

    @Test
    @DisplayName("Should validate builtin types")
    void testIsBuiltinType() {
        assertTrue(Xsd11BuiltinTypes.isBuiltinType("xs:string"));
        assertTrue(Xsd11BuiltinTypes.isBuiltinType("xs:integer"));
        assertTrue(Xsd11BuiltinTypes.isBuiltinType("xs:dateTimeStamp"));
        assertFalse(Xsd11BuiltinTypes.isBuiltinType("xs:unknownType"));
        assertFalse(Xsd11BuiltinTypes.isBuiltinType("customType"));
    }

    @Test
    @DisplayName("Should generate XSD 1.1 types list")
    void testGetXsd11TypesList() {
        String list = Xsd11BuiltinTypes.getXsd11TypesList();

        assertNotNull(list);
        assertTrue(list.contains("XSD 1.1 New Built-in Types"));
        assertTrue(list.contains("xs:dateTimeStamp"));
        assertTrue(list.contains("xs:yearMonthDuration"));
        assertTrue(list.contains("xs:dayTimeDuration"));
        assertTrue(list.contains("xs:precisionDecimal"));
    }

    @Test
    @DisplayName("Should handle common XSD 1.0 types correctly")
    void testCommonXsd10Types() {
        String[] commonTypes = {
                "xs:string", "xs:boolean", "xs:decimal", "xs:integer",
                "xs:int", "xs:long", "xs:double", "xs:float",
                "xs:date", "xs:time", "xs:dateTime", "xs:duration",
                "xs:anyURI", "xs:QName"
        };

        for (String type : commonTypes) {
            Xsd11BuiltinTypes.TypeInfo info = Xsd11BuiltinTypes.getTypeInfo(type);
            assertNotNull(info, "Type should be defined: " + type);
            assertFalse(info.isXsd11Only(), "Type should not be XSD 1.1 only: " + type);
        }
    }
}
