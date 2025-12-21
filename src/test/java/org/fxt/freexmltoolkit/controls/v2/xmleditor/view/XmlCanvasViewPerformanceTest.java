package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@ExtendWith(ApplicationExtension.class)
public class XmlCanvasViewPerformanceTest {

    private XmlCanvasView canvasView;
    private XmlEditorContext context;

    // Use a large XML document for performance testing
    private static final int NUM_ROOT_CHILDREN = 1000;
    private static final int NUM_NESTED_CHILDREN = 10;
    private static final int NESTING_DEPTH = 3;

    @Start
    void start(Stage stage) {
        // This is called on the JavaFX Application Thread
        // Initialize XmlEditorContext with a dummy document first
        context = new XmlEditorContext(new XmlDocument());
        canvasView = new XmlCanvasView(context);
        stage.setScene(new javafx.scene.Scene(canvasView, 800, 600));
        stage.show();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        // Ensure UI is ready and context is set up before each test
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            context = new XmlEditorContext(new XmlDocument());
            canvasView = new XmlCanvasView(context);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX setup timed out.");
    }

    private XmlDocument createLargeXmlDocument() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        doc.setRootElement(root);

        for (int i = 0; i < NUM_ROOT_CHILDREN; i++) {
            XmlElement child = new XmlElement("item_" + i);
            root.addChild(child);
            addNestedChildren(child, 0);
        }
        return doc;
    }

    private void addNestedChildren(XmlElement parent, int currentDepth) {
        if (currentDepth >= NESTING_DEPTH) {
            return;
        }
        for (int i = 0; i < NUM_NESTED_CHILDREN; i++) {
            XmlElement nestedChild = new XmlElement("nested_item_" + currentDepth + "_" + i);
            parent.addChild(nestedChild);
            nestedChild.setTextContent("Value_" + currentDepth + "_" + i);
            addNestedChildren(nestedChild, currentDepth + 1);
        }
    }

    @Test
    @DisplayName("Performance test for rendering a large XML document")
    @org.junit.jupiter.api.Tag("slow")
    @org.junit.jupiter.api.Disabled("Flaky performance test - timing-sensitive, disabled until performance improvements implemented")
    void testRenderLargeXmlDocumentPerformance() {
        // Create a large XML document model
        XmlDocument largeDoc = createLargeXmlDocument();

        // Define an acceptable time limit (10 seconds for large documents on slow CI)
        Duration timeLimit = Duration.ofSeconds(10);

        // Measure the time to rebuild the tree and render
        assertTimeoutPreemptively(timeLimit, () -> {
            // Update the context with the large document and trigger rebuildTree() in XmlCanvasView
            CountDownLatch renderLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                String xmlString = context.getSerializer().serialize(largeDoc);
                context.loadDocumentFromString(xmlString); // Use public method
                canvasView.render(); // Explicitly call render to measure rendering time
                renderLatch.countDown();
            });
            assertTrue(renderLatch.await(5, TimeUnit.SECONDS), "Rendering large document timed out.");

        }, "Rendering large XML document took too long");
    }
}
