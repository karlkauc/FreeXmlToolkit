package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter for XML text.
 *
 * <p>Provides pattern-based syntax highlighting for XML elements including:</p>
 * <ul>
 *   <li>XML declaration (&lt;?xml ... ?&gt;)</li>
 *   <li>Element tags (&lt;tag&gt;, &lt;/tag&gt;)</li>
 *   <li>Attributes (name="value")</li>
 *   <li>Comments (&lt;!-- ... --&gt;)</li>
 *   <li>CDATA sections (&lt;![CDATA[ ... ]]&gt;)</li>
 *   <li>Processing instructions (&lt;?target data?&gt;)</li>
 *   <li>Entity references (&amp;amp;, &amp;lt;, etc.)</li>
 * </ul>
 *
 * <p>CSS Classes for styling:</p>
 * <ul>
 *   <li>.xml-declaration - XML declaration</li>
 *   <li>.xml-tag - Element tag names</li>
 *   <li>.xml-bracket - Angle brackets</li>
 *   <li>.xml-attribute - Attribute names</li>
 *   <li>.xml-attribute-value - Attribute values</li>
 *   <li>.xml-comment - Comments</li>
 *   <li>.xml-cdata - CDATA sections</li>
 *   <li>.xml-pi - Processing instructions</li>
 *   <li>.xml-entity - Entity references</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlSyntaxHighlighter {

    /**
     * Pattern groups for different XML elements.
     */
    private static final String XML_DECLARATION_PATTERN = "(<\\?xml[^?]*\\?>)";
    private static final String PROCESSING_INSTRUCTION_PATTERN = "(<\\?[a-zA-Z][^?]*\\?>)";
    private static final String COMMENT_PATTERN = "(<!--.*?-->)";
    private static final String CDATA_PATTERN = "(<!\\[CDATA\\[.*?\\]\\]>)";
    private static final String ELEMENT_PATTERN = "(</?\\s*[a-zA-Z][a-zA-Z0-9_:.-]*)" + // Opening tag
            "|" +
            "(\\s*/?>)"; // Closing bracket
    private static final String ATTRIBUTE_PATTERN = "([a-zA-Z][a-zA-Z0-9_:.-]*)\\s*=\\s*\"([^\"]*)\"";
    private static final String ENTITY_PATTERN = "(&[a-zA-Z]+;|&#[0-9]+;|&#x[0-9a-fA-F]+;)";

    /**
     * Combined pattern for all XML elements.
     */
    private static final Pattern PATTERN = Pattern.compile(
            "(?<XMLDECL>" + XML_DECLARATION_PATTERN + ")" +
                    "|(?<PI>" + PROCESSING_INSTRUCTION_PATTERN + ")" +
                    "|(?<COMMENT>" + COMMENT_PATTERN + ")" +
                    "|(?<CDATA>" + CDATA_PATTERN + ")" +
                    "|(?<ELEMENT>" + ELEMENT_PATTERN + ")" +
                    "|(?<ATTRIBUTE>" + ATTRIBUTE_PATTERN + ")" +
                    "|(?<ENTITY>" + ENTITY_PATTERN + ")",
            Pattern.DOTALL
    );

    /**
     * Computes syntax highlighting for the given text.
     *
     * @param text the XML text to highlight
     * @return StyleSpans for the text
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = null;

            if (matcher.group("XMLDECL") != null) {
                styleClass = "xml-declaration";
            } else if (matcher.group("PI") != null) {
                styleClass = "xml-pi";
            } else if (matcher.group("COMMENT") != null) {
                styleClass = "xml-comment";
            } else if (matcher.group("CDATA") != null) {
                styleClass = "xml-cdata";
            } else if (matcher.group("ELEMENT") != null) {
                // Handle element tags and brackets
                String matched = matcher.group();
                if (matched.matches("\\s*/?>")) {
                    styleClass = "xml-bracket";
                } else {
                    styleClass = "xml-tag";
                }
            } else if (matcher.group("ATTRIBUTE") != null) {
                // Attribute pattern has two groups: name and value
                // We need to highlight them separately
                handleAttribute(matcher, lastKwEnd, spansBuilder);
                lastKwEnd = matcher.end();
                continue;
            } else if (matcher.group("ENTITY") != null) {
                styleClass = "xml-entity";
            }

            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastKwEnd = matcher.end();
            }
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Handles attribute highlighting (name and value separately).
     */
    private static void handleAttribute(Matcher matcher, int lastKwEnd, StyleSpansBuilder<Collection<String>> spansBuilder) {
        String fullMatch = matcher.group();
        int attrStart = matcher.start();

        // Parse attribute manually to get name and value positions
        Pattern attrPattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9_:.-]*)\\s*=\\s*\"([^\"]*)\"");
        Matcher attrMatcher = attrPattern.matcher(fullMatch);

        if (attrMatcher.find()) {
            int nameStart = attrStart + attrMatcher.start(1);
            int nameEnd = attrStart + attrMatcher.end(1);
            int valueStart = attrStart + attrMatcher.start(2);
            int valueEnd = attrStart + attrMatcher.end(2);

            // Add gap before attribute
            if (nameStart > lastKwEnd) {
                spansBuilder.add(Collections.emptyList(), nameStart - lastKwEnd);
            }

            // Add attribute name
            spansBuilder.add(Collections.singleton("xml-attribute"), nameEnd - nameStart);

            // Add gap between name and value (=, spaces, quotes)
            if (valueStart > nameEnd) {
                spansBuilder.add(Collections.emptyList(), valueStart - nameEnd);
            }

            // Add attribute value (without quotes)
            spansBuilder.add(Collections.singleton("xml-attribute-value"), valueEnd - valueStart);

            // Add closing quote
            if (matcher.end() > valueEnd) {
                spansBuilder.add(Collections.emptyList(), matcher.end() - valueEnd);
            }
        }
    }

    /**
     * Returns the default CSS stylesheet for XML syntax highlighting.
     *
     * @return CSS stylesheet string
     */
    public static String getDefaultStylesheet() {
        return """
                .xml-declaration {
                    -fx-fill: #880088;
                    -fx-font-weight: bold;
                }
                
                .xml-tag {
                    -fx-fill: #000080;
                    -fx-font-weight: bold;
                }
                
                .xml-bracket {
                    -fx-fill: #000080;
                }
                
                .xml-attribute {
                    -fx-fill: #FF0000;
                }
                
                .xml-attribute-value {
                    -fx-fill: #008000;
                }
                
                .xml-comment {
                    -fx-fill: #808080;
                    -fx-font-style: italic;
                }
                
                .xml-cdata {
                    -fx-fill: #660066;
                }
                
                .xml-pi {
                    -fx-fill: #880088;
                }
                
                .xml-entity {
                    -fx-fill: #0000FF;
                }
                """;
    }

    /**
     * Returns a dark theme CSS stylesheet for XML syntax highlighting.
     *
     * @return CSS stylesheet string for dark theme
     */
    public static String getDarkThemeStylesheet() {
        return """
                .xml-declaration {
                    -fx-fill: #D197D9;
                    -fx-font-weight: bold;
                }
                
                .xml-tag {
                    -fx-fill: #569CD6;
                    -fx-font-weight: bold;
                }
                
                .xml-bracket {
                    -fx-fill: #808080;
                }
                
                .xml-attribute {
                    -fx-fill: #9CDCFE;
                }
                
                .xml-attribute-value {
                    -fx-fill: #CE9178;
                }
                
                .xml-comment {
                    -fx-fill: #6A9955;
                    -fx-font-style: italic;
                }
                
                .xml-cdata {
                    -fx-fill: #D197D9;
                }
                
                .xml-pi {
                    -fx-fill: #D197D9;
                }
                
                .xml-entity {
                    -fx-fill: #4EC9B0;
                }
                """;
    }
}
