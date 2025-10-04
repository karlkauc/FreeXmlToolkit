package org.fxt.freexmltoolkit.controls.editor;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.service.ThreadPoolManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages syntax highlighting for XML content in the CodeArea.
 * This class handles pattern matching, CSS styling, and enumeration highlighting.
 */
public class SyntaxHighlightManager {

    private static final Logger logger = LogManager.getLogger(SyntaxHighlightManager.class);

    // Syntax Highlighting Patterns
    private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))"
            + "|(?<COMMENT><!--[^<>]+-->)");
    private static final Pattern ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");

    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;
    private static final int GROUP_ATTRIBUTE_NAME = 1;
    private static final int GROUP_EQUAL_SYMBOL = 2;
    private static final int GROUP_ATTRIBUTE_VALUE = 3;

    // Element patterns for enumeration detection
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)[^>]*>([^<]*)</\\1>");

    private final CodeArea codeArea;
    private final ThreadPoolManager threadPoolManager;

    // Background task management
    private Task<StyleSpans<Collection<String>>> syntaxHighlightingTask;

    // Cache for enumeration elements
    private final Map<String, Set<String>> enumerationElementsByContext = new HashMap<>();

    /**
     * Constructor for SyntaxHighlightManager.
     *
     * @param codeArea          The CodeArea to manage syntax highlighting for
     * @param threadPoolManager Thread pool manager for background operations
     */
    public SyntaxHighlightManager(CodeArea codeArea, ThreadPoolManager threadPoolManager) {
        this.codeArea = codeArea;
        this.threadPoolManager = threadPoolManager;
    }

    /**
     * Applies syntax highlighting using external CSS only.
     *
     * @param text The text to apply highlighting to
     */
    public void applySyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Cancel any running syntax highlighting task
        if (syntaxHighlightingTask != null && syntaxHighlightingTask.isRunning()) {
            syntaxHighlightingTask.cancel();
        }

        // Create new background task for syntax highlighting
        syntaxHighlightingTask = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                // Check if task was cancelled
                if (isCancelled()) {
                    return null;
                }

                // Compute syntax highlighting with enumeration in background
                return computeHighlightingWithEnumeration(text);
            }
        };

        syntaxHighlightingTask.setOnSucceeded(event -> {
            StyleSpans<Collection<String>> highlighting = syntaxHighlightingTask.getValue();
            if (highlighting != null) {
                codeArea.setStyleSpans(0, highlighting);
            }
        });

        syntaxHighlightingTask.setOnFailed(event -> {
            logger.error("Syntax highlighting failed", syntaxHighlightingTask.getException());
            // Fallback to basic highlighting
            StyleSpans<Collection<String>> basicHighlighting = computeHighlighting(text);
            codeArea.setStyleSpans(0, basicHighlighting);
        });

        // Run the task using managed thread pool with proper Task execution
        threadPoolManager.executeCPUIntensive("syntax-highlighting-" + System.currentTimeMillis(), () -> {
            // Execute the JavaFX Task in the background
            Thread taskThread = new Thread(syntaxHighlightingTask);
            taskThread.setName("SyntaxHighlighting-" + System.currentTimeMillis());
            taskThread.setDaemon(true);
            taskThread.start();
            return null;
        });
    }

    /**
     * Applies syntax highlighting combined with error highlighting.
     *
     * @param text          The text to highlight
     * @param currentErrors Map of line numbers to error messages
     */
    public void applySyntaxHighlightingWithErrors(String text, Map<Integer, String> currentErrors) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Cancel any running syntax highlighting task
        if (syntaxHighlightingTask != null && syntaxHighlightingTask.isRunning()) {
            syntaxHighlightingTask.cancel();
        }

        // Create new background task for combined highlighting
        syntaxHighlightingTask = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                if (isCancelled()) {
                    return null;
                }

                // Compute base syntax highlighting with enumeration
                StyleSpans<Collection<String>> baseHighlighting = computeHighlightingWithEnumeration(text);

                // Add error highlighting as additional styles
                return addErrorStylesToHighlighting(baseHighlighting, text, currentErrors);
            }
        };

        syntaxHighlightingTask.setOnSucceeded(event -> {
            StyleSpans<Collection<String>> highlighting = syntaxHighlightingTask.getValue();
            if (highlighting != null) {
                codeArea.setStyleSpans(0, highlighting);
                // Update paragraph graphics to show error markers
                // Notify listeners that highlighting was updated
                Platform.runLater(this::notifyHighlightingUpdated);
            }
        });

        syntaxHighlightingTask.setOnFailed(event -> {
            logger.error("Syntax highlighting with errors failed", syntaxHighlightingTask.getException());
            // Fallback to basic highlighting
            StyleSpans<Collection<String>> basicHighlighting = computeHighlighting(text);
            codeArea.setStyleSpans(0, basicHighlighting);
        });

        // Run the task using managed thread pool
        threadPoolManager.executeCPUIntensive("syntax-highlighting-errors-" + System.currentTimeMillis(), () -> {
            Thread taskThread = new Thread(syntaxHighlightingTask);
            taskThread.setName("SyntaxHighlightingWithErrors-" + System.currentTimeMillis());
            taskThread.setDaemon(true);
            taskThread.start();
            return null;
        });
    }

    /**
     * Computes syntax highlighting with enumeration element indicators.
     *
     * @param text The text to highlight
     * @return StyleSpans with highlighting information
     */
    public StyleSpans<Collection<String>> computeHighlightingWithEnumeration(String text) {
        if (text == null) {
            text = "";
        }

        // First, get the standard syntax highlighting
        StyleSpans<Collection<String>> baseHighlighting = computeHighlighting(text);

        // Create a builder for enumeration highlighting
        StyleSpansBuilder<Collection<String>> enumSpansBuilder = new StyleSpansBuilder<>();
        int lastMatchEnd = 0;

        // Find all enumeration elements with content
        List<ElementTextInfo> enumElements = findAllEnumerationElements(text);

        for (ElementTextInfo elementInfo : enumElements) {
            int gapLength = elementInfo.startPosition() - lastMatchEnd;
            int contentLength = elementInfo.endPosition() - elementInfo.startPosition();

            // Skip invalid spans with negative lengths
            if (gapLength < 0 || contentLength < 0) {
                logger.warn("Skipping invalid span: gap={}, content={}, start={}, end={}",
                        gapLength, contentLength, elementInfo.startPosition(), elementInfo.endPosition());
                continue;
            }

            enumSpansBuilder.add(Collections.emptyList(), gapLength);
            enumSpansBuilder.add(Collections.singleton("enumeration-content"), contentLength);
            lastMatchEnd = elementInfo.endPosition();
        }
        int finalGapLength = text.length() - lastMatchEnd;
        if (finalGapLength >= 0) {
            enumSpansBuilder.add(Collections.emptyList(), finalGapLength);
        }

        // Overlay the enumeration highlighting on top of the base syntax highlighting
        StyleSpans<Collection<String>> enumHighlighting = enumSpansBuilder.create();

        // Debug logging
        logger.debug("Base highlighting spans: {}", baseHighlighting.length());
        logger.debug("Enumeration highlighting spans: {}", enumHighlighting.length());

        return baseHighlighting.overlay(enumHighlighting, (baseStyle, enumStyle) -> {
            // If we have enumeration styling, use it; otherwise use base styling
            if (enumStyle != null && !enumStyle.isEmpty()) {
                logger.debug("Applying enumeration style: {}", enumStyle);
                return enumStyle;
            } else {
                return baseStyle;
            }
        });
    }

    /**
     * Static method for basic XML syntax highlighting without enumeration features.
     * Used by other components that don't need enumeration highlighting.
     *
     * @param text The text to highlight
     * @return StyleSpans with basic XML highlighting
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null) {
            text = "";
        }

        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else {
                if (matcher.group("ELEMENT") != null) {
                    String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                    spansBuilder.add(Collections.singleton("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                    if (attributesText != null && !attributesText.isEmpty()) {
                        lastKwEnd = 0;

                        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
                        while (amatcher.find()) {
                            spansBuilder.add(Collections.emptyList(), amatcher.start() - lastKwEnd);
                            spansBuilder.add(Collections.singleton("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
                            lastKwEnd = amatcher.end();
                        }
                        if (attributesText.length() > lastKwEnd)
                            spansBuilder.add(Collections.emptyList(), attributesText.length() - lastKwEnd);
                    }

                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
                }
            }
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Manually triggers syntax highlighting refresh.
     *
     * @param text The current text content
     */
    public void refreshSyntaxHighlighting(String text) {
        if (text != null && !text.isEmpty()) {
            logger.debug("Manually refreshing syntax highlighting for text length: {}", text.length());
            applySyntaxHighlighting(text);
            logger.debug("Syntax highlighting refresh completed");
        } else {
            logger.debug("No text to highlight");
        }
    }

    /**
     * Updates the enumeration elements cache for highlighting.
     *
     * @param enumerationElementsByContext Map of context to enumeration element names
     */
    public void updateEnumerationElementsCache(Map<String, Set<String>> enumerationElementsByContext) {
        this.enumerationElementsByContext.clear();
        this.enumerationElementsByContext.putAll(enumerationElementsByContext);
        logger.debug("Updated enumeration elements cache with {} contexts", enumerationElementsByContext.size());
    }

    /**
     * Gets the current enumeration elements cache.
     *
     * @return Map of context to enumeration element names
     */
    public Map<String, Set<String>> getEnumerationElementsByContext() {
        return new HashMap<>(enumerationElementsByContext);
    }

    private List<ElementTextInfo> findAllEnumerationElements(String text) {
        List<ElementTextInfo> elements = new ArrayList<>();

        if (enumerationElementsByContext.isEmpty()) {
            return elements;
        }

        // Use a more flexible pattern that matches any element with content
        // and then checks if the element name is in our enumeration cache for the current context
        Pattern tagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)[^>]*>([^<]*)</\\1>");
        Matcher matcher = tagPattern.matcher(text);

        while (matcher.find()) {
            String elementName = matcher.group(1);
            String content = matcher.group(2);

            if (content.isBlank()) {
                continue; // Skip empty elements
            }

            // Find the context for this element by looking at the XML structure
            String context = findElementContext(text, matcher.start());

            // Check if this element is in our enumeration cache for this context
            Set<String> contextElements = enumerationElementsByContext.get(context);
            if (contextElements != null && contextElements.contains(elementName)) {
                int contentStart = matcher.start(2);
                int contentEnd = matcher.end(2);
                elements.add(new ElementTextInfo(elementName, content, contentStart, contentEnd));
            }
        }

        return elements;
    }

    private String findElementContext(String text, int elementPosition) {
        try {
            // Look backwards from the element position to build the context
            String textBeforeElement = text.substring(0, elementPosition);

            // Use a stack to track element nesting
            Stack<String> elementStack = new Stack<>();

            // Simple character-based parsing for better performance
            int pos = textBeforeElement.length() - 1;
            while (pos >= 0) {
                char ch = textBeforeElement.charAt(pos);

                if (ch == '>') {
                    // Look for opening tag
                    int tagStart = textBeforeElement.lastIndexOf('<', pos);
                    if (tagStart >= 0) {
                        String tag = textBeforeElement.substring(tagStart + 1, pos).trim();
                        if (!tag.startsWith("/") && !tag.endsWith("/")) {
                            // Extract element name (first word)
                            int spacePos = tag.indexOf(' ');
                            String elementName = spacePos > 0 ? tag.substring(0, spacePos) : tag;
                            if (!elementName.isEmpty()) {
                                elementStack.push(elementName);
                            }
                        }
                    }
                } else if (ch == '<' && pos + 1 < textBeforeElement.length() && textBeforeElement.charAt(pos + 1) == '/') {
                    // Closing tag found, pop from stack
                    if (!elementStack.isEmpty()) {
                        elementStack.pop();
                    }
                }

                pos--;
            }

            // Build context path
            if (elementStack.isEmpty()) {
                return "/"; // Root context
            } else {
                // Use the immediate parent as context
                return "/" + elementStack.peek();
            }

        } catch (Exception e) {
            return "/"; // Default to root context
        }
    }

    private StyleSpans<Collection<String>> addErrorStylesToHighlighting(
            StyleSpans<Collection<String>> baseHighlighting, String text, Map<Integer, String> currentErrors) {

        if (currentErrors.isEmpty()) {
            return baseHighlighting;
        }

        // Use overlay to add error styles
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        // Split text into lines to identify error lines
        String[] lines = text.split("\\n", -1);
        int position = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;
            int lineLength = line.length();

            if (currentErrors.containsKey(lineNumber)) {
                // This line has errors - add error styling
                Collection<String> errorStyles = new ArrayList<>();
                errorStyles.add("diagnostic-error");
                spansBuilder.add(errorStyles, lineLength);
            } else {
                // No error - use empty styles (syntax highlighting will be preserved)
                spansBuilder.add(Collections.emptyList(), lineLength);
            }

            position += lineLength;

            // Add newline character styling if not the last line
            if (lineIndex < lines.length - 1) {
                spansBuilder.add(Collections.emptyList(), 1);
                position += 1;
            }
        }

        StyleSpans<Collection<String>> errorHighlighting = spansBuilder.create();

        // Use overlay method to combine syntax and error highlighting
        return baseHighlighting.overlay(errorHighlighting, (syntaxStyles, errorStyles) -> {
            if (errorStyles.isEmpty()) {
                return syntaxStyles;
            }
            // Combine syntax highlighting with error styles
            Collection<String> combined = new ArrayList<>(syntaxStyles);
            combined.addAll(errorStyles);
            return combined;
        });
    }

    /**
     * Cancels any running syntax highlighting task.
     */
    public void cancelHighlighting() {
        if (syntaxHighlightingTask != null && syntaxHighlightingTask.isRunning()) {
            syntaxHighlightingTask.cancel();
        }
    }

    /**
     * Notify listeners that highlighting was updated.
     * Override this method to provide custom notification behavior.
     */
    protected void notifyHighlightingUpdated() {
        // Default implementation does nothing
        // Subclasses or clients can override/implement this as needed
    }

    /**
     * Information about element text content at cursor position.
     */
    public record ElementTextInfo(String elementName, String textContent, int startPosition, int endPosition) {
    }
}