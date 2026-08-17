package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects the character encoding of an XML-family text file so search results
 * and replacements round-trip without corrupting non-UTF-8 files: BOM first
 * (UTF-8 / UTF-16LE / UTF-16BE), then the {@code encoding="…"} pseudo-attribute
 * of an XML declaration, defaulting to UTF-8.
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
        String prefix = new String(head, 0, Math.min(head.length, 256), StandardCharsets.ISO_8859_1);
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
        byte[] bytes = Files.readAllBytes(file);
        Sniff sniff = sniff(bytes);
        String text = new String(bytes, sniff.bomLength(), bytes.length - sniff.bomLength(),
                sniff.charset());
        return new Loaded(text, sniff.charset(), sniff.bomLength() > 0);
    }
}
