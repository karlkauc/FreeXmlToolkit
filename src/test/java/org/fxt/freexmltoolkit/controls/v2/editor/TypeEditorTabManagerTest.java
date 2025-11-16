package org.fxt.freexmltoolkit.controls.v2.editor;

import javafx.application.Platform;
import javafx.scene.control.TabPane;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.AbstractTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.ComplexTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SimpleTypeEditorTab;
import org.fxt.freexmltoolkit.controls.v2.editor.tabs.SimpleTypesListTab;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TypeEditorTabManager.
 * Tests tab lifecycle, duplicate prevention, dirty tracking, and unsaved changes handling.
 *
 * @since 2.0
 */
@ExtendWith(ApplicationExtension.class)
@DisplayName("TypeEditorTabManager Tests")
class TypeEditorTabManagerTest {

    private TabPane tabPane;
    private TypeEditorTabManager manager;

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
                tabPane = new TabPane();
                manager = new TypeEditorTabManager(tabPane);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Opening same ComplexType twice should prevent duplicate tabs")
    void testOpenComplexTypePreventsDuplicates() throws InterruptedException {
        XsdComplexType complexType = new XsdComplexType("AddressType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Open first time
                manager.openComplexTypeTab(complexType);
                int tabCount1 = tabPane.getTabs().size();
                assertEquals(1, tabCount1, "Should have 1 tab after first open");

                // Open same type again
                manager.openComplexTypeTab(complexType);
                int tabCount2 = tabPane.getTabs().size();
                assertEquals(1, tabCount2, "Should still have 1 tab - no duplicate");

                // Verify the tab is selected
                assertNotNull(tabPane.getSelectionModel().getSelectedItem());
                assertTrue(tabPane.getSelectionModel().getSelectedItem() instanceof ComplexTypeEditorTab);

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Opening same SimpleType twice should prevent duplicate tabs")
    void testOpenSimpleTypePreventsDuplicates() throws InterruptedException {
        XsdSimpleType simpleType = new XsdSimpleType("ISINType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Open first time
                manager.openSimpleTypeTab(simpleType);
                int tabCount1 = tabPane.getTabs().size();
                assertEquals(1, tabCount1, "Should have 1 tab after first open");

                // Open same type again
                manager.openSimpleTypeTab(simpleType);
                int tabCount2 = tabPane.getTabs().size();
                assertEquals(1, tabCount2, "Should still have 1 tab - no duplicate");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Opening SimpleTypes List multiple times should prevent duplicates")
    void testOpenSimpleTypesListPreventsDuplicates() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Open first time
                manager.openSimpleTypesListTab();
                int tabCount1 = tabPane.getTabs().size();
                assertEquals(1, tabCount1, "Should have 1 tab after first open");

                // Open again
                manager.openSimpleTypesListTab();
                int tabCount2 = tabPane.getTabs().size();
                assertEquals(1, tabCount2, "Should still have 1 tab - no duplicate");

                // Verify it's the list tab
                assertTrue(tabPane.getSelectionModel().getSelectedItem() instanceof SimpleTypesListTab);

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Multiple different tabs can be open at the same time")
    void testMultipleTabsCanBeOpen() throws InterruptedException {
        XsdComplexType complexType = new XsdComplexType("AddressType");
        XsdSimpleType simpleType = new XsdSimpleType("ISINType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                manager.openComplexTypeTab(complexType);
                manager.openSimpleTypeTab(simpleType);
                manager.openSimpleTypesListTab();

                assertEquals(3, tabPane.getTabs().size(), "Should have 3 tabs open");
                assertEquals(3, manager.getOpenTabCount(), "Manager should report 3 tabs");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("isTypeOpen should return true for open tabs")
    void testIsTypeOpen() throws InterruptedException {
        XsdComplexType complexType = new XsdComplexType("AddressType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                assertFalse(manager.isTypeOpen(complexType.getId()), "Type should not be open initially");

                manager.openComplexTypeTab(complexType);

                assertTrue(manager.isTypeOpen(complexType.getId()), "Type should be open after opening");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("getOpenTabCount should return correct count")
    void testGetOpenTabCount() throws InterruptedException {
        XsdComplexType type1 = new XsdComplexType("AddressType");
        XsdComplexType type2 = new XsdComplexType("AmountType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                assertEquals(0, manager.getOpenTabCount(), "Initially should have 0 tabs");

                manager.openComplexTypeTab(type1);
                assertEquals(1, manager.getOpenTabCount(), "Should have 1 tab");

                manager.openComplexTypeTab(type2);
                assertEquals(2, manager.getOpenTabCount(), "Should have 2 tabs");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Dirty tab should show * in title")
    void testDirtyTabShowsAsteriskInTitle() throws InterruptedException {
        XsdComplexType complexType = new XsdComplexType("AddressType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                manager.openComplexTypeTab(complexType);

                AbstractTypeEditorTab tab = (AbstractTypeEditorTab) tabPane.getTabs().get(0);
                String originalTitle = tab.getText();

                assertFalse(originalTitle.endsWith("*"), "Tab should not have * initially");

                // Make it dirty
                tab.setDirty(true);

                assertTrue(tab.getText().endsWith("*"), "Dirty tab should have * in title");
                assertTrue(tab.isDirty(), "Tab should be marked as dirty");

                // Clean it
                tab.setDirty(false);

                assertFalse(tab.getText().endsWith("*"), "Clean tab should not have * in title");
                assertFalse(tab.isDirty(), "Tab should not be marked as dirty");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Save all tabs should save only dirty tabs")
    void testSaveAllTabs() throws InterruptedException {
        XsdComplexType type1 = new XsdComplexType("AddressType");
        XsdComplexType type2 = new XsdComplexType("AmountType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                manager.openComplexTypeTab(type1);
                manager.openComplexTypeTab(type2);

                // Make first tab dirty
                AbstractTypeEditorTab tab1 = (AbstractTypeEditorTab) tabPane.getTabs().get(0);
                tab1.setDirty(true);

                assertTrue(tab1.isDirty(), "Tab 1 should be dirty before save");

                // Save all
                boolean success = manager.saveAllTabs();

                assertTrue(success, "saveAllTabs should succeed");
                assertFalse(tab1.isDirty(), "Tab 1 should not be dirty after save");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("SimpleTypes List tab cannot be dirty")
    void testSimpleTypesListCannotBeDirty() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                manager.openSimpleTypesListTab();

                SimpleTypesListTab tab = (SimpleTypesListTab) tabPane.getTabs().get(0);

                assertFalse(tab.isDirty(), "List tab should not be dirty");

                // Try to set dirty (should be ignored)
                tab.setDirty(true);

                assertFalse(tab.isDirty(), "List tab should still not be dirty");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Tab save should clear dirty flag")
    void testTabSaveClearsDirtyFlag() throws InterruptedException {
        XsdComplexType complexType = new XsdComplexType("AddressType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                manager.openComplexTypeTab(complexType);

                AbstractTypeEditorTab tab = (AbstractTypeEditorTab) tabPane.getTabs().get(0);
                tab.setDirty(true);

                assertTrue(tab.isDirty(), "Tab should be dirty before save");

                // Save
                boolean success = tab.save();

                assertTrue(success, "Save should succeed");
                assertFalse(tab.isDirty(), "Tab should not be dirty after save");
                assertFalse(tab.getText().endsWith("*"), "Tab title should not have * after save");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }

    @Test
    @DisplayName("Tab discard should clear dirty flag")
    void testTabDiscardChangesClearsDirtyFlag() throws InterruptedException {
        XsdSimpleType simpleType = new XsdSimpleType("ISINType");

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                manager.openSimpleTypeTab(simpleType);

                AbstractTypeEditorTab tab = (AbstractTypeEditorTab) tabPane.getTabs().get(0);
                tab.setDirty(true);

                assertTrue(tab.isDirty(), "Tab should be dirty before discard");

                // Discard
                tab.discardChanges();

                assertFalse(tab.isDirty(), "Tab should not be dirty after discard");
                assertFalse(tab.getText().endsWith("*"), "Tab title should not have * after discard");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within timeout");
    }
}
