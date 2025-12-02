package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IncludeSourceInfo}.
 */
@DisplayName("IncludeSourceInfo")
class IncludeSourceInfoTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("forMainSchema creates correct source info")
        void forMainSchema_createsCorrectSourceInfo() {
            Path mainPath = Path.of("/test/schema.xsd");

            IncludeSourceInfo info = IncludeSourceInfo.forMainSchema(mainPath);

            assertNotNull(info);
            assertEquals(mainPath, info.getSourceFile());
            assertTrue(info.isMainSchema());
            assertFalse(info.isFromInclude());
            assertNull(info.getSchemaLocation());
            assertNull(info.getIncludeNodeId());
        }

        @Test
        @DisplayName("forIncludedSchema creates correct source info")
        void forIncludedSchema_createsCorrectSourceInfo() {
            Path includePath = Path.of("/test/include/types.xsd");
            String schemaLocation = "include/types.xsd";
            XsdInclude includeNode = new XsdInclude(schemaLocation);

            IncludeSourceInfo info = IncludeSourceInfo.forIncludedSchema(includePath, schemaLocation, includeNode);

            assertNotNull(info);
            assertEquals(includePath, info.getSourceFile());
            assertFalse(info.isMainSchema());
            assertTrue(info.isFromInclude());
            assertEquals(schemaLocation, info.getSchemaLocation());
            assertEquals(includeNode.getId(), info.getIncludeNodeId());
        }

        @Test
        @DisplayName("forIncludedSchema handles null include node")
        void forIncludedSchema_handlesNullIncludeNode() {
            Path includePath = Path.of("/test/include/types.xsd");
            String schemaLocation = "include/types.xsd";

            IncludeSourceInfo info = IncludeSourceInfo.forIncludedSchema(includePath, schemaLocation, null);

            assertNotNull(info);
            assertEquals(includePath, info.getSourceFile());
            assertFalse(info.isMainSchema());
            assertTrue(info.isFromInclude());
            assertEquals(schemaLocation, info.getSchemaLocation());
            assertNull(info.getIncludeNodeId());
        }
    }

    @Nested
    @DisplayName("File Name Extraction")
    class FileNameExtraction {

        @Test
        @DisplayName("getFileName returns correct file name")
        void getFileName_returnsCorrectFileName() {
            Path path = Path.of("/test/schema/main.xsd");
            IncludeSourceInfo info = IncludeSourceInfo.forMainSchema(path);

            assertEquals("main.xsd", info.getFileName());
        }

        @Test
        @DisplayName("getFileName returns unknown for null path")
        void getFileName_returnsUnknownForNullPath() {
            IncludeSourceInfo info = IncludeSourceInfo.forMainSchema(null);

            assertEquals("unknown", info.getFileName());
        }
    }

    @Nested
    @DisplayName("Copy Methods")
    class CopyMethods {

        @Test
        @DisplayName("withSourceFile creates new info with different path")
        void withSourceFile_createsNewInfoWithDifferentPath() {
            Path originalPath = Path.of("/test/original.xsd");
            Path newPath = Path.of("/test/new.xsd");
            IncludeSourceInfo original = IncludeSourceInfo.forMainSchema(originalPath);

            IncludeSourceInfo copy = original.withSourceFile(newPath);

            assertNotEquals(original, copy);
            assertEquals(newPath, copy.getSourceFile());
            assertEquals(original.isMainSchema(), copy.isMainSchema());
        }

        @Test
        @DisplayName("moveToMainSchema creates main schema info")
        void moveToMainSchema_createsMainSchemaInfo() {
            Path includePath = Path.of("/test/include/types.xsd");
            IncludeSourceInfo includeInfo = IncludeSourceInfo.forIncludedSchema(includePath, "types.xsd", null);

            Path mainPath = Path.of("/test/main.xsd");
            IncludeSourceInfo mainInfo = includeInfo.moveToMainSchema(mainPath);

            assertTrue(mainInfo.isMainSchema());
            assertFalse(mainInfo.isFromInclude());
            assertEquals(mainPath, mainInfo.getSourceFile());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equals returns true for identical info")
        void equals_returnsTrueForIdenticalInfo() {
            Path path = Path.of("/test/schema.xsd");
            IncludeSourceInfo info1 = IncludeSourceInfo.forMainSchema(path);
            IncludeSourceInfo info2 = IncludeSourceInfo.forMainSchema(path);

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different source files")
        void equals_returnsFalseForDifferentSourceFiles() {
            IncludeSourceInfo info1 = IncludeSourceInfo.forMainSchema(Path.of("/test/schema1.xsd"));
            IncludeSourceInfo info2 = IncludeSourceInfo.forMainSchema(Path.of("/test/schema2.xsd"));

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("equals returns false for main vs included")
        void equals_returnsFalseForMainVsIncluded() {
            Path path = Path.of("/test/schema.xsd");
            IncludeSourceInfo mainInfo = IncludeSourceInfo.forMainSchema(path);
            IncludeSourceInfo includeInfo = IncludeSourceInfo.forIncludedSchema(path, "schema.xsd", null);

            assertNotEquals(mainInfo, includeInfo);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("toString for main schema includes 'main'")
        void toString_forMainSchemaIncludesMain() {
            IncludeSourceInfo info = IncludeSourceInfo.forMainSchema(Path.of("/test/main.xsd"));

            String str = info.toString();
            assertTrue(str.contains("main"), "Should contain 'main': " + str);
        }

        @Test
        @DisplayName("toString for include schema includes 'include'")
        void toString_forIncludeSchemaIncludesInclude() {
            IncludeSourceInfo info = IncludeSourceInfo.forIncludedSchema(
                    Path.of("/test/types.xsd"), "types.xsd", null);

            String str = info.toString();
            assertTrue(str.contains("include"), "Should contain 'include': " + str);
        }
    }
}
