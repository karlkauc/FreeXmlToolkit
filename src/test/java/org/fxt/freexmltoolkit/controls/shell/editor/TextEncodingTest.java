package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading and writing documents in their own encoding. */
class TextEncodingTest {

    @Test
    void utf16WithBomRoundTrips(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("u16.xml");
        byte[] original = concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "<a>ü</a>".getBytes(StandardCharsets.UTF_16LE));
        Files.write(file, original);

        TextEncoding.Read read = TextEncoding.read(file);
        assertEquals("<a>ü</a>", read.text());
        assertEquals(new TextEncoding(StandardCharsets.UTF_16LE, true), read.encoding());

        read.encoding().write(file, read.text(), true);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void anEditedDeclarationDecidesTheCharsetOnSave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("doc.xml");
        TextEncoding latin1 = new TextEncoding(StandardCharsets.ISO_8859_1, false);
        String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a>ü</a>";

        TextEncoding written = latin1.write(file, text, true);
        assertEquals(StandardCharsets.UTF_8, written.charset());
        assertEquals(text, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void unmappableCharactersBecomeCharacterReferencesInXml(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("doc.xml");
        TextEncoding latin1 = new TextEncoding(StandardCharsets.ISO_8859_1, false);

        latin1.write(file, "<a>5 € ü</a>", true);
        assertEquals("<a>5 &#x20AC; ü</a>", Files.readString(file, StandardCharsets.ISO_8859_1));
    }

    @Test
    void unmappableCharactersFailOutsideXml() {
        TextEncoding latin1 = new TextEncoding(StandardCharsets.ISO_8859_1, false);
        assertThrows(CharacterCodingException.class, () -> latin1.encode("5 €", false));
    }

    @Test
    void newDocumentsAreUtf8WithoutBom() throws Exception {
        assertTrue(TextEncoding.DEFAULT.encode("ü", false).length == 2);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] all = new byte[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }
}
