package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes XPath/XQuery expressions to determine the context at the cursor position.
 * Used by the completion provider to offer appropriate suggestions.
 *
 * <p>This analyzer is stateless and thread-safe.</p>
 */
public class XPathExpressionAnalyzer {

    private static final Logger logger = LogManager.getLogger(XPathExpressionAnalyzer.class);

    // Token boundary characters for XPath
    private static final String TOKEN_BOUNDARIES = " /[]()@$=!<>|+*,:'\"";

    // XPath axes
    private static final Set<String> AXES = Set.of(
            "ancestor", "ancestor-or-self", "attribute", "child", "descendant",
            "descendant-or-self", "following", "following-sibling", "namespace",
            "parent", "preceding", "preceding-sibling", "self"
    );

    // XQuery FLWOR keywords
    private static final Set<String> XQUERY_KEYWORDS = Set.of(
            "for", "let", "where", "order", "by", "return",
            "if", "then", "else", "some", "every", "satisfies", "in"
    );

    // XPath operators (word-based)
    private static final Set<String> OPERATORS = Set.of(
            "and", "or", "not", "div", "mod", "to", "eq", "ne", "lt", "le", "gt", "ge",
            "is", "union", "intersect", "except", "instance", "of", "treat", "as", "cast", "castable"
    );

    // Pattern to detect function call (name followed by opening parenthesis)
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_\\-]*(?::[a-zA-Z_][a-zA-Z0-9_\\-]*)?)\\s*\\($"
    );

    // Pattern to detect axis
    private static final Pattern AXIS_PATTERN = Pattern.compile(
            "(ancestor|ancestor-or-self|attribute|child|descendant|descendant-or-self|" +
                    "following|following-sibling|namespace|parent|preceding|preceding-sibling|self)::$"
    );

    private XPathExpressionAnalyzer() {
        // Static utility class
    }

    /**
     * Analyzes the XPath/XQuery expression at the given caret position.
     *
     * @param expression    the full expression text
     * @param caretPosition the cursor position (0-based)
     * @param isXQuery      true if XQuery mode (supports FLWOR)
     * @return the analyzed context
     */
    public static XPathEditorContext analyze(String expression, int caretPosition, boolean isXQuery) {
        if (expression == null || expression.isEmpty()) {
            return XPathEditorContext.builder()
                    .caretPosition(0)
                    .fullExpression("")
                    .textBeforeCaret("")
                    .contextType(XPathContextType.PATH_START)
                    .isXQuery(isXQuery)
                    .build();
        }

        // Ensure caret position is valid
        caretPosition = Math.max(0, Math.min(caretPosition, expression.length()));

        String textBeforeCaret = expression.substring(0, caretPosition);

        // Calculate nesting depths
        int predicateDepth = countUnclosed(textBeforeCaret, '[', ']');
        int functionDepth = countUnclosedParens(textBeforeCaret);

        // Find current token
        TokenInfo tokenInfo = findCurrentToken(textBeforeCaret);

        // Determine context type
        XPathContextType contextType = determineContextType(textBeforeCaret, tokenInfo, isXQuery, predicateDepth, functionDepth);

        // Extract XPath path for context-aware completions
        PathInfo pathInfo = extractXPathPath(textBeforeCaret, tokenInfo.startPosition);

        XPathEditorContext context = XPathEditorContext.builder()
                .caretPosition(caretPosition)
                .fullExpression(expression)
                .textBeforeCaret(textBeforeCaret)
                .contextType(contextType)
                .currentToken(tokenInfo.token)
                .precedingToken(tokenInfo.precedingToken)
                .tokenStartPosition(tokenInfo.startPosition)
                .isXQuery(isXQuery)
                .predicateDepth(predicateDepth)
                .functionDepth(functionDepth)
                .xpathPath(pathInfo.pathElements)
                .isAbsolutePath(pathInfo.isAbsolute)
                .isDescendantPath(pathInfo.isDescendant)
                .build();

        logger.debug("Analyzed XPath context: {}", context);
        return context;
    }

    /**
     * Determines the context type based on the text before the caret.
     */
    private static XPathContextType determineContextType(String textBeforeCaret, TokenInfo tokenInfo,
                                                         boolean isXQuery, int predicateDepth, int functionDepth) {
        if (textBeforeCaret.isEmpty()) {
            return XPathContextType.PATH_START;
        }

        // Check if in string literal
        if (isInStringLiteral(textBeforeCaret)) {
            return XPathContextType.IN_STRING_LITERAL;
        }

        // Check if in XPath comment
        if (isInComment(textBeforeCaret)) {
            return XPathContextType.IN_COMMENT;
        }

        // Get the text without the current token
        String textWithoutCurrentToken = textBeforeCaret.substring(0, tokenInfo.startPosition).stripTrailing();

        // Check specific patterns
        if (textWithoutCurrentToken.endsWith("::")) {
            return XPathContextType.AFTER_AXIS;
        }

        if (textWithoutCurrentToken.endsWith("@")) {
            return XPathContextType.AFTER_AT;
        }

        if (textWithoutCurrentToken.endsWith("$")) {
            return XPathContextType.AFTER_DOLLAR;
        }

        if (textWithoutCurrentToken.endsWith("//")) {
            return XPathContextType.AFTER_DOUBLE_SLASH;
        }

        if (textWithoutCurrentToken.endsWith("/")) {
            return XPathContextType.AFTER_SLASH;
        }

        // Check for operator context
        if (endsWithOperator(textWithoutCurrentToken)) {
            return XPathContextType.AFTER_OPERATOR;
        }

        // XQuery-specific contexts
        if (isXQuery) {
            String lastWord = getLastWord(textWithoutCurrentToken);
            if ("for".equals(lastWord)) {
                return XPathContextType.AFTER_FOR;
            }
            if ("let".equals(lastWord)) {
                return XPathContextType.AFTER_LET;
            }
            if ("in".equals(lastWord) || textWithoutCurrentToken.endsWith(":=")) {
                return XPathContextType.AFTER_BINDING;
            }
            if ("where".equals(lastWord)) {
                return XPathContextType.AFTER_WHERE;
            }
            if ("return".equals(lastWord)) {
                return XPathContextType.AFTER_RETURN;
            }
        }

        // Check nesting
        if (functionDepth > 0 && predicateDepth == 0) {
            // Check if we're right after opening paren
            if (textWithoutCurrentToken.endsWith("(")) {
                return XPathContextType.IN_FUNCTION_ARGS;
            }
            // Check if after comma in function args
            if (textWithoutCurrentToken.endsWith(",")) {
                return XPathContextType.IN_FUNCTION_ARGS;
            }
        }

        if (predicateDepth > 0) {
            return XPathContextType.IN_PREDICATE;
        }

        if (functionDepth > 0) {
            return XPathContextType.IN_FUNCTION_ARGS;
        }

        // Default: treat as path start if at beginning or after operator
        if (textWithoutCurrentToken.isEmpty()) {
            return XPathContextType.PATH_START;
        }

        // Check last character for context
        char lastChar = textWithoutCurrentToken.charAt(textWithoutCurrentToken.length() - 1);
        if (lastChar == '[' || lastChar == '(') {
            return predicateDepth > 0 ? XPathContextType.IN_PREDICATE : XPathContextType.IN_FUNCTION_ARGS;
        }

        // XQuery body context
        if (isXQuery) {
            return XPathContextType.XQUERY_BODY;
        }

        return XPathContextType.PATH_START;
    }

    /**
     * Finds the current token being typed.
     */
    private static TokenInfo findCurrentToken(String textBeforeCaret) {
        if (textBeforeCaret.isEmpty()) {
            return new TokenInfo("", "", 0);
        }

        // Scan backwards to find token start
        int tokenStart = textBeforeCaret.length();
        for (int i = textBeforeCaret.length() - 1; i >= 0; i--) {
            char c = textBeforeCaret.charAt(i);
            if (TOKEN_BOUNDARIES.indexOf(c) >= 0) {
                tokenStart = i + 1;
                break;
            }
            if (i == 0) {
                tokenStart = 0;
            }
        }

        String currentToken = textBeforeCaret.substring(tokenStart);

        // Find preceding token
        String beforeCurrentToken = textBeforeCaret.substring(0, tokenStart).stripTrailing();
        String precedingToken = "";
        if (!beforeCurrentToken.isEmpty()) {
            int precedingEnd = beforeCurrentToken.length();
            int precedingStart = precedingEnd;
            for (int i = beforeCurrentToken.length() - 1; i >= 0; i--) {
                char c = beforeCurrentToken.charAt(i);
                if (TOKEN_BOUNDARIES.indexOf(c) >= 0) {
                    precedingStart = i + 1;
                    break;
                }
                if (i == 0) {
                    precedingStart = 0;
                }
            }
            precedingToken = beforeCurrentToken.substring(precedingStart);
        }

        return new TokenInfo(currentToken, precedingToken, tokenStart);
    }

    /**
     * Counts unclosed brackets.
     */
    private static int countUnclosed(String text, char open, char close) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Handle string literals
            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                stringChar = c;
            } else if (inString && c == stringChar) {
                // Check for escaped quote
                if (i + 1 < text.length() && text.charAt(i + 1) == stringChar) {
                    i++; // Skip escaped quote
                } else {
                    inString = false;
                }
            }

            if (!inString) {
                if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth = Math.max(0, depth - 1);
                }
            }
        }

        return depth;
    }

    /**
     * Counts unclosed parentheses, considering function calls.
     */
    private static int countUnclosedParens(String text) {
        return countUnclosed(text, '(', ')');
    }

    /**
     * Checks if currently inside a string literal.
     */
    private static boolean isInStringLiteral(String text) {
        int singleQuotes = 0;
        int doubleQuotes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                // Check for escaped quote
                if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    singleQuotes++;
                }
            } else if (c == '"') {
                // Check for escaped quote
                if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i++;
                } else {
                    doubleQuotes++;
                }
            }
        }

        return (singleQuotes % 2 != 0) || (doubleQuotes % 2 != 0);
    }

    /**
     * Checks if currently inside an XPath comment (: ... :).
     */
    private static boolean isInComment(String text) {
        int lastOpen = text.lastIndexOf("(:");
        if (lastOpen < 0) {
            return false;
        }
        int lastClose = text.lastIndexOf(":)");
        return lastClose < lastOpen;
    }

    /**
     * Checks if text ends with an operator.
     */
    private static boolean endsWithOperator(String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) {
            return false;
        }

        // Check symbol operators
        char last = trimmed.charAt(trimmed.length() - 1);
        if ("=<>|+*,".indexOf(last) >= 0) {
            return true;
        }

        // Check word operators
        String lastWord = getLastWord(trimmed);
        return OPERATORS.contains(lastWord);
    }

    /**
     * Gets the last word from the text.
     */
    private static String getLastWord(String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) {
            return "";
        }

        int start = trimmed.length();
        for (int i = trimmed.length() - 1; i >= 0; i--) {
            char c = trimmed.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                start = i + 1;
                break;
            }
            if (i == 0) {
                start = 0;
            }
        }

        return trimmed.substring(start);
    }

    /**
     * Checks if a token looks like an axis name.
     */
    public static boolean isAxisName(String token) {
        return AXES.contains(token.toLowerCase());
    }

    /**
     * Checks if a token looks like an XQuery keyword.
     */
    public static boolean isXQueryKeyword(String token) {
        return XQUERY_KEYWORDS.contains(token.toLowerCase());
    }

    /**
     * Checks if a token looks like an operator.
     */
    public static boolean isOperator(String token) {
        return OPERATORS.contains(token.toLowerCase());
    }

    /**
     * Extracts the XPath path elements from the expression.
     * Parses expressions like "/root/child/" to extract ["root", "child"].
     *
     * @param textBeforeCaret     the text before the cursor
     * @param tokenStartPosition  the start position of the current token
     * @return PathInfo containing path elements and path type flags
     */
    private static PathInfo extractXPathPath(String textBeforeCaret, int tokenStartPosition) {
        List<String> pathElements = new ArrayList<>();
        boolean isAbsolute = false;
        boolean isDescendant = false;

        if (textBeforeCaret == null || textBeforeCaret.isEmpty()) {
            return new PathInfo(pathElements, false, false);
        }

        // Get the text before the current token (the completed path)
        String pathText = textBeforeCaret.substring(0, tokenStartPosition).stripTrailing();

        if (pathText.isEmpty()) {
            return new PathInfo(pathElements, false, false);
        }

        // Check for absolute or descendant path
        if (pathText.startsWith("//")) {
            isDescendant = true;
            isAbsolute = true;
            pathText = pathText.substring(2);
        } else if (pathText.startsWith("/")) {
            isAbsolute = true;
            pathText = pathText.substring(1);
        }

        // Split by / to get path steps
        // But we need to be careful about predicates, function calls, etc.
        StringBuilder currentElement = new StringBuilder();
        int bracketDepth = 0;
        int parenDepth = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < pathText.length(); i++) {
            char c = pathText.charAt(i);

            // Handle string literals
            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                stringChar = c;
                currentElement.append(c);
            } else if (inString) {
                currentElement.append(c);
                if (c == stringChar) {
                    // Check for escaped quote
                    if (i + 1 < pathText.length() && pathText.charAt(i + 1) == stringChar) {
                        i++;
                        currentElement.append(stringChar);
                    } else {
                        inString = false;
                    }
                }
            } else if (c == '[') {
                bracketDepth++;
                currentElement.append(c);
            } else if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
                currentElement.append(c);
            } else if (c == '(') {
                parenDepth++;
                currentElement.append(c);
            } else if (c == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
                currentElement.append(c);
            } else if (c == '/' && bracketDepth == 0 && parenDepth == 0) {
                // This is a path separator
                String element = extractElementName(currentElement.toString().trim());
                if (!element.isEmpty()) {
                    pathElements.add(element);
                }
                currentElement = new StringBuilder();

                // Check for // (descendant)
                if (i + 1 < pathText.length() && pathText.charAt(i + 1) == '/') {
                    i++; // Skip second slash
                    // Note: subsequent elements after // can be any descendant
                }
            } else {
                currentElement.append(c);
            }
        }

        // Handle remaining element
        String remainingElement = extractElementName(currentElement.toString().trim());
        if (!remainingElement.isEmpty()) {
            pathElements.add(remainingElement);
        }

        return new PathInfo(pathElements, isAbsolute, isDescendant);
    }

    /**
     * Extracts the element name from a path step.
     * Handles cases like "element", "element[predicate]", "ns:element", "axis::element".
     */
    private static String extractElementName(String pathStep) {
        if (pathStep == null || pathStep.isEmpty()) {
            return "";
        }

        // Skip if it's an axis (contains ::)
        if (pathStep.contains("::")) {
            String afterAxis = pathStep.substring(pathStep.indexOf("::") + 2);
            return extractElementName(afterAxis);
        }

        // Remove predicates [...]
        int bracketPos = pathStep.indexOf('[');
        if (bracketPos > 0) {
            pathStep = pathStep.substring(0, bracketPos);
        }

        // Skip wildcards
        if (pathStep.equals("*")) {
            return "*";
        }

        // Skip node tests like node(), text(), etc.
        if (pathStep.endsWith("()")) {
            return "";
        }

        // Skip . and ..
        if (pathStep.equals(".") || pathStep.equals("..")) {
            return "";
        }

        // Return the element name (possibly with namespace prefix)
        return pathStep.trim();
    }

    /**
     * Token information holder.
     */
    private static class TokenInfo {
        final String token;
        final String precedingToken;
        final int startPosition;

        TokenInfo(String token, String precedingToken, int startPosition) {
            this.token = token;
            this.precedingToken = precedingToken;
            this.startPosition = startPosition;
        }
    }

    /**
     * Path information holder.
     */
    private static class PathInfo {
        final List<String> pathElements;
        final boolean isAbsolute;
        final boolean isDescendant;

        PathInfo(List<String> pathElements, boolean isAbsolute, boolean isDescendant) {
            this.pathElements = pathElements;
            this.isAbsolute = isAbsolute;
            this.isDescendant = isDescendant;
        }
    }
}
