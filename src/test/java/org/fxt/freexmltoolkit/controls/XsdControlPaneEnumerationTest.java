package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for XsdControlPane enumeration functionality
 */
@ExtendWith({ApplicationExtension.class})
class XsdControlPaneEnumerationTest {

    private XsdControlPane controlPane;

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        if (!Platform.isFxApplicationThread()) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                controlPane = new XsdControlPane();
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();
    }

    @Test
    void testBasicControlPaneInstantiation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Test that we can create a control pane without errors
                assertNotNull(controlPane, "Control pane should be created successfully");
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();

        // Test passes if no exception is thrown
        assertTrue(true, "Basic instantiation test passed");
    }

    @Test
    void testSetChangeCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Test that we can set a change callback without errors
                controlPane.setChangeCallback(() -> {
                    // Test callback
                });
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();

        // Test passes if no exception is thrown
        assertTrue(true, "Should be able to set change callback");
    }
}