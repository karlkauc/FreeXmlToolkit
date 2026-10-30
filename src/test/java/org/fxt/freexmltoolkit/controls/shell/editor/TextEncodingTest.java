package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** windows-1252 leaves 0x81 undefined: reading it must not produce U+FFFD, or the save fails. */
    @Test
    void undeclaredBytesUndefinedInWindows1252RoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("legacy.txt");
        byte[] original = {'a', (byte) 0x81, 'b', (byte) 0xE4, (byte) 0x80, (byte) 0x9D, '\n'};
        Files.write(file, original);

        TextEncoding.Read read = TextEncoding.read(file);
        assertFalse(read.text().contains("\uFFFD"));
        read.encoding().write(file, read.text(), false);

        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void undeclaredWindows1252TextStaysWindows1252(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cp1252.txt");
        byte[] original = {'1', (byte) 0x80, ' ', (byte) 0x93, 'x', (byte) 0x94};
        Files.write(file, original);

        TextEncoding.Read read = TextEncoding.read(file);
        assertEquals("1\u20AC \u201Cx\u201D", read.text());
        read.encoding().write(file, read.text(), false);

        assertArrayEquals(original, Files.readAllBytes(file));
    }

    /** A declaration the bytes do not match: no U+FFFD, every byte is kept as a character. */
    @Test
    void wronglyDeclaredFileIsReadWithoutLoss(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ascii.xml");
        byte[] original = "<?xml version=\"1.0\" encoding=\"US-ASCII\"?><a>\u00e4</a>"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        Files.write(file, original);

        TextEncoding.Read read = TextEncoding.read(file);
        assertTrue(read.text().contains("<a>\u00e4</a>"), read.text());
    }
}
