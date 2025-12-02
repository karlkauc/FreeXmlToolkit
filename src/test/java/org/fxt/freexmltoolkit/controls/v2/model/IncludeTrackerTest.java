package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IncludeTracker}.
 */
@DisplayName("IncludeTracker")
class IncludeTrackerTest {

    private Path mainSchemaPath;
    private IncludeTracker tracker;

    @BeforeEach
    void setUp() {
        mainSchemaPath = Path.of("/test/schema/main.xsd");
        tracker = new IncludeTracker(mainSchemaPath);
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("starts in main schema context")
        void startsInMainSchemaContext() {
            assertTrue(tracker.isInMainSchema());
            assertEquals(0, tracker.getDepth());
            assertFalse(tracker.hasActiveContext());
        }

        @Test
        @DisplayName("returns main schema path initially")
        void returnsMainSchemaPathInitially() {
            assertEquals(mainSchemaPath, tracker.getCurrentSourceFile());
            assertEquals(mainSchemaPath, tracker.getMainSchemaPath());
        }

        @Test
        @DisplayName("has no current include initially")
        void hasNoCurrentIncludeInitially() {
            assertNull(tracker.getCurrentInclude());
            assertNull(tracker.getCurrentSchemaLocation());
        }

        @Test
        @DisplayName("getCurrentSourceInfo returns main schema info")
        void getCurrentSourceInfo_returnsMainSchemaInfo() {
            IncludeSourceInfo info = tracker.getCurrentSourceInfo();

            assertNotNull(info);
            assertTrue(info.isMainSchema());
            assertFalse(info.isFromInclude());
            assertEquals(mainSchemaPath, info.getSourceFile());
        }
    }

    @Nested
    @DisplayName("Push/Pop Context")
    class PushPopContext {

        @Test
        @DisplayName("pushContext increases depth")
        void pushContext_increasesDepth() {
            XsdInclude include = new XsdInclude("include/types.xsd");
            Path includePath = Path.of("/test/schema/include/types.xsd");

            tracker.pushContext(include, includePath);

            assertEquals(1, tracker.getDepth());
            assertFalse(tracker.isInMainSchema());
            assertTrue(tracker.hasActiveContext());
        }

        @Test
        @DisplayName("pushContext updates current info")
        void pushContext_updatesCurrentInfo() {
            XsdInclude include = new XsdInclude("include/types.xsd");
            Path includePath = Path.of("/test/schema/include/types.xsd");

            tracker.pushContext(include, includePath);

            assertEquals(include, tracker.getCurrentInclude());
            assertEquals(includePath, tracker.getCurrentSourceFile());
            assertEquals("include/types.xsd", tracker.getCurrentSchemaLocation());
        }

        @Test
        @DisplayName("popContext decreases depth")
        void popContext_decreasesDepth() {
            XsdInclude include = new XsdInclude("include/types.xsd");
            Path includePath = Path.of("/test/schema/include/types.xsd");

            tracker.pushContext(include, includePath);
            tracker.popContext();

            assertEquals(0, tracker.getDepth());
            assertTrue(tracker.isInMainSchema());
            assertFalse(tracker.hasActiveContext());
        }

        @Test
        @DisplayName("popContext on empty stack throws exception")
        void popContext_onEmptyStackThrowsException() {
            assertThrows(IllegalStateException.class, () -> tracker.popContext());
        }

        @Test
        @DisplayName("nested includes work correctly")
        void nestedIncludes_workCorrectly() {
            XsdInclude include1 = new XsdInclude("level1.xsd");
            Path path1 = Path.of("/test/schema/level1.xsd");
            XsdInclude include2 = new XsdInclude("level2.xsd");
            Path path2 = Path.of("/test/schema/level2.xsd");

            tracker.pushContext(include1, path1);
            assertEquals(1, tracker.getDepth());
            assertEquals(include1, tracker.getCurrentInclude());

            tracker.pushContext(include2, path2);
            assertEquals(2, tracker.getDepth());
            assertEquals(include2, tracker.getCurrentInclude());

            tracker.popContext();
            assertEquals(1, tracker.getDepth());
            assertEquals(include1, tracker.getCurrentInclude());

            tracker.popContext();
            assertEquals(0, tracker.getDepth());
            assertTrue(tracker.isInMainSchema());
        }
    }

    @Nested
    @DisplayName("Source Info")
    class SourceInfo {

        @Test
        @DisplayName("getCurrentSourceInfo returns include info when in include context")
        void getCurrentSourceInfo_returnsIncludeInfoWhenInIncludeContext() {
            XsdInclude include = new XsdInclude("include/types.xsd");
            Path includePath = Path.of("/test/schema/include/types.xsd");

            tracker.pushContext(include, includePath);
            IncludeSourceInfo info = tracker.getCurrentSourceInfo();

            assertNotNull(info);
            assertFalse(info.isMainSchema());
            assertTrue(info.isFromInclude());
            assertEquals(includePath, info.getSourceFile());
            assertEquals("include/types.xsd", info.getSchemaLocation());
            assertEquals(include.getId(), info.getIncludeNodeId());
        }
    }

    @Nested
    @DisplayName("Tag Nodes")
    class TagNodes {

        @Test
        @DisplayName("tagNode sets source info on node")
        void tagNode_setsSourceInfoOnNode() {
            XsdElement element = new XsdElement("TestElement");

            tracker.tagNode(element);

            assertNotNull(element.getSourceInfo());
            assertTrue(element.getSourceInfo().isMainSchema());
            assertEquals(mainSchemaPath, element.getSourceFile());
        }

        @Test
        @DisplayName("tagNode sets include source info when in include context")
        void tagNode_setsIncludeSourceInfoWhenInIncludeContext() {
            XsdInclude include = new XsdInclude("include/types.xsd");
            Path includePath = Path.of("/test/schema/include/types.xsd");
            tracker.pushContext(include, includePath);

            XsdElement element = new XsdElement("IncludedElement");
            tracker.tagNode(element);

            assertNotNull(element.getSourceInfo());
            assertTrue(element.getSourceInfo().isFromInclude());
            assertEquals(includePath, element.getSourceFile());
        }

        @Test
        @DisplayName("tagNode handles null node gracefully")
        void tagNode_handlesNullNodeGracefully() {
            assertDoesNotThrow(() -> tracker.tagNode(null));
        }

        @Test
        @DisplayName("tagNodeRecursively tags all descendants")
        void tagNodeRecursively_tagsAllDescendants() {
            XsdComplexType complexType = new XsdComplexType("TestType");
            XsdSequence sequence = new XsdSequence();
            XsdElement element1 = new XsdElement("Element1");
            XsdElement element2 = new XsdElement("Element2");
            sequence.addChild(element1);
            sequence.addChild(element2);
            complexType.addChild(sequence);

            tracker.tagNodeRecursively(complexType);

            // All nodes should have main schema source info
            assertNotNull(complexType.getSourceInfo());
            assertNotNull(sequence.getSourceInfo());
            assertNotNull(element1.getSourceInfo());
            assertNotNull(element2.getSourceInfo());
            assertTrue(complexType.getSourceInfo().isMainSchema());
            assertTrue(element1.getSourceInfo().isMainSchema());
        }

        @Test
        @DisplayName("tagNodeRecursively handles null node gracefully")
        void tagNodeRecursively_handlesNullNodeGracefully() {
            assertDoesNotThrow(() -> tracker.tagNodeRecursively(null));
        }
    }

    @Nested
    @DisplayName("Clear")
    class Clear {

        @Test
        @DisplayName("clear resets to initial state")
        void clear_resetsToInitialState() {
            XsdInclude include = new XsdInclude("include/types.xsd");
            Path includePath = Path.of("/test/schema/include/types.xsd");
            tracker.pushContext(include, includePath);

            tracker.clear();

            assertEquals(0, tracker.getDepth());
            assertTrue(tracker.isInMainSchema());
            assertFalse(tracker.hasActiveContext());
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("toString shows main when in main context")
        void toString_showsMainWhenInMainContext() {
            String str = tracker.toString();
            assertTrue(str.contains("main"), "Should contain 'main': " + str);
        }

        @Test
        @DisplayName("toString shows depth when in include context")
        void toString_showsDepthWhenInIncludeContext() {
            tracker.pushContext(new XsdInclude("test.xsd"), Path.of("/test/test.xsd"));

            String str = tracker.toString();
            assertTrue(str.contains("depth=1"), "Should contain 'depth=1': " + str);
        }
    }
}
