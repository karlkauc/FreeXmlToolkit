package org.fxt.freexmltoolkit.service.yaml;

import java.util.List;

import org.fxt.freexmltoolkit.service.DataFormatConverter;

import com.google.gson.JsonElement;

/**
 * Formats YAML: block style, a fixed indentation, quotes only where needed - by reading the data
 * and writing it again ({@link DataFormatConverter#writeYaml}). That is only done when nothing is
 * lost: a text with comments, anchors / aliases, tags or directives is refused, as the data model
 * does not keep them. Documents ({@code ---}) stay separate. UI-free.
 */
public final class YamlFormatter {

    private YamlFormatter() {
    }

    /** The text has something formatting would lose; the message says what. */
    public static final class NotFormattableException extends Exception {
        public NotFormattableException(String message) {
            super(message);
        }
    }

    /**
     * @param text   the YAML text
     * @param indent spaces per level (2 at least)
     * @return the formatted text
     * @throws NotFormattableException when formatting would lose comments, anchors, tags or directives
     * @throws DataFormatConverter.ConversionException when the text is not valid YAML
     */
    public static String format(String text, int indent)
            throws NotFormattableException, DataFormatConverter.ConversionException {
        String lost = lostByFormatting(text);
        if (lost != null) {
            throw new NotFormattableException("Formatting would remove the " + lost + " of the YAML document.");
        }
        List<JsonElement> documents = DataFormatConverter.parseYamlDocuments(text);
        DataFormatConverter.Options options = DataFormatConverter.Options.DEFAULTS.withIndent(Math.max(2, indent));
        StringBuilder out = new StringBuilder();
        boolean marker = text.stripLeading().startsWith("---") || documents.size() > 1;
        for (JsonElement document : documents) {
            if (marker) {
                out.append("---\n");
            }
            out.append(DataFormatConverter.writeYaml(document, options));
        }
        return out.toString();
    }

    /**
     * @param text the YAML text
     * @return what formatting would lose ("comments", "anchors and aliases", …), or {@code null} for nothing
     */
    public static String lostByFormatting(String text) {
        for (YamlTokenizer.Token token : YamlTokenizer.tokenize(text)) {
            switch (token.kind()) {
                case COMMENT -> {
                    return "comments";
                }
                case ANCHOR, ALIAS -> {
                    return "anchors and aliases";
                }
                case TAG -> {
                    return "tags";
                }
                case DIRECTIVE -> {
                    if (text.charAt(token.start()) == '%') {
                        return "directives";
                    }
                }
                default -> {
                    // kept
                }
            }
        }
        return null;
    }
}
