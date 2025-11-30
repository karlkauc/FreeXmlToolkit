package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the structured access methods of XsdAppInfo.
 * Tests @since, @version, @author, @see, and @deprecated annotations.
 *
 * @since 2.0
 */
@DisplayName("XsdAppInfo Structured Access Tests")
class XsdAppInfoStructuredTest {

    private XsdAppInfo appInfo;

    @BeforeEach
    void setUp() {
        appInfo = new XsdAppInfo();
    }

    @Nested
    @DisplayName("@since annotation")
    class SinceTests {

        @Test
        @DisplayName("should set and get @since version")
        void testSetAndGetSince() {
            appInfo.setSince("4.0.0");
            assertEquals("4.0.0", appInfo.getSince());
        }

        @Test
        @DisplayName("should return null when @since not set")
        void testGetSinceWhenNotSet() {
            assertNull(appInfo.getSince());
        }

        @Test
        @DisplayName("should clear @since when set to null")
        void testClearSince() {
            appInfo.setSince("4.0.0");
            appInfo.setSince(null);
            assertNull(appInfo.getSince());
        }

        @Test
        @DisplayName("should clear @since when set to empty string")
        void testClearSinceWithEmptyString() {
            appInfo.setSince("4.0.0");
            appInfo.setSince("");
            assertNull(appInfo.getSince());
        }

        @Test
        @DisplayName("should trim @since value")
        void testTrimSinceValue() {
            appInfo.setSince("  4.0.0  ");
            assertEquals("4.0.0", appInfo.getSince());
        }
    }

    @Nested
    @DisplayName("@version annotation")
    class VersionTests {

        @Test
        @DisplayName("should set and get @version")
        void testSetAndGetVersion() {
            appInfo.setVersion("1.0-SNAPSHOT");
            assertEquals("1.0-SNAPSHOT", appInfo.getVersion());
        }

        @Test
        @DisplayName("should return null when @version not set")
        void testGetVersionWhenNotSet() {
            assertNull(appInfo.getVersion());
        }
    }

    @Nested
    @DisplayName("@author annotation")
    class AuthorTests {

        @Test
        @DisplayName("should set and get @author")
        void testSetAndGetAuthor() {
            appInfo.setAuthor("John Doe");
            assertEquals("John Doe", appInfo.getAuthor());
        }

        @Test
        @DisplayName("should return null when @author not set")
        void testGetAuthorWhenNotSet() {
            assertNull(appInfo.getAuthor());
        }
    }

    @Nested
    @DisplayName("@see annotation")
    class SeeTests {

        @Test
        @DisplayName("should add single @see reference")
        void testAddSingleSeeReference() {
            appInfo.addSeeReference("{@link /Root/Element}");
            List<String> refs = appInfo.getSeeReferences();
            assertEquals(1, refs.size());
            assertEquals("{@link /Root/Element}", refs.get(0));
        }

        @Test
        @DisplayName("should add multiple @see references")
        void testAddMultipleSeeReferences() {
            appInfo.addSeeReference("{@link /Root/Element1}");
            appInfo.addSeeReference("{@link /Root/Element2}");
            appInfo.addSeeReference("See also documentation");
            List<String> refs = appInfo.getSeeReferences();
            assertEquals(3, refs.size());
        }

        @Test
        @DisplayName("should remove specific @see reference")
        void testRemoveSeeReference() {
            appInfo.addSeeReference("{@link /Root/Element1}");
            appInfo.addSeeReference("{@link /Root/Element2}");
            appInfo.removeSeeReference("{@link /Root/Element1}");
            List<String> refs = appInfo.getSeeReferences();
            assertEquals(1, refs.size());
            assertEquals("{@link /Root/Element2}", refs.get(0));
        }

        @Test
        @DisplayName("should clear all @see references")
        void testClearSeeReferences() {
            appInfo.addSeeReference("{@link /Root/Element1}");
            appInfo.addSeeReference("{@link /Root/Element2}");
            appInfo.clearSeeReferences();
            assertTrue(appInfo.getSeeReferences().isEmpty());
        }

        @Test
        @DisplayName("should return empty list when no @see references")
        void testGetSeeReferencesWhenNone() {
            assertTrue(appInfo.getSeeReferences().isEmpty());
        }

        @Test
        @DisplayName("should not add null or empty @see reference")
        void testDoNotAddNullOrEmpty() {
            appInfo.addSeeReference(null);
            appInfo.addSeeReference("");
            appInfo.addSeeReference("   ");
            assertTrue(appInfo.getSeeReferences().isEmpty());
        }
    }

    @Nested
    @DisplayName("@deprecated annotation")
    class DeprecatedTests {

        @Test
        @DisplayName("should set and get @deprecated message")
        void testSetAndGetDeprecated() {
            appInfo.setDeprecated("Use NewElement instead");
            assertEquals("Use NewElement instead", appInfo.getDeprecated());
            assertTrue(appInfo.isDeprecated());
        }

        @Test
        @DisplayName("should return null when not deprecated")
        void testGetDeprecatedWhenNotSet() {
            assertNull(appInfo.getDeprecated());
            assertFalse(appInfo.isDeprecated());
        }

        @Test
        @DisplayName("should clear deprecation")
        void testClearDeprecated() {
            appInfo.setDeprecated("Deprecated");
            appInfo.clearDeprecated();
            assertNull(appInfo.getDeprecated());
            assertFalse(appInfo.isDeprecated());
        }

        @Test
        @DisplayName("should handle deprecated with {@link}")
        void testDeprecatedWithLink() {
            String message = "Use {@link /NewRoot/NewElement} instead";
            appInfo.setDeprecated(message);
            assertEquals(message, appInfo.getDeprecated());
        }

        @Test
        @DisplayName("should handle empty deprecation message")
        void testEmptyDeprecatedMessage() {
            appInfo.setDeprecated("");
            // Empty string should still mark as deprecated
            assertTrue(appInfo.isDeprecated());
            assertEquals("", appInfo.getDeprecated());
        }
    }

    @Nested
    @DisplayName("Combined operations")
    class CombinedTests {

        @Test
        @DisplayName("should handle all annotations together")
        void testAllAnnotationsTogether() {
            appInfo.setSince("4.0.0");
            appInfo.setVersion("1.0");
            appInfo.setAuthor("John Doe");
            appInfo.addSeeReference("{@link /Element1}");
            appInfo.addSeeReference("{@link /Element2}");
            appInfo.setDeprecated("Use new API");

            assertEquals("4.0.0", appInfo.getSince());
            assertEquals("1.0", appInfo.getVersion());
            assertEquals("John Doe", appInfo.getAuthor());
            assertEquals(2, appInfo.getSeeReferences().size());
            assertEquals("Use new API", appInfo.getDeprecated());
            assertTrue(appInfo.isDeprecated());
            assertTrue(appInfo.hasEntries());
        }

        @Test
        @DisplayName("should copy appinfo correctly")
        void testCopy() {
            appInfo.setSince("4.0.0");
            appInfo.addSeeReference("{@link /Element}");
            appInfo.setDeprecated("Old API");

            XsdAppInfo copy = appInfo.copy();

            assertEquals("4.0.0", copy.getSince());
            assertEquals(1, copy.getSeeReferences().size());
            assertEquals("Old API", copy.getDeprecated());

            // Modify original, copy should not change
            appInfo.setSince("5.0.0");
            assertEquals("4.0.0", copy.getSince());
        }

        @Test
        @DisplayName("should convert to display string and back")
        void testDisplayStringRoundTrip() {
            appInfo.setSince("4.0.0");
            appInfo.addSeeReference("{@link /Element}");

            String displayString = appInfo.toDisplayString();
            assertNotNull(displayString);
            assertTrue(displayString.contains("@since"));
            assertTrue(displayString.contains("@see"));

            XsdAppInfo parsed = XsdAppInfo.fromDisplayString(displayString);
            assertEquals("4.0.0", parsed.getSince());
            assertEquals(1, parsed.getSeeReferences().size());
        }
    }

    @Nested
    @DisplayName("XML serialization")
    class XmlSerializationTests {

        @Test
        @DisplayName("should generate correct XML for @since")
        void testXmlSerializationSince() {
            appInfo.setSince("4.0.0");
            List<String> xmlStrings = appInfo.toXmlStrings();
            assertEquals(1, xmlStrings.size());
            assertTrue(xmlStrings.get(0).contains("xs:appinfo"));
            assertTrue(xmlStrings.get(0).contains("@since"));
            assertTrue(xmlStrings.get(0).contains("4.0.0"));
        }

        @Test
        @DisplayName("should generate correct XML for multiple entries")
        void testXmlSerializationMultiple() {
            appInfo.setSince("4.0.0");
            appInfo.addSeeReference("{@link /Element}");
            appInfo.setDeprecated("Old API");

            List<String> xmlStrings = appInfo.toXmlStrings();
            assertEquals(3, xmlStrings.size());
        }
    }
}
