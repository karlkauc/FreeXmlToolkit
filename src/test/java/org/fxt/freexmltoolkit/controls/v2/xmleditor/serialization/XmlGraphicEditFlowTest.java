package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;
import org.junit.jupiter.api.Test;

/**
 * Tests that simulate the exact flow when editing in the graphical XML view:
 * 1. Load XML text into XmlEditorContext (same as refreshGraphicViewV2)
 * 2. Execute a SetElementTextCommand (same as commitEditing in XmlCanvasView)
 * 3. Call context.serializeToString() (same as notifyDocumentModified)
 * 4. Verify the output has no blank lines
 */
class XmlGraphicEditFlowTest {

    @Test
    void testEditFlowWithSmallXml() {
        String originalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FundsXML4 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:noNamespaceSchemaLocation="https://fdp-service.oekb.at/FundsXML_4.1.10_AI.xsd">
                   <ControlData>
                      <UniqueDocumentID>EAM_7511019_20260130_FUND_1056</UniqueDocumentID>
                      <DocumentGenerated>2026-03-10T14:31:45</DocumentGenerated>
                      <ContentDate>2026-01-30</ContentDate>
                   </ControlData>
                </FundsXML4>
                """;

        // Step 1: Load into XmlEditorContext (same as refreshGraphicViewV2)
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(originalXml);

        // Step 2: Find element and execute command (same as commitEditing)
        XmlElement root = context.getDocument().getRootElement();
        XmlElement controlData = root.getChildElements().get(0);
        XmlElement uniqueDocId = controlData.getChildElements().get(0);

        context.executeCommand(new SetElementTextCommand(uniqueDocId, "EDITED_VALUE"));

        // Step 3: Serialize (same as notifyDocumentModified)
        String result = context.serializeToString();

        // Step 4: Verify
        System.out.println("=== SERIALIZED OUTPUT ===");
        String[] lines = result.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            System.out.println(String.format("%3d: '%s'", i + 1, lines[i]));
        }
        System.out.println("=== END ===");

        assertFalse(result.contains("\n\n"),
                "Should not contain blank lines (consecutive newlines). Output:\n" + result);
        assertTrue(result.contains("EDITED_VALUE"));
    }

    @Test
    void testEditFlowWithRealFile() throws IOException {
        Path realFile = Path.of("/home/karl/FreeXmlToolkit/HRICAMUSEEB4-2026-02-27.xml");
        if (!Files.exists(realFile)) {
            return;
        }

        String originalXml = Files.readString(realFile);

        // Step 1: Load into XmlEditorContext
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(originalXml);

        // Step 2: Edit a value
        XmlElement root = context.getDocument().getRootElement();
        XmlElement controlData = root.getChildElements().get(0);
        XmlElement uniqueDocId = controlData.getChildElements().get(0);

        context.executeCommand(new SetElementTextCommand(uniqueDocId, "EDITED_VALUE"));

        // Step 3: Serialize
        String result = context.serializeToString();

        // Write first 3000 chars to file for inspection
        Files.writeString(Path.of("/tmp/graphic_edit_flow_output.txt"),
                result.substring(0, Math.min(3000, result.length())));

        // Step 4: Verify — check first 50 lines for blank lines
        String[] lines = result.split("\n", -1);
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            System.out.println(String.format("%3d: '%s'", i + 1, lines[i]));
        }

        assertFalse(result.contains("\n\n"),
                "Real file: should not contain blank lines after edit");
    }

    @Test
    void testEditFlowPreservesStructure() {
        // Test with deeply nested XML
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "  <level1>\n" +
                "    <level2>\n" +
                "      <level3>original</level3>\n" +
                "    </level2>\n" +
                "  </level1>\n" +
                "</root>";

        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);

        // Edit the deeply nested value
        XmlElement root = context.getDocument().getRootElement();
        XmlElement level1 = root.getChildElements().get(0);
        XmlElement level2 = level1.getChildElements().get(0);
        XmlElement level3 = level2.getChildElements().get(0);

        context.executeCommand(new SetElementTextCommand(level3, "modified"));

        String result = context.serializeToString();

        System.out.println("=== NESTED OUTPUT ===");
        System.out.println(result);
        System.out.println("=== END ===");

        assertFalse(result.contains("\n\n"), "No blank lines");
        assertTrue(result.contains("modified"));
        // Verify proper nesting structure
        assertTrue(result.contains("<level1>"));
        assertTrue(result.contains("</level1>"));
    }
}
