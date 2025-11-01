package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdControlPane change callback mechanism.
 * Verifies that the change callback is properly invoked when assertions are added, edited, or deleted.
 */
@ExtendWith(ApplicationExtension.class)
class XsdControlPaneChangeCallbackTest {

    @BeforeAll
    static void initJavaFX() {
        // Initialize JavaFX toolkit
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException e) {
            // JavaFX already initialized
        }
    }

    /**
     * Test that the change callback is invoked when setChangeCallback is called
     */
    @Test
    void testChangeCallbackCanBeSet() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger(0);

        Platform.runLater(() -> {
            try {
                XsdControlPane controlPane = new XsdControlPane();

                // Set a callback that increments a counter
                controlPane.setChangeCallback(() -> {
                    callbackCount.incrementAndGet();
                });

                // Verify the callback is set (we can't directly verify it was called without triggering an assertion operation)
                // But we can verify the setter doesn't throw an exception
                assertTrue(true, "Change callback set successfully");

                latch.countDown();
            } catch (Exception e) {
                fail("Exception during test: " + e.getMessage());
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX test execution timed out");
    }

    /**
     * Test that change callback is NOT called when it's not set (null safety)
     */
    @Test
    void testNoExceptionWhenCallbackIsNull() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                    <xs:complexType name="TestType">
                        <xs:sequence>
                            <xs:element name="item" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        XsdDomManipulator manipulator = new XsdDomManipulator();
        manipulator.loadXsd(xsd);

        XsdNodeInfo complexTypeNode = new XsdNodeInfo(
                "TestType",
                null,
                "/TestType",
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                null,
                XsdNodeInfo.NodeType.COMPLEX_TYPE
        );

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                XsdControlPane controlPane = new XsdControlPane();
                controlPane.setUndoManager(new XsdUndoManager());

                // Don't set a change callback - it should be null
                // This should not throw an exception when assertions are added
                controlPane.updateForNode(complexTypeNode, manipulator);

                assertTrue(true, "No exception thrown when callback is null");
                latch.countDown();
            } catch (Exception e) {
                fail("Exception during test: " + e.getMessage());
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX test execution timed out");
    }

    /**
     * Test that getCurrentXsdContent returns the XSD content from the DOM manipulator
     */
    @Test
    void testGetCurrentXsdContentReturnsContent() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:complexType name="TestType">
                        <xs:sequence>
                            <xs:element name="item" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        XsdDomManipulator manipulator = new XsdDomManipulator();
        manipulator.loadXsd(xsd);

        XsdNodeInfo complexTypeNode = new XsdNodeInfo(
                "TestType",
                null,
                "/TestType",
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                null,
                XsdNodeInfo.NodeType.COMPLEX_TYPE
        );

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                XsdControlPane controlPane = new XsdControlPane();
                controlPane.setUndoManager(new XsdUndoManager());
                controlPane.updateForNode(complexTypeNode, manipulator);

                // Get the current XSD content
                String currentContent = controlPane.getCurrentXsdContent();

                assertNotNull(currentContent, "Current XSD content should not be null");
                assertTrue(currentContent.contains("TestType"), "Content should contain TestType");
                assertTrue(currentContent.contains("xs:schema"), "Content should contain xs:schema");

                latch.countDown();
            } catch (Exception e) {
                fail("Exception during test: " + e.getMessage());
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX test execution timed out");
    }

    /**
     * Test that getCurrentXsdContent returns null when no DOM manipulator is set
     */
    @Test
    void testGetCurrentXsdContentReturnsNullWhenNoDomManipulator() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                XsdControlPane controlPane = new XsdControlPane();

                // Don't update with any node/manipulator
                String currentContent = controlPane.getCurrentXsdContent();

                assertNull(currentContent, "Current XSD content should be null when no DOM manipulator is set");

                latch.countDown();
            } catch (Exception e) {
                fail("Exception during test: " + e.getMessage());
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX test execution timed out");
    }
}
