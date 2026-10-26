package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacets;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog.BuiltinType;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog.Category;
import org.junit.jupiter.api.Test;

/**
 * The built-in type catalog is complete (XSD 1.0 + 1.1), internally consistent and agrees with the
 * facet tables in {@link XsdDatatypeFacets}.
 */
class XsdBuiltinTypeCatalogTest {

    @Test
    void containsEveryBuiltinTypeExactlyOnce() {
        List<BuiltinType> all = XsdBuiltinTypeCatalog.all();
        // 3 ur-types + 19 primitives + 25 derived (XSD 1.0) + 4 XSD 1.1 additions
        assertEquals(51, all.size());
        Set<String> names = new HashSet<>();
        for (BuiltinType type : all) {
            assertTrue(names.add(type.localName()), "duplicate: " + type.localName());
            assertFalse(type.description().isBlank(), type.localName() + " needs a description");
            assertFalse(type.example().isBlank(), type.localName() + " needs an example");
        }
        for (String primitive : List.of("string", "boolean", "decimal", "float", "double", "duration", "dateTime",
                "time", "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary",
                "anyURI", "QName", "NOTATION")) {
            assertTrue(names.contains(primitive), "primitive missing: " + primitive);
        }
    }

    @Test
    void everyBaseTypeResolvesToACatalogEntry() {
        for (BuiltinType type : XsdBuiltinTypeCatalog.all()) {
            if (type.localName().equals("anyType")) {
                assertNull(type.baseType());
            } else {
                assertNotNull(type.baseType(), type.localName() + " needs a base type");
                assertTrue(XsdBuiltinTypeCatalog.find(type.baseType()).isPresent(),
                        type.localName() + " has an unknown base " + type.baseType());
            }
        }
    }

    @Test
    void findIgnoresAnyPrefix() {
        assertTrue(XsdBuiltinTypeCatalog.find("xs:int").isPresent());
        assertTrue(XsdBuiltinTypeCatalog.find("xsd:int").isPresent());
        assertTrue(XsdBuiltinTypeCatalog.find("foo:int").isPresent());
        assertTrue(XsdBuiltinTypeCatalog.find("int").isPresent());
        assertTrue(XsdBuiltinTypeCatalog.find(" xs:int ").isPresent());
        assertFalse(XsdBuiltinTypeCatalog.isBuiltin("MyType"));
        assertFalse(XsdBuiltinTypeCatalog.isBuiltin(null));
        assertFalse(XsdBuiltinTypeCatalog.isBuiltin(""));
        assertEquals("xsd:int", XsdBuiltinTypeCatalog.find("int").orElseThrow().qualified("xsd"));
        assertEquals("int", XsdBuiltinTypeCatalog.find("int").orElseThrow().qualified(""));
    }

    @Test
    void xsd11AdditionsAreFlagged() {
        Set<String> xsd11 = XsdBuiltinTypeCatalog.all().stream()
                .filter(BuiltinType::xsd11Only).map(BuiltinType::localName).collect(Collectors.toSet());
        assertEquals(Set.of("dateTimeStamp", "dayTimeDuration", "yearMonthDuration", "precisionDecimal"), xsd11);
        assertTrue(XsdBuiltinTypeCatalog.find("precisionDecimal").orElseThrow().limitedSupport());
        Set<String> lists = XsdBuiltinTypeCatalog.all().stream()
                .filter(BuiltinType::listType).map(BuiltinType::localName).collect(Collectors.toSet());
        assertEquals(Set.of("NMTOKENS", "IDREFS", "ENTITIES"), lists);
    }

    @Test
    void categoriesCoverAllTypesInDisplayOrder() {
        var byCategory = XsdBuiltinTypeCatalog.byCategory();
        assertEquals(List.of(Category.values()), List.copyOf(byCategory.keySet()));
        assertEquals(XsdBuiltinTypeCatalog.all().size(), byCategory.values().stream().mapToInt(List::size).sum());
        assertEquals("string", byCategory.get(Category.STRING).getFirst().localName());
        assertEquals(List.of("anyType", "anySimpleType", "anyAtomicType"),
                byCategory.get(Category.ANY).stream().map(BuiltinType::localName).toList());
    }

    @Test
    void facetTablesKnowEveryAtomicType() {
        for (BuiltinType type : XsdBuiltinTypeCatalog.all()) {
            if (type.category() == Category.ANY || type.listType() || type.limitedSupport()) {
                continue;
            }
            assertFalse(XsdDatatypeFacets.getApplicableFacets(type.localName()).isEmpty(),
                    "XsdDatatypeFacets has no facet table for " + type.localName());
        }
    }
}
