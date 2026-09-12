/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.fxt.freexmltoolkit.util;

import java.util.Arrays;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Shared Markdown support for XSD documentation.
 *
 * <p>Lives in {@code util} because the domain model, the documentation services and the
 * shell controls all need it; a {@code domain → service} dependency would be an inversion.
 *
 * <p>The parser/renderer pair is static on purpose: creating them per instance cost several
 * kilobytes per XPath entry and exhausted the heap for large schemas, whose element maps hold
 * hundreds of thousands of entries. Both are immutable and thread-safe once built.
 */
public final class MarkdownSupport {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()));
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    private MarkdownSupport() {
        // utility class
    }

    /**
     * Renders Markdown to HTML (GitHub-flavoured tables and strikethrough, soft breaks kept).
     *
     * @param markdown the Markdown source
     * @return the rendered HTML
     */
    public static String render(String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }

    /**
     * Renders {@code text} as Markdown, or HTML-escapes it when Markdown is off.
     *
     * <p>Escaping the non-Markdown branch is what makes the result safe to emit with
     * {@code th:utext} unconditionally, and keeps literal {@code **bold**} visible as typed.
     *
     * @param text     the documentation text
     * @param markdown whether this node's documentation renders as Markdown
     * @return HTML that is always safe for unescaped output
     */
    public static String renderOrEscape(String text, boolean markdown) {
        if (text == null) {
            return "";
        }
        return markdown ? render(text) : escapeHtml(text);
    }

    /**
     * Reads the {@code @markdown} flag from an {@code xs:annotation} node.
     *
     * <p>Understands both the canonical {@code <xs:appinfo source="@markdown">true</xs:appinfo>}
     * form and the legacy {@code <xs:appinfo source="@markdown true"/>} encoding, because it
     * defers to {@link XsdAppInfo}. When one annotation states the flag more than once, the last
     * explicit value wins.
     *
     * @param annotationNode the {@code xs:annotation} node, may be null
     * @return TRUE/FALSE when the annotation states a preference, null when it says nothing
     */
    public static Boolean markdownFlag(Node annotationNode) {
        if (annotationNode == null) {
            return null;
        }
        Boolean flag = null;
        NodeList children = annotationNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"appinfo".equals(child.getLocalName())) {
                continue;
            }
            String source = attributeValue(child, "source");
            if (!"@markdown".equals(XsdAppInfo.tagOf(source))) {
                continue;
            }
            Boolean parsed = XsdAppInfo.parseBooleanFlag(XsdAppInfo.valueOf(source, child.getTextContent()));
            if (parsed != null) {
                flag = parsed;
            }
        }
        return flag;
    }

    /**
     * Renders (possibly HTML) documentation as plain text for the PDF, Word and Excel exporters:
     * block boundaries ({@code <br>}, {@code </p>}, {@code </div>}, list items, headings) become
     * line breaks so paragraphs and list entries stay separated, remaining tags are dropped,
     * common entities decoded, and intra-line whitespace collapsed.
     *
     * @param html the HTML (or already plain) text
     * @return the plain-text rendering, never null
     */
    public static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        String s = html
                .replaceAll("(?i)<\\s*br\\s*/?>", "\n")
                .replaceAll("(?i)</\\s*(p|div|li|tr|h[1-6])\\s*>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
            if (!t.isEmpty()) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(t);
            }
        }
        return out.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String attributeValue(Node node, String name) {
        if (node.getAttributes() == null) {
            return null;
        }
        Node attr = node.getAttributes().getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }
}
