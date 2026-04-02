package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.XmlService;
import org.junit.jupiter.api.Test;

/**
 * Tests whether XmlService.prettyFormat (Java Transformer) produces blank lines.
 * This is a different code path from the V2 serializer.
 */
class XmlPrettyFormatTest {

    @Test
    void testPrettyFormatSmallXml() throws IOException {
        String xml = """
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

        String formatted = XmlService.prettyFormat(xml, 4);
        assertNotNull(formatted);

        Files.writeString(Path.of("/tmp/prettyformat_small.txt"), formatted);

        String[] lines = formatted.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            System.out.println(String.format("%3d: '%s'", i + 1, lines[i]));
        }

        // Check for blank lines (consecutive newlines)
        assertFalse(formatted.contains("\n\n"),
                "prettyFormat should not produce blank lines. Output:\n" + formatted);
    }

    @Test
    void testPrettyFormatRealFile() throws IOException {
        Path realFile = Path.of("/home/karl/FreeXmlToolkit/HRICAMUSEEB4-2026-02-27.xml");
        if (!Files.exists(realFile)) {
            return;
        }

        String xml = Files.readString(realFile);
        String formatted = XmlService.prettyFormat(xml, 4);
        assertNotNull(formatted);

        Files.writeString(Path.of("/tmp/prettyformat_real.txt"),
                formatted.substring(0, Math.min(3000, formatted.length())));

        // Check first 30 lines
        String[] lines = formatted.split("\n", -1);
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            System.out.println(String.format("%3d: '%s'", i + 1, lines[i]));
        }

        assertFalse(formatted.contains("\n\n"),
                "prettyFormat on real file should not produce blank lines");
    }

    @Test
    void testPrettyFormatAfterV2Serialize() throws IOException {
        // Simulate: V2 serializer produces output, then prettyFormat is called on it
        // (in case autoFormat or some other path reformats)
        Path realFile = Path.of("/home/karl/FreeXmlToolkit/HRICAMUSEEB4-2026-02-27.xml");
        if (!Files.exists(realFile)) {
            return;
        }

        String xml = Files.readString(realFile);

        // V2 parse + serialize
        var context = new org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext();
        context.loadDocumentFromString(xml);
        String v2Output = context.serializeToString();

        // Now prettyFormat the V2 output (what might happen if autoFormat triggers)
        String formatted = XmlService.prettyFormat(v2Output, 4);
        assertNotNull(formatted);

        Files.writeString(Path.of("/tmp/prettyformat_after_v2.txt"),
                formatted.substring(0, Math.min(3000, formatted.length())));

        assertFalse(formatted.contains("\n\n"),
                "prettyFormat after V2 serialize should not produce blank lines");
    }
}
