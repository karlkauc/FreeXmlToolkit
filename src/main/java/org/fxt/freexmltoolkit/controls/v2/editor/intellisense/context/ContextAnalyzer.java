package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes XML text to determine the context at a specific cursor position.
 * This is the core component for context-sensitive IntelliSense.
 *
 * <p>Thread-safe and stateless - all methods are static.</p>
 */
public class ContextAnalyzer {

    private static final Logger logger = LogManager.getLogger(ContextAnalyzer.class);

    // Regex patterns for XML parsing
    private static final Pattern OPENING_TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:-]*)([^/>]*)(?<!/)>");
    private static final Pattern CLOSING_TAG_PATTERN = Pattern.compile("</([a-zA-Z][a-zA-Z0-9_:-]*)>");
    private static final Pattern ELEMENT_NAME_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:-]*)");

    private ContextAnalyzer() {
        // Utility class - no instantiation
    }

    /**
     * Analyzes the XML context at a specific cursor position.
     *
     * @param text           the full XML text
     * @param caretPosition  the cursor position
     * @return the XML context
     */
    public static XmlContext analyze(String text, int caretPosition) {
        if (text == null || text.isEmpty()) {
            return createEmptyContext(caretPosition);
        }

        // Ensure caret position is within bounds
        int safeCaretPos = Math.max(0, Math.min(caretPosition, text.length()));
        String textBeforeCaret = text.substring(0, safeCaretPos);

        XmlContext.Builder builder = new XmlContext.Builder()
                .caretPosition(safeCaretPos)
                .textBeforeCaret(textBeforeCaret);

        // Check for special contexts first
        if (isInComment(textBeforeCaret)) {
            logger.debug("Context: In comment");
            return builder.type(ContextType.COMMENT).inComment(true).build();
        }

        if (isInCData(textBeforeCaret)) {
            logger.debug("Context: In CDATA");
            return builder.type(ContextType.CDATA).inCData(true).build();
        }

        if (isInProcessingInstruction(textBeforeCaret)) {
            logger.debug("Context: In processing instruction");
            return builder.type(ContextType.PROCESSING_INSTRUCTION).build();
        }

        // Build element stack and XPath context
        XPathContext xpathContext = buildXPathContext(textBeforeCaret);
        builder.xpathContext(xpathContext);

        // Determine context type and related information
        ContextType contextType = determineContextType(textBeforeCaret, safeCaretPos);
        builder.type(contextType);

        // Set parent element from XPath
        if (xpathContext.getParentElement() != null) {
            builder.parentElement(xpathContext.getParentElement());
        }

        // Set current element based on context
        switch (contextType) {
            case ELEMENT -> {
                String currentElem = getCurrentElementName(textBeforeCaret);
                builder.currentElement(currentElem);
                builder.completionStartPosition(findElementCompletionStart(textBeforeCaret));
            }
            case ATTRIBUTE -> {
                String elementName = getElementNameForAttributes(textBeforeCaret);
                builder.currentElement(elementName);
                builder.completionStartPosition(findAttributeCompletionStart(textBeforeCaret));
            }
            case ATTRIBUTE_VALUE -> {
                String elementName = getElementNameForAttributes(textBeforeCaret);
                String attrName = getCurrentAttributeName(textBeforeCaret);
                builder.currentElement(elementName);
                builder.currentAttribute(attrName);
                builder.completionStartPosition(findAttributeValueCompletionStart(textBeforeCaret));
            }
            case TEXT_CONTENT -> {
                String elementName = xpathContext.getCurrentElement();
                builder.currentElement(elementName);
                builder.completionStartPosition(findTextContentCompletionStart(textBeforeCaret));
            }
        }

        XmlContext context = builder.build();
        logger.debug("Analyzed context: {}", context);
        return context;
    }

    /**
     * Creates an empty context for empty text.
     */
    private static XmlContext createEmptyContext(int caretPosition) {
        return new XmlContext.Builder()
                .caretPosition(caretPosition)
                .type(ContextType.ELEMENT)
                .xpathContext(new XPathContext(new ArrayList<>()))
                .build();
    }

    /**
     * Checks if the cursor is inside a comment.
     */
    private static boolean isInComment(String textBeforeCaret) {
        int lastCommentStart = textBeforeCaret.lastIndexOf("<!--");
        int lastCommentEnd = textBeforeCaret.lastIndexOf("-->");
        return lastCommentStart > lastCommentEnd;
    }

    /**
     * Checks if the cursor is inside a CDATA section.
     */
    private static boolean isInCData(String textBeforeCaret) {
        int lastCDataStart = textBeforeCaret.lastIndexOf("<![CDATA[");
        int lastCDataEnd = textBeforeCaret.lastIndexOf("]]>");
        return lastCDataStart > lastCDataEnd;
    }

    /**
     * Checks if the cursor is inside a processing instruction.
     */
    private static boolean isInProcessingInstruction(String textBeforeCaret) {
        int lastPIStart = textBeforeCaret.lastIndexOf("<?");
        int lastPIEnd = textBeforeCaret.lastIndexOf("?>");
        return lastPIStart > lastPIEnd;
    }

    /**
     * Builds the XPath context by parsing the element hierarchy.
     */
    private static XPathContext buildXPathContext(String textBeforeCaret) {
        Stack<String> elementStack = new Stack<>();

        int pos = 0;
        while (pos < textBeforeCaret.length()) {
            int nextOpen = textBeforeCaret.indexOf('<', pos);
            if (nextOpen == -1) break;

            int nextClose = textBeforeCaret.indexOf('>', nextOpen);
            if (nextClose == -1) break;

            String tag = textBeforeCaret.substring(nextOpen + 1, nextClose);

            // Skip comments, CDATA, processing instructions
            if (tag.startsWith("!--") || tag.startsWith("![CDATA[") || tag.startsWith("?")) {
                pos = nextClose + 1;
                continue;
            }

            if (tag.startsWith("/")) {
                // Closing tag
                String elementName = tag.substring(1).trim();
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            } else if (!tag.endsWith("/")) {
                // Opening tag (not self-closing)
                String elementName = extractElementName(tag);
                if (elementName != null) {
                    elementStack.push(elementName);
                }
            }

            pos = nextClose + 1;
        }

        return new XPathContext(new ArrayList<>(elementStack));
    }

    /**
     * Extracts the element name from a tag string.
     */
    private static String extractElementName(String tag) {
        // Extract name before space or end of string
        int spaceIndex = tag.indexOf(' ');
        if (spaceIndex != -1) {
            return tag.substring(0, spaceIndex);
        }
        return tag.trim();
    }

    /**
     * Determines the context type at the cursor position.
     */
    private static ContextType determineContextType(String textBeforeCaret, int caretPosition) {
        // Check if we just typed '<' for element completion
        if (textBeforeCaret.endsWith("<")) {
            return ContextType.ELEMENT;
        }

        // Find the last '<' and '>'
        int lastOpenBracket = textBeforeCaret.lastIndexOf('<');
        int lastCloseBracket = textBeforeCaret.lastIndexOf('>');

        // If last '<' is after last '>', we're inside a tag
        if (lastOpenBracket > lastCloseBracket) {
            String tagContent = textBeforeCaret.substring(lastOpenBracket + 1);

            // Skip closing tags
            if (tagContent.startsWith("/")) {
                return ContextType.UNKNOWN;
            }

            // Check if we're in attribute value (inside quotes)
            if (isInAttributeValue(tagContent)) {
                return ContextType.ATTRIBUTE_VALUE;
            }

            // Check if we're in attribute name position
            if (isInAttributePosition(tagContent)) {
                return ContextType.ATTRIBUTE;
            }

            // Still typing element name
            return ContextType.ELEMENT;
        }

        // Outside tags - in text content
        return ContextType.TEXT_CONTENT;
    }

    /**
     * Checks if cursor is inside attribute value (within quotes).
     */
    private static boolean isInAttributeValue(String tagContent) {
        int lastDoubleQuote = tagContent.lastIndexOf('"');
        int lastSingleQuote = tagContent.lastIndexOf('\'');
        int lastQuote = Math.max(lastDoubleQuote, lastSingleQuote);

        if (lastQuote == -1) return false;

        // Count quotes before last quote to see if we're inside
        char quoteChar = lastDoubleQuote > lastSingleQuote ? '"' : '\'';
        int quoteCount = 0;
        for (int i = 0; i < tagContent.length(); i++) {
            if (tagContent.charAt(i) == quoteChar) {
                quoteCount++;
            }
        }

        // Odd number of quotes means we're inside
        return quoteCount % 2 == 1;
    }

    /**
     * Checks if cursor is in attribute name position (space after element name).
     */
    private static boolean isInAttributePosition(String tagContent) {
        // Must have at least one space (after element name)
        return tagContent.contains(" ");
    }

    /**
     * Gets the current element name when typing after '<'.
     */
    private static String getCurrentElementName(String textBeforeCaret) {
        int lastOpenBracket = textBeforeCaret.lastIndexOf('<');
        if (lastOpenBracket == -1 || lastOpenBracket == textBeforeCaret.length() - 1) {
            return null;
        }

        String afterBracket = textBeforeCaret.substring(lastOpenBracket + 1);
        return extractElementName(afterBracket);
    }

    /**
     * Gets the element name when in attribute context.
     */
    private static String getElementNameForAttributes(String textBeforeCaret) {
        int lastOpenBracket = textBeforeCaret.lastIndexOf('<');
        if (lastOpenBracket == -1) return null;

        String tagContent = textBeforeCaret.substring(lastOpenBracket + 1);
        return extractElementName(tagContent);
    }

    /**
     * Gets the current attribute name when in attribute value context.
     */
    private static String getCurrentAttributeName(String textBeforeCaret) {
        // Find the last '=' before cursor
        int lastEquals = textBeforeCaret.lastIndexOf('=');
        if (lastEquals == -1) return null;

        // Find attribute name before '='
        String beforeEquals = textBeforeCaret.substring(0, lastEquals).trim();
        int lastSpace = beforeEquals.lastIndexOf(' ');
        if (lastSpace != -1) {
            return beforeEquals.substring(lastSpace + 1).trim();
        }

        return null;
    }

    /**
     * Finds the start position for element completion.
     */
    private static int findElementCompletionStart(String textBeforeCaret) {
        int lastOpenBracket = textBeforeCaret.lastIndexOf('<');
        return lastOpenBracket + 1; // After '<'
    }

    /**
     * Finds the start position for attribute completion.
     */
    private static int findAttributeCompletionStart(String textBeforeCaret) {
        // Find start of current word (after last space)
        int pos = textBeforeCaret.length() - 1;
        while (pos >= 0 && Character.isLetterOrDigit(textBeforeCaret.charAt(pos))) {
            pos--;
        }
        return pos + 1;
    }

    /**
     * Finds the start position for attribute value completion.
     */
    private static int findAttributeValueCompletionStart(String textBeforeCaret) {
        // Find the opening quote
        int lastDoubleQuote = textBeforeCaret.lastIndexOf('"');
        int lastSingleQuote = textBeforeCaret.lastIndexOf('\'');
        return Math.max(lastDoubleQuote, lastSingleQuote) + 1;
    }

    /**
     * Finds the start position for text content completion.
     */
    private static int findTextContentCompletionStart(String textBeforeCaret) {
        int lastCloseBracket = textBeforeCaret.lastIndexOf('>');
        if (lastCloseBracket == -1) return 0;

        // Find start of current word
        int pos = textBeforeCaret.length() - 1;
        while (pos > lastCloseBracket && Character.isLetterOrDigit(textBeforeCaret.charAt(pos))) {
            pos--;
        }
        return pos + 1;
    }
}
