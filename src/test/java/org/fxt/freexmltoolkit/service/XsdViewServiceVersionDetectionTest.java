package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.Xsd11Feature;
import org.fxt.freexmltoolkit.domain.XsdVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XSD version detection in XsdViewService
 */
@DisplayName("XSD Version Detection Tests")
class XsdViewServiceVersionDetectionTest {

    private XsdViewService service;

    @BeforeEach
    void setUp() {
        service = new XsdViewService();
    }

    @Test
    @DisplayName("Should detect XSD 1.0 by default")
    void testDetectXsd10Default() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion10());
        assertFalse(version.hasXsd11Features());
    }

    @Test
    @DisplayName("Should detect XSD 1.1 from vc:minVersion attribute")
    void testDetectXsd11FromAttribute() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.VERSION_INDICATOR));
    }

    @Test
    @DisplayName("Should detect XSD 1.1 from xs:assert usage")
    void testDetectXsd11FromAssert() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:attribute name="min" type="xs:int"/>
                            <xs:attribute name="max" type="xs:int"/>
                            <xs:assert test="@min le @max"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.hasXsd11Features());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.ASSERTIONS));
    }

    @Test
    @DisplayName("Should detect XSD 1.1 from xs:alternative usage")
    void testDetectXsd11FromAlternative() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="value">
                        <xs:alternative test="@type='int'" type="xs:int"/>
                        <xs:alternative test="@type='string'" type="xs:string"/>
                    </xs:element>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.ALTERNATIVES));
    }

    @Test
    @DisplayName("Should detect XSD 1.1 from xs:openContent usage")
    void testDetectXsd11FromOpenContent() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:complexType name="ExtensibleType">
                        <xs:openContent mode="interleave">
                            <xs:any namespace="##any"/>
                        </xs:openContent>
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.OPEN_CONTENT));
    }

    @Test
    @DisplayName("Should detect XSD 1.1 from xs:override usage")
    void testDetectXsd11FromOverride() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:override schemaLocation="base.xsd">
                        <xs:complexType name="PersonType">
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:override>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.OVERRIDE));
    }

    @Test
    @DisplayName("Should detect XSD 1.1 from new built-in type usage")
    void testDetectXsd11FromNewBuiltinType() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="timestamp" type="xs:dateTimeStamp"/>
                    <xs:element name="duration" type="xs:yearMonthDuration"/>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.NEW_BUILTIN_TYPES));
    }

    @Test
    @DisplayName("Should detect multiple XSD 1.1 features")
    void testDetectMultipleXsd11Features() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="timestamp" type="xs:dateTimeStamp"/>
                            </xs:sequence>
                            <xs:attribute name="min" type="xs:int"/>
                            <xs:attribute name="max" type="xs:int"/>
                            <xs:assert test="@min le @max"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        service.buildLightweightTree(xsdContent);
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion11());
        assertTrue(version.hasXsd11Features());

        // Should have detected at least VERSION_INDICATOR, ASSERTIONS, and NEW_BUILTIN_TYPES
        assertTrue(version.getDetectedFeatures().size() >= 3);
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.VERSION_INDICATOR));
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.ASSERTIONS));
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.NEW_BUILTIN_TYPES));
    }

    @Test
    @DisplayName("Should handle invalid schema gracefully")
    void testHandleInvalidSchemaGracefully() {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <invalid>This is not a valid schema</invalid>
                """;

        // Should not throw exception
        assertDoesNotThrow(() -> {
            service.buildLightweightTree(xsdContent);
            XsdVersion version = service.getXsdVersion();
            assertNotNull(version);
        });
    }

    @Test
    @DisplayName("Should return default version for null content")
    void testHandleNullContent() {
        // getXsdVersion() should return default version before any parsing
        XsdVersion version = service.getXsdVersion();

        assertNotNull(version);
        assertTrue(version.isVersion10());
    }
}
