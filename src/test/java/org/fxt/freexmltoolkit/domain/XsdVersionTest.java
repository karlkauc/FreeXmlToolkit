package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdVersion class
 */
@DisplayName("XSD Version Tests")
class XsdVersionTest {

    @Test
    @DisplayName("Should create XSD 1.0 version")
    void testCreateVersion10() {
        XsdVersion version = new XsdVersion("1.0", false);

        assertTrue(version.isVersion10());
        assertFalse(version.isVersion11());
        assertEquals("1.0", version.getVersion());
        assertFalse(version.isStrict());
    }

    @Test
    @DisplayName("Should create XSD 1.1 version")
    void testCreateVersion11() {
        XsdVersion version = new XsdVersion("1.1", false);

        assertTrue(version.isVersion11());
        assertFalse(version.isVersion10());
        assertEquals("1.1", version.getVersion());
    }

    @Test
    @DisplayName("Should create strict version")
    void testCreateStrictVersion() {
        XsdVersion version = new XsdVersion("1.1", true);

        assertTrue(version.isStrict());
        assertTrue(version.isVersion11());
    }

    @Test
    @DisplayName("Should support XSD 1.1 features in version 1.1")
    void testSupportsXsd11Features() {
        XsdVersion version = XsdVersion.VERSION_1_1;

        assertTrue(version.supports(Xsd11Feature.ASSERTIONS));
        assertTrue(version.supports(Xsd11Feature.ALTERNATIVES));
        assertTrue(version.supports(Xsd11Feature.OPEN_CONTENT));
    }

    @Test
    @DisplayName("Should not support XSD 1.1 features in version 1.0")
    void testDoesNotSupportXsd11FeaturesIn10() {
        XsdVersion version = XsdVersion.VERSION_1_0;

        assertFalse(version.supports(Xsd11Feature.ASSERTIONS));
        assertFalse(version.supports(Xsd11Feature.ALTERNATIVES));
        assertFalse(version.supports(Xsd11Feature.OPEN_CONTENT));
    }

    @Test
    @DisplayName("Should register detected features")
    void testRegisterFeature() {
        XsdVersion version = new XsdVersion("1.1", false);

        assertFalse(version.hasXsd11Features());

        version.registerFeature(Xsd11Feature.ASSERTIONS);
        assertTrue(version.hasXsd11Features());
        assertEquals(1, version.getDetectedFeatures().size());
        assertTrue(version.getDetectedFeatures().contains(Xsd11Feature.ASSERTIONS));

        version.registerFeature(Xsd11Feature.ALTERNATIVES);
        assertEquals(2, version.getDetectedFeatures().size());
    }

    @Test
    @DisplayName("Should validate XSD 1.1 node in version 1.0")
    void testValidateXsd11NodeInVersion10() {
        XsdVersion version = XsdVersion.VERSION_1_0;

        // Create a node with XSD 1.1 features
        XsdNodeInfo node = new XsdNodeInfo(
                "testElement",
                "xs:string",
                "/testElement",
                "Test documentation",
                List.of(),
                List.of(),
                "1",
                "1",
                XsdNodeInfo.NodeType.ASSERT
        );

        List<String> errors = version.validate(node);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("XSD 1.1 feature"));
        assertTrue(errors.get(0).contains("not supported in XSD 1.0"));
    }

    @Test
    @DisplayName("Should validate XSD 1.1 node in version 1.1")
    void testValidateXsd11NodeInVersion11() {
        XsdVersion version = XsdVersion.VERSION_1_1;

        // Create a node with XSD 1.1 features
        XsdNodeInfo node = new XsdNodeInfo(
                "testElement",
                "xs:string",
                "/testElement",
                "Test documentation",
                List.of(),
                List.of(),
                "1",
                "1",
                XsdNodeInfo.NodeType.ASSERT
        );

        List<String> errors = version.validate(node);

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should create version from attributes with vc:minVersion='1.1'")
    void testFromAttributesWithMinVersion11() {
        XsdVersion version = XsdVersion.fromAttributes("1.1", null);

        assertTrue(version.isVersion11());
    }

    @Test
    @DisplayName("Should create version from attributes with vc:minVersion='1.0'")
    void testFromAttributesWithMinVersion10() {
        XsdVersion version = XsdVersion.fromAttributes("1.0", null);

        assertTrue(version.isVersion10());
    }

    @Test
    @DisplayName("Should default to version 1.0 when no attributes")
    void testFromAttributesDefault() {
        XsdVersion version = XsdVersion.fromAttributes(null, null);

        assertTrue(version.isVersion10());
    }

    @Test
    @DisplayName("Should generate feature summary")
    void testGetFeatureSummary() {
        XsdVersion version = new XsdVersion("1.1", false);
        version.registerFeature(Xsd11Feature.ASSERTIONS);
        version.registerFeature(Xsd11Feature.ALTERNATIVES);

        String summary = version.getFeatureSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("Detected XSD 1.1 Features"));
        assertTrue(summary.contains("Assertions"));
        assertTrue(summary.contains("Type Alternatives"));
    }

    @Test
    @DisplayName("Should generate compatibility warnings")
    void testGetCompatibilityWarnings() {
        XsdVersion version = new XsdVersion("1.0", false);
        version.registerFeature(Xsd11Feature.ASSERTIONS);

        List<String> warnings = version.getCompatibilityWarnings();

        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("XSD 1.1 features")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("vc:minVersion")));
    }

    @Test
    @DisplayName("Should have no warnings for proper version declaration")
    void testNoWarningsForProperVersion() {
        XsdVersion version = new XsdVersion("1.1", false);
        version.registerFeature(Xsd11Feature.ASSERTIONS);

        List<String> warnings = version.getCompatibilityWarnings();

        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void testEqualsAndHashCode() {
        XsdVersion v1 = new XsdVersion("1.1", false);
        XsdVersion v2 = new XsdVersion("1.1", false);
        XsdVersion v3 = new XsdVersion("1.0", false);

        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    @DisplayName("Should generate toString representation")
    void testToString() {
        XsdVersion version = new XsdVersion("1.1", true);
        version.registerFeature(Xsd11Feature.ASSERTIONS);

        String str = version.toString();

        assertTrue(str.contains("XSD 1.1"));
        assertTrue(str.contains("strict"));
        assertTrue(str.contains("Features"));
    }
}
