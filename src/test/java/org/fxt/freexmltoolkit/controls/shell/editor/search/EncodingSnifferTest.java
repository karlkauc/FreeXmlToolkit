package org.fxt.freexmltoolkit.controls.shell.editor.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncodingSnifferTest {

    @Test
    void utf8BomIsDetectedAndStripped(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bom.xml");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "<root/>".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        Files.write(file, all);

        EncodingSniffer.Loaded loaded = EncodingSniffer.load(file);
        assertEquals("<root/>", loaded.text());
        assertEquals(StandardCharsets.UTF_8, loaded.charset());
        assertTrue(loaded.bom());
    }

    @Test
    void utf16LeBomIsDetected() {
        byte[] head = {(byte) 0xFF, (byte) 0xFE, 0x3C, 0x00};
        EncodingSniffer.Sniff sniff = EncodingSniffer.sniff(head);
        assertEquals(StandardCharsets.UTF_16LE, sniff.charset());
        assertEquals(2, sniff.bomLength());
    }

    @Test
    void declaredEncodingInXmlPrologWins() {
        byte[] head = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a/>"
                .getBytes(StandardCharsets.ISO_8859_1);
        EncodingSniffer.Sniff sniff = EncodingSniffer.sniff(head);
        assertEquals(Charset.forName("ISO-8859-1"), sniff.charset());
        assertEquals(0, sniff.bomLength());
    }

    @Test
    void unknownDeclaredEncodingFallsBackToUtf8() {
        byte[] head = "<?xml version=\"1.0\" encoding=\"NO-SUCH-CS\"?><a/>"
                .getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(StandardCharsets.UTF_8, EncodingSniffer.sniff(head).charset());
    }

    @Test
    void noPrologDefaultsToUtf8() {
        assertEquals(StandardCharsets.UTF_8,
                EncodingSniffer.sniff("<root/>".getBytes(StandardCharsets.UTF_8)).charset());
    }

    @Test
    void isoLatinFileRoundTrips(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("latin.xml");
        String content = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>äöü</a>";
        Files.write(file, content.getBytes(Charset.forName("ISO-8859-1")));

        EncodingSniffer.Loaded loaded = EncodingSniffer.load(file);
        assertEquals(content, loaded.text());
        assertEquals(Charset.forName("ISO-8859-1"), loaded.charset());
        assertFalse(loaded.bom());
    }

    @Test
    void nulBytesMeanBinaryExceptForUtf16() {
        assertTrue(EncodingSniffer.isBinary(new byte[]{'P', 'K', 0, 3}));
        assertFalse(EncodingSniffer.isBinary(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x3C, 0x00}));
        assertFalse(EncodingSniffer.isBinary("<root/>".getBytes(StandardCharsets.UTF_8)));
    }
}
