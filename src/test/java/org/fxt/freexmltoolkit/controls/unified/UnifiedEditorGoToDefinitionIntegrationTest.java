package org.fxt.freexmltoolkit.controls.unified;

import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: XML tab in Unified Editor triggers Go to Definition,
 * which opens the XSD in a new XsdUnifiedTab within the same editor.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedEditorGoToDefinitionIntegrationTest {

    private TabPane tabPane;
    private UnifiedEditorTabManager tabManager;

    private static final String SIMPLE_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified">
                <xs:element name="Root" type="xs:string"/>
            </xs:schema>
            """;

    @Start
    void start(Stage stage) {
        tabPane = new TabPane();
        tabManager = new UnifiedEditorTabManager(tabPane);
        stage.show();
    }

    @Test
    void goToDefinition_opensXsdInSameEditor(@TempDir Path tempDir) throws IOException {
        // Setup: create XSD file on disk
        File xsdFile = tempDir.resolve("schema.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        // Setup: open an XML file first
        File xmlFile = tempDir.resolve("test.xml").toFile();
        Files.writeString(xmlFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Root>Hello</Root>
                """);
        tabManager.openFile(xmlFile);
        assertEquals(1, tabManager.getTabCount());

        // Act: simulate Go to Definition by calling openXsdFileAndNavigate
        // (this is what the handler wired in createTab now does)
        XsdUnifiedTab xsdTab = tabManager.openXsdFileAndNavigate(xsdFile, "Root");

        // Assert: XSD opened as a new tab in the same editor
        assertNotNull(xsdTab);
        assertEquals(2, tabManager.getTabCount());

        // Assert: the XSD tab is now selected
        assertEquals(xsdTab, tabManager.getCurrentTab());
    }

    @Test
    void goToDefinition_reusesExistingXsdTab(@TempDir Path tempDir) throws IOException {
        File xsdFile = tempDir.resolve("schema.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        // Open XSD tab once
        tabManager.openXsdFileAndNavigate(xsdFile, "Root");
        assertEquals(1, tabManager.getTabCount());

        // Go to definition again for same XSD — should reuse tab
        tabManager.openXsdFileAndNavigate(xsdFile, "Root");
        assertEquals(1, tabManager.getTabCount());
    }

    @Test
    void goToDefinition_handlerWiredOnXmlTab(@TempDir Path tempDir) throws IOException {
        // Setup: create files
        File xsdFile = tempDir.resolve("schema.xsd").toFile();
        Files.writeString(xsdFile.toPath(), SIMPLE_XSD);

        File xmlFile = tempDir.resolve("test.xml").toFile();
        Files.writeString(xmlFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Root>Hello</Root>
                """);

        // Open XML tab — Go to Definition handler should be wired automatically
        AbstractUnifiedEditorTab xmlTab = tabManager.openFile(xmlFile);
        assertInstanceOf(XmlUnifiedTab.class, xmlTab);

        // Verify the handler is wired by checking editorContext has a handler set
        XmlUnifiedTab xml = (XmlUnifiedTab) xmlTab;
        assertNotNull(xml.getTextEditor().getEditorContext().getGoToDefinitionHandler());
    }
}
