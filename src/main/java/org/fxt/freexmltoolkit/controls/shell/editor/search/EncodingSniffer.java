package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects the character encoding of an XML-family text file so search results
 * and replacements round-trip without corrupting non-UTF-8 files: BOM first
 * (UTF-8 / UTF-16LE / UTF-16BE), then the {@code encoding="…"} pseudo-attribute
 * of an XML declaration, defaulting to UTF-8. Bytes that are not valid UTF-8 although
 * no BOM says so (an undeclared or wrongly declared Latin-1 file) are read as windows-1252,
 * which maps every byte, so writing the text back restores the file unchanged.
 */
public final class EncodingSniffer {

    private EncodingSniffer() {
    }

    /** The detected charset plus the byte length of a leading BOM (0 if none). */
    public record Sniff(Charset charset, int bomLength) {
    }

    /** A fully decoded file: text without BOM, plus what is needed to write it back. */
    public record Loaded(String text, Charset charset, boolean bom) {
    }

    /** Sniffs the charset from the first bytes of a file (pass at least ~256 bytes). */
    public static Sniff sniff(byte[] head) {
        if (head.length >= 3
                && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            return new Sniff(StandardCharsets.UTF_8, 3);
        }
        if (head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
            return new Sniff(StandardCharsets.UTF_16LE, 2);
        }
        if (head.length >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF) {
            return new Sniff(StandardCharsets.UTF_16BE, 2);
        }
        Charset declared = declaredEncoding(head);
        return new Sniff(declared != null ? declared : StandardCharsets.UTF_8, 0);
    }

    /**
     * Reads the {@code encoding} pseudo-attribute from an XML declaration at the
     * start of {@code head} (the declaration itself is ASCII-compatible), or
     * {@code null} if absent or the name is unknown to this JVM.
     */
    private static Charset declaredEncoding(byte[] head) {
        return declaredEncoding(new String(head, 0, Math.min(head.length, 256), StandardCharsets.ISO_8859_1));
    }

    /**
     * The charset named by the XML declaration at the start of {@code text}, or {@code null} if there
     * is none or the name is unknown to this JVM.
     */
    public static Charset declaredEncoding(String text) {
        String prefix = text.length() > 256 ? text.substring(0, 256) : text;
        if (!prefix.startsWith("<?xml")) {
            return null;
        }
        int end = prefix.indexOf("?>");
        String decl = end >= 0 ? prefix.substring(0, end) : prefix;
        var matcher = java.util.regex.Pattern
                .compile("encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']")
                .matcher(decl);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Charset.forName(matcher.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    /** @return true if the head bytes look binary (NUL byte outside a UTF-16 BOM'd file). */
    public static boolean isBinary(byte[] head) {
        Sniff sniff = sniff(head);
        if (sniff.charset() == StandardCharsets.UTF_16LE || sniff.charset() == StandardCharsets.UTF_16BE) {
            return false; // UTF-16 text legitimately contains NUL bytes
        }
        int limit = Math.min(head.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (head[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** Reads and decodes {@code file} with the sniffed charset, stripping a BOM. */
    public static Loaded load(Path file) throws IOException {
        return decode(Files.readAllBytes(file));
    }

    /** Decodes a whole file's bytes with the sniffed charset, stripping a BOM. */
    public static Loaded decode(byte[] bytes) {
        Sniff sniff = sniff(bytes);
        int offset = sniff.bomLength();
        int length = bytes.length - offset;
        if (StandardCharsets.UTF_8.equals(sniff.charset()) && offset == 0 && !isValidUtf8(bytes)) {
            return new Loaded(new String(bytes, WINDOWS_1252), WINDOWS_1252, false);
        }
        return new Loaded(new String(bytes, offset, length, sniff.charset()), sniff.charset(), offset > 0);
    }

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
