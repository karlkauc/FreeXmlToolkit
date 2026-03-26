package org.fxt.freexmltoolkit.controls.unified;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Tests for UnifiedEditorTabManager.openXsdFileAndNavigate().
 * Requires JavaFX Application Thread (TestFX).
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedEditorTabManagerNavigationTest {

    private TabPane tabPane;
    private UnifiedEditorTabManager tabManager;

    private static final String SIMPLE_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified">
                <xs:element name="Root" type="RootType"/>
                <xs:complexType name="RootType">
                    <xs:sequence>
                        <xs:element name="Child" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:schema>
            """;

    @Start
    void start(Stage stage) {
        tabPane = new TabPane();
        tabManager = new UnifiedEditorTabManager(tabPane);
        stage.show();
    }

    @Test
    void openXsdFileAndNavigate_opensNewTab(@TempDir Path tempDir) throws IOException {
        File xsdFile = tempDir.resolve("test.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        XsdUnifiedTab result = tabManager.openXsdFileAndNavigate(xsdFile, "Root");

        assertNotNull(result);
        assertEquals(1, tabManager.getTabCount());
        assertInstanceOf(XsdUnifiedTab.class, tabManager.getCurrentTab());
    }

    @Test
    void openXsdFileAndNavigate_reusesExistingTab(@TempDir Path tempDir) throws IOException {
        File xsdFile = tempDir.resolve("test.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        // Open the same file twice with different element targets
        XsdUnifiedTab first = tabManager.openXsdFileAndNavigate(xsdFile, "Root");
        XsdUnifiedTab second = tabManager.openXsdFileAndNavigate(xsdFile, "Child");

        // Should still be 1 tab (reused), same instance
        assertEquals(1, tabManager.getTabCount());
        assertSame(first, second);
    }

    @Test
    void openXsdFileAndNavigate_nullFileReturnsNull() {
        assertNull(tabManager.openXsdFileAndNavigate(null, "Root"));
        assertEquals(0, tabManager.getTabCount());
    }

    @Test
    void openXsdFileAndNavigate_nonExistentFileReturnsNull(@TempDir Path tempDir) {
        File nonExistent = tempDir.resolve("missing.xsd").toFile();
        assertNull(tabManager.openXsdFileAndNavigate(nonExistent, "Root"));
        assertEquals(0, tabManager.getTabCount());
    }
}
