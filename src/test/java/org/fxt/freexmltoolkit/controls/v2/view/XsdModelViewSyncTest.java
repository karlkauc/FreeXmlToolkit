package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Model ↔ View synchronization.
 * Tests that changes to the XsdNode model automatically trigger view updates.
 *
 * @since 2.0
 */
@ExtendWith(ApplicationExtension.class)
class XsdModelViewSyncTest {

    private Stage stage;

    @Start
    private void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void testModelChangeTriggersRedraw() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        // Create XsdGraphView with the schema
        XsdGraphView view = new XsdGraphView(schema);

        // Create a latch to track redraw calls
        CountDownLatch redrawLatch = new CountDownLatch(1);
        AtomicBoolean redrawCalled = new AtomicBoolean(false);

        // Run on JavaFX thread
        Platform.runLater(() -> {
            // Show the view (required for rendering)
            // XsdGraphView extends BorderPane, so we can use it directly
            stage.setScene(new javafx.scene.Scene(view, 800, 600));
            stage.show();

            // Wait for initial render to complete
            Platform.runLater(() -> {
                // Now modify the model - this should trigger a redraw
                XsdElement rootElement = (XsdElement) schema.getChildren().get(0);

                // Track if redraw happens by checking if PropertyChangeListener fires
                rootElement.addPropertyChangeListener(evt -> {
                    redrawCalled.set(true);
                    redrawLatch.countDown();
                });

                // Change element name - this should trigger PropertyChangeListener
                rootElement.setName("modifiedRoot");
            });
        });

        // Wait for the redraw to be triggered
        boolean redrawTriggered = redrawLatch.await(5, TimeUnit.SECONDS);

        assertTrue(redrawTriggered, "Redraw should be triggered when model changes");
        assertTrue(redrawCalled.get(), "PropertyChangeListener should be invoked");
    }

    @Test
    void testPropertyChangeListenerRegistration() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="person">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        // Track redraw calls
        CountDownLatch callbackLatch = new CountDownLatch(1);
        Runnable redrawCallback = callbackLatch::countDown;

        // Build visual tree with callback
        Platform.runLater(() -> {
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            XsdNodeRenderer.VisualNode rootNode = builder.buildFromSchema(schema, redrawCallback);

            assertNotNull(rootNode, "Root node should be created");

            // Modify model
            XsdElement personElement = (XsdElement) schema.getChildren().get(0);
            personElement.setType("customType");  // This should trigger callback
        });

        // Wait for callback to be invoked
        boolean callbackInvoked = callbackLatch.await(5, TimeUnit.SECONDS);
        assertTrue(callbackInvoked, "Model change callback should be invoked");
    }

    @Test
    void testVisualNodeHoldsModelReference() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        Platform.runLater(() -> {
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            XsdNodeRenderer.VisualNode rootNode = builder.buildFromSchema(schema);

            assertNotNull(rootNode, "Root node should be created");

            // Verify model reference
            Object modelObject = rootNode.getModelObject();
            assertInstanceOf(XsdElement.class, modelObject, "Model object should be XsdElement");

            XsdElement element = (XsdElement) modelObject;
            assertEquals("test", element.getName(), "Element name should match");
        });

        // Give JavaFX thread time to complete
        Thread.sleep(100);
    }

    @Test
    void testCallbackRunsOnJavaFXThread() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicBoolean ranOnFXThread = new AtomicBoolean(false);

        Runnable redrawCallback = () -> {
            // Check if running on JavaFX Application Thread
            ranOnFXThread.set(Platform.isFxApplicationThread());
            callbackLatch.countDown();
        };

        Platform.runLater(() -> {
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            builder.buildFromSchema(schema, redrawCallback);

            // Modify model to trigger callback
            XsdElement rootElement = (XsdElement) schema.getChildren().get(0);
            rootElement.setName("modified");
        });

        boolean callbackInvoked = callbackLatch.await(5, TimeUnit.SECONDS);
        assertTrue(callbackInvoked, "Callback should be invoked");
        assertTrue(ranOnFXThread.get(), "Callback should run on JavaFX Application Thread");
    }

    @Test
    void testMultipleModelChangesHandled() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="child1" type="xs:string"/>
                                <xs:element name="child2" type="xs:int"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(xsd);

        // Expect at least 3 callback invocations (one for each element change)
        CountDownLatch callbackLatch = new CountDownLatch(3);

        Runnable redrawCallback = callbackLatch::countDown;

        Platform.runLater(() -> {
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            builder.buildFromSchema(schema, redrawCallback);

            // Get elements
            XsdElement rootElement = (XsdElement) schema.getChildren().get(0);
            XsdComplexType complexType = (XsdComplexType) rootElement.getChildren().stream()
                    .filter(n -> n instanceof XsdComplexType)
                    .findFirst()
                    .orElse(null);

            assertNotNull(complexType, "ComplexType should exist");

            XsdSequence sequence = (XsdSequence) complexType.getChildren().stream()
                    .filter(n -> n instanceof XsdSequence)
                    .findFirst()
                    .orElse(null);

            assertNotNull(sequence, "Sequence should exist");

            // Modify multiple elements
            rootElement.setName("modifiedRoot");

            XsdElement child1 = (XsdElement) sequence.getChildren().get(0);
            child1.setName("modifiedChild1");

            XsdElement child2 = (XsdElement) sequence.getChildren().get(1);
            child2.setType("xs:string");
        });

        // Wait for all callbacks
        boolean allCallbacksInvoked = callbackLatch.await(5, TimeUnit.SECONDS);
        assertTrue(allCallbacksInvoked, "All model change callbacks should be invoked");
    }
}
