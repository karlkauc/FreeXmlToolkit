package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ScannerTestDocuments;
import org.junit.jupiter.api.Test;

/**
 * {@link XmlNamespaceDeclarations#scan} and the XPath stack of {@link ContextAnalyzer} now scan the text
 * in place (no cleaned copies, no substring per tag). The former implementations are the oracle: both
 * must agree on random documents, including malformed ones.
 *
 * <p>One deliberate difference: an attribute value no longer runs across a comment, CDATA section or
 * processing instruction. With mismatched quotes the former regex read whole stretches of the document
 * as one namespace URI; well-formed XML cannot contain such a value, so the generator quotes properly.</p>
 */
class ScannerEquivalenceTest {

    @Test
    void namespaceScanAgreesWithTheFormerRegexImplementation() {
        Random random = new Random(1019);
        for (int doc = 0; doc < 500; doc++) {
            String xml = ScannerTestDocuments.document(random);
            for (int probe = 0; probe < 6; probe++) {
                String prefix = xml.substring(0, random.nextInt(xml.length() + 1));
                assertEquals(legacyNamespaces(prefix), XmlNamespaceDeclarations.scan(prefix).bindings(),
                        () -> "in:\n" + prefix);
            }
        }
    }

    @Test
    void xpathStackAgreesWithTheFormerSubstringImplementation() {
        Random random = new Random(2026);
        for (int doc = 0; doc < 500; doc++) {
            String xml = ScannerTestDocuments.document(random);
            for (int probe = 0; probe < 10; probe++) {
                int caret = random.nextInt(xml.length() + 1);
                XmlContext context = ContextAnalyzer.analyze(xml, caret);
                if (context.getXPathContext() == null) {
                    continue; // comment/CDATA/PI contexts carry no element stack
                }
                assertEquals(legacyXPath(xml, caret), context.getXPath(), () -> "at " + caret + " in:\n" + xml);
            }
        }
    }

    // ---- the implementations before 2026-10-19, verbatim ---------------------------------------------

    private static final Pattern XMLNS =
            Pattern.compile("\\sxmlns(?::([\\w.-]+))?\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private static Map<String, String> legacyNamespaces(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }
        String cleaned = text.replaceAll("<!--[\\s\\S]*?(?:-->|$)", " ")
                .replaceAll("<!\\[CDATA\\[[\\s\\S]*?(?:]]>|$)", " ")
                .replaceAll("<\\?[\\s\\S]*?(?:\\?>|$)", " ");
        Matcher m = XMLNS.matcher(cleaned);
        while (m.find()) {
            String prefix = m.group(1) == null ? "" : m.group(1);
            String uri = m.group(2) != null ? m.group(2) : m.group(3);
            map.put(prefix, uri);
        }
        return map;
    }

    private static String legacyXPath(String fullText, int caretPosition) {
        int safeCaret = Math.max(0, Math.min(caretPosition, fullText.length()));
        Stack<String> elementStack = new Stack<>();
        int pos = 0;
        while (pos < safeCaret) {
            int nextOpen = fullText.indexOf('<', pos);
            if (nextOpen == -1 || nextOpen >= safeCaret) {
                break;
            }
            int nextClose = fullText.indexOf('>', nextOpen);
            if (nextClose == -1 || nextClose >= safeCaret) {
                int fullClose = fullText.indexOf('>', nextOpen);
                int nextTagStart = fullText.indexOf('<', nextOpen + 1);
                if (nextTagStart != -1 && nextTagStart < fullClose) {
                    fullClose = nextTagStart;
                }
                if (fullClose != -1) {
                    String tag = fullText.substring(nextOpen + 1, fullClose);
                    if (!tag.startsWith("!--") && !tag.startsWith("![CDATA[") && !tag.startsWith("?")
                            && !tag.startsWith("/") && !tag.endsWith("/")) {
                        String elementName = extractElementName(tag);
                        if (elementName != null && !elementName.isEmpty()) {
                            elementStack.push(elementName);
                        }
                    }
                }
                break;
            }
            String tag = fullText.substring(nextOpen + 1, nextClose);
            if (tag.startsWith("!--") || tag.startsWith("![CDATA[") || tag.startsWith("?")) {
                pos = nextClose + 1;
                continue;
            }
            if (tag.startsWith("/")) {
                String elementName = tag.substring(1).trim();
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            } else if (!tag.endsWith("/")) {
                String elementName = extractElementName(tag);
                if (elementName != null) {
                    elementStack.push(elementName);
                }
            }
            pos = nextClose + 1;
        }
        return new XPathContext(new java.util.ArrayList<>(elementStack)).getXPath();
    }

    private static String extractElementName(String tag) {
        int end = 0;
        while (end < tag.length() && !Character.isWhitespace(tag.charAt(end))) {
            end++;
        }
        return tag.substring(0, end).trim();
    }
}
