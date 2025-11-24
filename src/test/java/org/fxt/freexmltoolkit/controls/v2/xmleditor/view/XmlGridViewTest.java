package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlGridView class.
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlGridViewTest {

    private XmlEditorContext context;
    private XmlGridView gridView;

    @BeforeAll
    static void initJavaFX() {
        new JFXPanel();
    }

    @BeforeEach
    void setUp() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            context = new XmlEditorContext();
            gridView = new XmlGridView(context);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Basic Tests ====================

    @Test
    void testConstructor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertNotNull(gridView);
            assertNotNull(gridView.getTableView());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testInitialState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertNull(gridView.getParentNode());
            assertNull(gridView.getRepeatingElementName());
            assertEquals(0, gridView.getTableView().getItems().size());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Grid Loading Tests ====================

    @Test
    void testLoadRepeatingElements() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            // Create document with repeating elements
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            XmlElement item1 = new XmlElement("item");
            item1.setAttribute("id", "1");
            item1.setAttribute("name", "First");
            root.addChild(item1);

            XmlElement item2 = new XmlElement("item");
            item2.setAttribute("id", "2");
            item2.setAttribute("name", "Second");
            root.addChild(item2);

            XmlElement item3 = new XmlElement("item");
            item3.setAttribute("id", "3");
            item3.setAttribute("name", "Third");
            root.addChild(item3);

            context.setDocument(doc);

            // Load repeating elements
            gridView.loadRepeatingElements(root, "item");

            // Wait for grid to build
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            assertEquals(root, gridView.getParentNode());
            assertEquals("item", gridView.getRepeatingElementName());
            assertEquals(3, gridView.getTableView().getItems().size());
            assertTrue(gridView.getTableView().getColumns().size() > 0);

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testLoadEmptyParent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            gridView.loadRepeatingElements(root, "item");

            assertNull(gridView.getParentNode());
            assertNull(gridView.getRepeatingElementName());
            assertEquals(0, gridView.getTableView().getItems().size());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testLoadSingleElement() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            XmlElement item = new XmlElement("item");
            root.addChild(item);

            gridView.loadRepeatingElements(root, "item");

            // Single element is loaded (not repeating, but still valid)
            assertEquals(1, gridView.getTableView().getItems().size());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Column Generation Tests ====================

    @Test
    void testColumnGeneration() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            // Create items with attributes and child elements
            for (int i = 1; i <= 3; i++) {
                XmlElement item = new XmlElement("item");
                item.setAttribute("id", String.valueOf(i));
                item.setAttribute("name", "Item" + i);

                XmlElement description = new XmlElement("description");
                description.setTextContent("Description " + i);
                item.addChild(description);

                root.addChild(item);
            }

            gridView.loadRepeatingElements(root, "item");

            // Wait for grid to build
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            // Should have: Index, @id, @name, description, Text columns
            assertTrue(gridView.getTableView().getColumns().size() >= 4);

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Auto-Detection Tests ====================

    @Test
    void testAutoDetectRepeatingElements() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            // Add repeating elements
            XmlElement item1 = new XmlElement("item");
            XmlElement item2 = new XmlElement("item");
            XmlElement item3 = new XmlElement("item");

            root.addChild(item1);
            root.addChild(item2);
            root.addChild(item3);

            context.setDocument(doc);
            context.getSelectionModel().setSelectedNode(root);

            // Wait for auto-detection
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // Ignore
            }

            // Grid should auto-detect repeating "item" elements
            assertEquals("item", gridView.getRepeatingElementName());
            assertEquals(3, gridView.getTableView().getItems().size());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testNoRepeatingElementsDetected() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            // Add non-repeating elements
            XmlElement item1 = new XmlElement("item1");
            XmlElement item2 = new XmlElement("item2");
            XmlElement item3 = new XmlElement("item3");

            root.addChild(item1);
            root.addChild(item2);
            root.addChild(item3);

            context.setDocument(doc);
            context.getSelectionModel().setSelectedNode(root);

            // Wait for auto-detection
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // Ignore
            }

            // Grid should be empty
            assertNull(gridView.getRepeatingElementName());
            assertEquals(0, gridView.getTableView().getItems().size());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Refresh Tests ====================

    @Test
    void testRefresh() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("root");
            doc.setRootElement(root);

            XmlElement item1 = new XmlElement("item");
            XmlElement item2 = new XmlElement("item");
            root.addChild(item1);
            root.addChild(item2);

            gridView.loadRepeatingElements(root, "item");

            // Wait for initial load
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            assertEquals(2, gridView.getTableView().getItems().size());

            // Add another item
            XmlElement item3 = new XmlElement("item");
            root.addChild(item3);

            // Refresh
            gridView.refresh();

            // Wait for refresh
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            assertEquals(3, gridView.getTableView().getItems().size());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== ElementRowData Tests ====================

    @Test
    void testElementRowDataAttributes() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlElement elem = new XmlElement("item");
            elem.setAttribute("id", "123");
            elem.setAttribute("name", "Test");

            XmlGridView.ElementRowData row = new XmlGridView.ElementRowData(elem);

            assertEquals("123", row.getAttributeProperty("id").get());
            assertEquals("Test", row.getAttributeProperty("name").get());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testElementRowDataChildText() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlElement elem = new XmlElement("item");

            XmlElement description = new XmlElement("description");
            description.setTextContent("Test description");
            elem.addChild(description);

            XmlGridView.ElementRowData row = new XmlGridView.ElementRowData(elem);

            assertEquals("Test description", row.getChildTextProperty("description").get());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testElementRowDataTextContent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlElement elem = new XmlElement("item");
            elem.setTextContent("Direct text");

            XmlGridView.ElementRowData row = new XmlGridView.ElementRowData(elem);

            assertEquals("Direct text", row.getTextProperty().get());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testElementRowDataRefresh() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlElement elem = new XmlElement("item");
            elem.setAttribute("id", "1");

            XmlGridView.ElementRowData row = new XmlGridView.ElementRowData(elem);

            assertEquals("1", row.getAttributeProperty("id").get());

            // Change attribute
            elem.setAttribute("id", "2");

            // Refresh
            row.refresh();

            assertEquals("2", row.getAttributeProperty("id").get());

            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
