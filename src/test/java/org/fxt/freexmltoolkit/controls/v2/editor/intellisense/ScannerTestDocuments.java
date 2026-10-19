package org.fxt.freexmltoolkit.controls.v2.editor.intellisense;

import java.util.Random;

/**
 * Random XML-ish documents for the scanner equivalence tests: nested and prefixed elements, attributes
 * (including xmlns declarations), self-closing tags, text, comments, CDATA sections and processing
 * instructions (some containing markup), plus the malformed bits an editor sees while the user types
 * (unterminated constructs, mismatched end tags, a dangling '<').
 */
public final class ScannerTestDocuments {

    private static final String[] NAMES = {"root", "a", "b", "item", "order", "ns:item", "x:a", "data-set", "v1.2"};
    private static final String[] PREFIXES = {"ns", "x", "p.q", "my-ns"};

    private ScannerTestDocuments() {
    }

    public static String document(Random random) {
        StringBuilder xml = new StringBuilder();
        if (random.nextBoolean()) {
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        appendElement(xml, random, 0);
        if (random.nextInt(4) == 0) {
            xml.append(junk(random));
        }
        return xml.toString();
    }

    private static void appendElement(StringBuilder xml, Random random, int depth) {
        String name = NAMES[random.nextInt(NAMES.length)];
        xml.append('<').append(name);
        int attributes = random.nextInt(3);
        for (int i = 0; i < attributes; i++) {
            xml.append(random.nextBoolean() ? " " : "\n    ");
            switch (random.nextInt(4)) {
                case 0 -> xml.append("xmlns=\"urn:default").append(random.nextInt(3)).append('"');
                case 1 -> {
                    char quote = random.nextBoolean() ? '\'' : '"';
                    xml.append("xmlns:").append(PREFIXES[random.nextInt(PREFIXES.length)])
                            .append('=').append(quote).append("urn:p").append(random.nextInt(3)).append(quote);
                }
                default -> xml.append("attr").append(i).append("=\"v").append(random.nextInt(9)).append('"');
            }
        }
        if (depth > 4 || random.nextInt(5) == 0) {
            xml.append(random.nextBoolean() ? "/>" : " />");
            return;
        }
        xml.append('>');
        int children = random.nextInt(4);
        for (int i = 0; i < children; i++) {
            xml.append(random.nextBoolean() ? "\n  " : "");
            switch (random.nextInt(7)) {
                case 0 -> xml.append("text ").append(random.nextInt(100));
                case 1 -> xml.append("<!-- note <b>tag in comment</b> xmlns:c=\"urn:c\" -->");
                case 2 -> xml.append("<![CDATA[ <a>not a tag</a> xmlns:d=\"urn:d\" ]]>");
                case 3 -> xml.append("<?pi data <c/> ?>");
                default -> appendElement(xml, random, depth + 1);
            }
        }
        if (random.nextInt(12) == 0) {
            xml.append("</mismatch>");
        } else if (random.nextInt(15) != 0) { // sometimes left open, as while typing
            xml.append("</").append(name).append(random.nextInt(6) == 0 ? " " : "").append('>');
        }
    }

    private static String junk(Random random) {
        return switch (random.nextInt(6)) {
            case 0 -> "<!-- unterminated comment <a>";
            case 1 -> "<![CDATA[ unterminated <b>";
            case 2 -> "<? unterminated pi";
            case 3 -> "<!-->";
            case 4 -> "<";
            default -> "<ns:item a=\"1\"";
        };
    }
}
