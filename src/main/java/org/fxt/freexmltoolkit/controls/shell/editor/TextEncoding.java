package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.fxt.freexmltoolkit.controls.shell.editor.search.EncodingSniffer;

/**
 * How a document's text is stored on disk: the charset and whether the file starts with a byte
 * order mark. Files are read in the encoding they declare (see {@link EncodingSniffer}) and saved
 * back in it, so opening and saving a non-UTF-8 file leaves its bytes intact.
 */
public record TextEncoding(Charset charset, boolean bom) {

    /** New documents and files without a declaration: UTF-8 without BOM. */
    public static final TextEncoding DEFAULT = new TextEncoding(StandardCharsets.UTF_8, false);

    public TextEncoding {
        Objects.requireNonNull(charset, "charset");
    }

    /** A file's text together with the encoding it was read in. */
    public record Read(String text, TextEncoding encoding) {
    }

    /** Reads {@code path} in its BOM / declared encoding (call off the FX thread). */
    public static Read read(Path path) throws IOException {
        EncodingSniffer.Loaded loaded = EncodingSniffer.load(path);
        return new Read(loaded.text(), new TextEncoding(loaded.charset(), loaded.bom()));
    }

    /**
     * Writes {@code text} to {@code path}. An XML declaration in the text names the charset (so editing
     * {@code encoding="…"} takes effect on save); otherwise this encoding's charset is used. The BOM is
     * kept when the charset stays the same. Characters the charset cannot hold are written as XML
     * character references ({@code &#x20AC;}) in XML-family text; elsewhere the write fails.
     *
     * @return the encoding the file now has
     */
    public TextEncoding write(Path path, String text, boolean xml) throws IOException {
        TextEncoding target = forSaving(text);
        Files.write(path, target.encode(text, xml));
        return target;
    }

    /** The encoding {@link #write} uses for {@code text}. */
    TextEncoding forSaving(String text) {
        Charset declared = EncodingSniffer.declaredEncoding(text);
        if (declared == null || declared.equals(charset)) {
            return this;
        }
        return new TextEncoding(declared, false);
    }

    byte[] encode(String text, boolean xml) throws CharacterCodingException {
        CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String content = xml && !encoder.canEncode(text) ? withCharacterReferences(text, encoder) : text;
        ByteBuffer body = encoder.reset().encode(CharBuffer.wrap(content));
        byte[] mark = bom ? byteOrderMark() : new byte[0];
        byte[] bytes = new byte[mark.length + body.remaining()];
        System.arraycopy(mark, 0, bytes, 0, mark.length);
        body.get(bytes, mark.length, body.remaining());
        return bytes;
    }

    private byte[] byteOrderMark() {
        if (StandardCharsets.UTF_8.equals(charset)) {
            return new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        }
        if (StandardCharsets.UTF_16LE.equals(charset)) {
            return new byte[] {(byte) 0xFF, (byte) 0xFE};
        }
        if (StandardCharsets.UTF_16BE.equals(charset)) {
            return new byte[] {(byte) 0xFE, (byte) 0xFF};
        }
        return new byte[0];
    }

    private static String withCharacterReferences(String text, CharsetEncoder encoder) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        text.codePoints().forEach(cp -> {
            String ch = Character.toString(cp);
            if (encoder.canEncode(ch)) {
                sb.append(ch);
            } else {
                sb.append("&#x").append(Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT)).append(';');
            }
        });
        return sb.toString();
    }
}
