package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdUnifiedTab validation functionality.
 * Specifically tests that validation detects invalid XSD content after editing.
 */
@ExtendWith(ApplicationExtension.class)
class XsdUnifiedTabValidationTest {

    private XsdUnifiedTab xsdTab;

    @Start
    void start(Stage stage) {
        // Create an XSD tab without a file (will use default template)
        xsdTab = new XsdUnifiedTab(null);

        // Tab must be added to a TabPane
        TabPane tabPane = new TabPane(xsdTab);

        // Add to scene so JavaFX components are initialized
        StackPane root = new StackPane(tabPane);
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    @Test
    void testValidationDetectsInvalidType() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> validationResult = new AtomicReference<>();

        String invalidXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string2"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        Platform.runLater(() -> {
            try {
                // Set the invalid content
                xsdTab.setEditorContent(invalidXsd);

                // Validate
                String result = xsdTab.validate();
                validationResult.set(result);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test timed out");

        // Validation should return an error message (not null)
        assertNotNull(validationResult.get(),
                "Validation should detect invalid type xs:string2");
        assertTrue(validationResult.get().contains("XSD") ||
                        validationResult.get().contains("string2") ||
                        validationResult.get().contains("Error"),
                "Error message should mention the XSD error. Got: " + validationResult.get());
    }

    @Test
    void testValidationPassesForValidXsd() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> validationResult = new AtomicReference<>();

        String validXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        Platform.runLater(() -> {
            try {
                // Set the valid content
                xsdTab.setEditorContent(validXsd);

                // Validate
                String result = xsdTab.validate();
                validationResult.set(result);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test timed out");

        // Validation should return null for valid XSD
        assertNull(validationResult.get(),
                "Validation should pass for valid XSD. Got: " + validationResult.get());
    }

    @Test
    void testValidationDetectsChangesAfterEditing() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> validationResult1 = new AtomicReference<>();
        AtomicReference<String> validationResult2 = new AtomicReference<>();

        String validXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        String invalidXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="name" type="xs:string2"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        Platform.runLater(() -> {
            try {
                // First, set valid content and validate
                xsdTab.setEditorContent(validXsd);
                String result1 = xsdTab.validate();
                validationResult1.set(result1);

                // Now change to invalid content and validate again
                xsdTab.setEditorContent(invalidXsd);
                String result2 = xsdTab.validate();
                validationResult2.set(result2);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test timed out");

        // First validation should pass
        assertNull(validationResult1.get(),
                "First validation (valid XSD) should pass. Got: " + validationResult1.get());

        // Second validation should fail
        assertNotNull(validationResult2.get(),
                "Second validation (invalid XSD) should detect error");
        assertTrue(validationResult2.get().contains("XSD") ||
                        validationResult2.get().contains("string2") ||
                        validationResult2.get().contains("Error"),
                "Error message should mention the XSD error. Got: " + validationResult2.get());
    }

    @Test
    void testValidationReadsDirectlyFromTextEditor() throws Exception {
        // This test verifies that validation reads from the text editor directly,
        // not from cached/serialized content
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> validationResult = new AtomicReference<>();
        AtomicReference<String> editorContent = new AtomicReference<>();

        String invalidXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="test" type="xs:invalidType123"/>
                </xs:schema>
                """;

        Platform.runLater(() -> {
            try {
                // Set content directly in the text editor
                xsdTab.getTextEditor().setText(invalidXsd);

                // Get the content back to verify it was set
                editorContent.set(xsdTab.getTextEditor().getText());

                // Validate - should read from text editor directly
                String result = xsdTab.validate();
                validationResult.set(result);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test timed out");

        // Verify content was set correctly
        assertTrue(editorContent.get().contains("xs:invalidType123"),
                "Editor should contain the invalid type. Got: " + editorContent.get());

        // Validation should detect the invalid type
        assertNotNull(validationResult.get(),
                "Validation should detect invalid type xs:invalidType123");
    }
}
