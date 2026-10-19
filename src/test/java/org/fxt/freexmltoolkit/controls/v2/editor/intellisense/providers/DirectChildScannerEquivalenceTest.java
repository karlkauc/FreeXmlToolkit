package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ScannerTestDocuments;
import org.junit.jupiter.api.Test;

/**
 * {@link DirectChildScanner} now walks the text in place (no stripping of the whole rest of the document
 * per typed '&lt;'). The former implementation is kept here as the oracle: both must agree on random
 * documents, including malformed ones.
 */
class DirectChildScannerEquivalenceTest {

    @Test
    void agreesWithTheFormerRegexImplementation() {
        Random random = new Random(20261019);
        String[] parents = {"root", "a", "b", "item", "order", "data-set", "v1.2"};
        int compared = 0;
        for (int doc = 0; doc < 400; doc++) {
            String xml = ScannerTestDocuments.document(random);
            for (int probe = 0; probe < 12; probe++) {
                int caret = random.nextInt(xml.length() + 1);
                String before = xml.substring(0, caret);
                String after = xml.substring(caret);
                String parent = parents[random.nextInt(parents.length)];
                assertEquals(Legacy.scan(before, after, parent), DirectChildScanner.scan(before, after, parent),
                        () -> "parent " + parent + " at " + caret + " in:\n" + xml);
                compared++;
            }
        }
        assertEquals(400 * 12, compared);
    }

    @Test
    void stopsReadingAtTheParentsEndTag() {
        String before = "<root><a/><b>";
        String after = "<c/><d/></b><e/></root>";
        var siblings = DirectChildScanner.scan(before, after, "b");
        assertEquals(List.of(), siblings.before());
        assertEquals(List.of("c", "d"), siblings.after());
    }

    /** The implementation before 2026-10-19, verbatim. */
    private static final class Legacy {
        private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z_][\\w.:-]*)(?:\\s[^<>]*)?(/?)>");

        static DirectChildScanner.Siblings scan(String textBeforeCaret, String textAfterCaret, String parentLocalName) {
            if (textBeforeCaret == null || textBeforeCaret.isEmpty() || parentLocalName == null || parentLocalName.isEmpty()) {
                return DirectChildScanner.Siblings.NONE;
            }
            int contentStart = findParentContentStart(textBeforeCaret, parentLocalName);
            if (contentStart < 0) {
                return DirectChildScanner.Siblings.NONE;
            }
            List<String> before = directChildren(stripNonMarkup(textBeforeCaret.substring(contentStart)), false);
            List<String> after = textAfterCaret == null || textAfterCaret.isEmpty()
                    ? List.of()
                    : directChildren(stripNonMarkup(textAfterCaret), true);
            return new DirectChildScanner.Siblings(before, after);
        }

        private static int findParentContentStart(String text, String parentLocalName) {
            Pattern open = Pattern.compile("<(?:[\\w.-]+:)?" + Pattern.quote(parentLocalName) + "(?=[\\s>/])");
            Matcher m = open.matcher(text);
            int lastStart = -1;
            while (m.find()) {
                lastStart = m.start();
            }
            if (lastStart < 0) {
                return -1;
            }
            int tagEnd = text.indexOf('>', lastStart);
            return tagEnd < 0 ? -1 : tagEnd + 1;
        }

        private static List<String> directChildren(String content, boolean stopAtParentClose) {
            List<String> names = new ArrayList<>();
            Matcher m = TAG.matcher(content);
            int depth = 0;
            while (m.find()) {
                boolean closing = !m.group(1).isEmpty();
                boolean selfClosing = !m.group(3).isEmpty();
                String name = DirectChildScanner.localName(m.group(2));
                if (closing) {
                    if (depth == 0) {
                        if (stopAtParentClose) {
                            break;
                        }
                        continue;
                    }
                    depth--;
                } else if (selfClosing) {
                    if (depth == 0) {
                        names.add(name);
                    }
                } else {
                    if (depth == 0) {
                        names.add(name);
                    }
                    depth++;
                }
            }
            return names;
        }

        static String stripNonMarkup(String content) {
            return content.replaceAll("<!--[\\s\\S]*?-->", "")
                    .replaceAll("<!\\[CDATA\\[[\\s\\S]*?]]>", "")
                    .replaceAll("<\\?[\\s\\S]*?\\?>", "");
        }
    }
}
