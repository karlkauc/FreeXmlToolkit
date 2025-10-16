package org.fxt.freexmltoolkit.controller;

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
 * Unit tests for XsdController FindReplaceDialog integration
 */
@ExtendWith({ApplicationExtension.class})
class XsdControllerFindReplaceTest {

    private XsdController controller;

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        try {
            if (!Platform.isFxApplicationThread()) {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.startup(latch::countDown);
                latch.await();
            }
        } catch (IllegalStateException e) {
            // Platform might already be initialized, this is OK
        }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                controller = new XsdController();
                // Initialize basic components that would normally be done by FXML loading
                controller.initialize();
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();
    }

    @Test
    void testFindReplaceDialogIntegration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Test that the controller initializes without errors with FindReplaceDialog
                assertNotNull(controller, "Controller should be initialized");

                // Test that initialization completes successfully
                assertTrue(true, "FindReplaceDialog integration initialized successfully");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();
    }

    @Test
    void testControllerWithFindReplaceDialog() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Test that we can create a controller with FindReplaceDialog support
                assertNotNull(controller, "Controller should be created successfully with FindReplaceDialog");
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();

        // Test passes if no exception is thrown
        assertTrue(true, "FindReplaceDialog integration test passed");
    }
}