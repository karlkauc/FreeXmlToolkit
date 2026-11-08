package org.fxt.freexmltoolkit.service.grammar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import com.thaiopensource.relaxng.edit.SchemaCollection;
import com.thaiopensource.relaxng.input.InputFormat;
import com.thaiopensource.relaxng.input.dtd.DtdInputFormat;
import com.thaiopensource.relaxng.input.parse.compact.CompactParseInputFormat;
import com.thaiopensource.relaxng.input.parse.sax.SAXParseInputFormat;
import com.thaiopensource.relaxng.output.LocalOutputDirectory;
import com.thaiopensource.relaxng.output.OutputDirectory;
import com.thaiopensource.relaxng.output.xsd.XsdOutputFormat;

/**
 * Translates a DTD or a RELAX NG schema into an equivalent XML Schema with Trang, for IntelliSense,
 * the grid's entry helpers and the Inspector, which all work on XSD. Validation keeps using the
 * original grammar ({@link DtdValidator}, {@link RelaxNgValidator}); where RELAX NG says more than XSD
 * can, the XSD is an approximation (Trang's own rules). Results are cached by file content.
 */
public final class GrammarToXsd {

    private GrammarToXsd() {
    }

    /** The folder of the generated schemas ({@code ~/.freeXmlToolkit/cache/grammar-xsd}, or {@code -Dfxt.grammarXsd.dir}). */
    static Path cacheFolder() {
        String configured = System.getProperty("fxt.grammarXsd.dir");
        return configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "cache", "grammar-xsd");
    }

    /**
     * @param grammar a {@code .dtd}, {@code .rng} or {@code .rnc} file
     * @return the main XSD (further XSDs for other namespaces lie next to it)
     * @throws IOException when the grammar cannot be read or translated
     */
    public static Path convert(Path grammar) throws IOException {
        GrammarKind kind = GrammarKind.ofFile(grammar).orElseThrow(() -> new IOException(
                grammar.getFileName() + " is no DTD or RELAX NG schema."));
        return convert(grammar, kind);
    }

    /** @param kind the language of {@code grammar} (also for files without the usual extension) */
    public static Path convert(Path grammar, GrammarKind kind) throws IOException {
        if (kind == GrammarKind.XSD) {
            return grammar;
        }
        String name = grammar.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        Path folder = cacheFolder().resolve(hash(grammar));
        Path xsd = folder.resolve(name + ".xsd");
        if (Files.isRegularFile(xsd)) {
            return xsd;
        }
        Files.createDirectories(folder);
        List<String> errors = new ArrayList<>();
        ErrorHandler handler = new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                // approximations Trang reports are fine for IntelliSense
            }

            @Override
            public void error(SAXParseException e) {
                errors.add(e.getMessage());
            }

            @Override
            public void fatalError(SAXParseException e) {
                errors.add(e.getMessage());
            }
        };
        InputFormat input = switch (kind) {
            case DTD -> new DtdInputFormat();
            case RELAX_NG_COMPACT -> new CompactParseInputFormat();
            default -> new SAXParseInputFormat();
        };
        String inputType = switch (kind) {
            case DTD -> "dtd";
            case RELAX_NG_COMPACT -> "rnc";
            default -> "rng";
        };
        try {
            SchemaCollection schemas = input.load(grammar.toUri().toString(), new String[0], "xsd", handler,
                    new SecureGrammarResolver());
            OutputDirectory out = new LocalOutputDirectory(schemas.getMainUri(), xsd.toFile(), ".xsd", "UTF-8", 100, 2);
            new XsdOutputFormat().output(schemas, out, new String[0], inputType, handler);
        } catch (Exception e) {
            throw new IOException("Cannot translate " + grammar.getFileName() + " to XSD: "
                    + (errors.isEmpty() ? e.getMessage() : String.join("; ", errors)), e);
        }
        if (!Files.isRegularFile(xsd)) {
            throw new IOException("Cannot translate " + grammar.getFileName() + " to XSD"
                    + (errors.isEmpty() ? "." : ": " + String.join("; ", errors)));
        }
        return xsd;
    }

    /**
     * Writes the internal subset of a document's DOCTYPE as a DTD file and translates it (documents
     * whose DTD is only in the document itself).
     */
    public static Path convertInternalSubset(String xml) throws IOException {
        String subset = internalSubset(xml);
        if (subset == null) {
            throw new IOException("The document has no internal DTD subset.");
        }
        Path folder = cacheFolder().resolve("internal");
        Files.createDirectories(folder);
        Path dtd = folder.resolve(sha(subset.getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".dtd");
        if (!Files.exists(dtd)) {
            Files.writeString(dtd, subset, StandardCharsets.UTF_8);
        }
        return convert(dtd, GrammarKind.DTD);
    }

    /** @return the text between {@code [} and {@code ]} of the DOCTYPE, or {@code null} */
    static String internalSubset(String xml) {
        String prolog = DeclaredGrammar.prolog(xml);
        int doctype = prolog.indexOf("<!DOCTYPE");
        if (doctype < 0) {
            return null;
        }
        int open = -1;
        char quote = 0;
        for (int i = doctype; i < prolog.length(); i++) {
            char c = prolog.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                open = i;
                break;
            } else if (c == '>') {
                return null;
            }
        }
        int close = open < 0 ? -1 : prolog.lastIndexOf(']');
        return open < 0 || close < open ? null : prolog.substring(open + 1, close);
    }

    private static String hash(Path file) throws IOException {
        return sha((file.toAbsolutePath().normalize() + "|" + Files.getLastModifiedTime(file).toMillis() + "|"
                + Files.size(file)).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private static String sha(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
