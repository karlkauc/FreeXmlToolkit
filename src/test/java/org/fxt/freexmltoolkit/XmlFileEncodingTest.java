package org.fxt.freexmltoolkit;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;

class XmlFileEncodingTest {

    @Test
    void testReadAndParseXmlFile() throws Exception {
        File file = new File("temp/LU1949801242_EUR_20250930_v4_1_10 (1).xml");

        if (!file.exists()) {
            System.out.println("File does not exist: " + file.getAbsolutePath());
            return;
        }

        // Read file exactly as the application does
        String content = Files.readString(file.toPath());

        System.out.println("File size: " + file.length() + " bytes");
        System.out.println("Content length: " + content.length() + " chars");
        System.out.println("First 100 chars: " + content.substring(0, Math.min(100, content.length())));
        System.out.println("First 10 bytes (hex):");
        for (int i = 0; i < Math.min(10, content.length()); i++) {
            System.out.printf("%02x ", (int) content.charAt(i));
        }
        System.out.println();

        // Try to parse
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try {
            builder.parse(new InputSource(new StringReader(content)));
            System.out.println("✓ XML parsed successfully");
        } catch (Exception e) {
            System.out.println("✗ XML parsing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
