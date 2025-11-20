package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighting manager for XmlCodeEditorV2.
 * Clean-room V2 implementation with configurable debouncing.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>XML syntax highlighting (tags, attributes, values, comments, CDATA)</li>
 *   <li>Configurable debouncing to reduce CPU usage</li>
 *   <li>Background processing via ExecutorService</li>
 *   <li>CSS-based styling for flexibility</li>
 * </ul>
 */
public class SyntaxHighlightManagerV2 {

    private static final Logger logger = LogManager.getLogger(SyntaxHighlightManagerV2.class);

    private final CodeArea codeArea;
    private final ExecutorService executor;
    private final long debounceMillis;

    // Optional: CombinedStyleManager for proper syntax+error highlighting
    private CombinedStyleManager styleManager;

    // V1-compatible XML syntax patterns
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

    // Debouncing
    private long lastHighlightRequest = 0;
    private String pendingText = null;

    /**
     * Creates a new SyntaxHighlightManagerV2 with default debouncing (300ms).
     *
     * @param codeArea the code area to manage
     */
    public SyntaxHighlightManagerV2(CodeArea codeArea) {
        this(codeArea, 300);
    }

    /**
     * Creates a new SyntaxHighlightManagerV2 with custom debouncing.
     *
     * @param codeArea the code area to manage
     * @param debounceMillis debounce time in milliseconds
     */
    public SyntaxHighlightManagerV2(CodeArea codeArea, long debounceMillis) {
        this.codeArea = codeArea;
        this.debounceMillis = debounceMillis;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SyntaxHighlightV2");
            t.setDaemon(true);
            return t;
        });

        logger.info("SyntaxHighlightManagerV2 created (debounce: {}ms)", debounceMillis);
    }

    /**
     * Applies syntax highlighting to the given text.
     * This method uses debouncing to avoid excessive CPU usage.
     *
     * @param text the text to highlight
     */
    public void applySyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        pendingText = text;

        // Debouncing: only process if enough time has passed
        if (now - lastHighlightRequest < debounceMillis) {
            // Schedule delayed processing
            executor.submit(() -> {
                try {
                    Thread.sleep(debounceMillis);
                    processHighlighting(pendingText);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            // Process immediately
            lastHighlightRequest = now;
            executor.submit(() -> processHighlighting(text));
        }
    }

    /**
     * Refreshes syntax highlighting (forces immediate update).
     *
     * @param text the text to highlight
     */
    public void refreshSyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        lastHighlightRequest = System.currentTimeMillis();
        executor.submit(() -> processHighlighting(text));
    }

    /**
     * Sets the combined style manager for proper syntax+error highlighting.
     *
     * @param styleManager the combined style manager
     */
    public void setStyleManager(CombinedStyleManager styleManager) {
        this.styleManager = styleManager;
    }

    /**
     * Processes syntax highlighting in background thread.
     *
     * @param text the text to highlight
     */
    private void processHighlighting(String text) {
        try {
            StyleSpans<Collection<String>> highlighting = computeHighlighting(text);

            // Apply highlighting on JavaFX thread
            Platform.runLater(() -> {
                try {
                    if (styleManager != null) {
                        // Use CombinedStyleManager to merge with error highlighting
                        styleManager.setSyntaxStyles(highlighting);
                    } else {
                        // Fallback: apply directly
                        codeArea.setStyleSpans(0, highlighting);
                    }
                } catch (Exception e) {
                    logger.debug("Error applying syntax highlighting: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.error("Error computing syntax highlighting", e);
        }
    }

    /**
     * Computes syntax highlighting spans for XML text using V1-compatible CSS classes.
     * Uses the same highlighting logic as the original XmlCodeEditor V1.
     *
     * @param text the XML text
     * @return style spans with V1-compatible CSS classes (tagmark, anytag, attribute, avalue, comment)
     */
    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);

            if (matcher.group("COMMENT") != null) {
                // XML comment
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else {
                if (matcher.group("ELEMENT") != null) {
                    String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);

                    // Opening bracket: < or </
                    spansBuilder.add(Collections.singleton("tagmark"),
                            matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));

                    // Element name
                    spansBuilder.add(Collections.singleton("anytag"),
                            matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                    // Attributes section
                    if (attributesText != null && !attributesText.isEmpty()) {
                        int attrLastEnd = 0;

                        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
                        while (amatcher.find()) {
                            // Whitespace before attribute
                            spansBuilder.add(Collections.emptyList(), amatcher.start() - attrLastEnd);

                            // Attribute name
                            spansBuilder.add(Collections.singleton("attribute"),
                                    amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));

                            // Equals sign
                            spansBuilder.add(Collections.singleton("tagmark"),
                                    amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));

                            // Attribute value
                            spansBuilder.add(Collections.singleton("avalue"),
                                    amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));

                            attrLastEnd = amatcher.end();
                        }

                        // Remaining whitespace after attributes
                        if (attributesText.length() > attrLastEnd) {
                            spansBuilder.add(Collections.emptyList(), attributesText.length() - attrLastEnd);
                        }
                    }

                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

                    // Closing bracket: > or />
                    spansBuilder.add(Collections.singleton("tagmark"),
                            matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
                }
            }
            lastKwEnd = matcher.end();
        }

        // Remaining text
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Shuts down the executor service.
     * Should be called when the editor is closed.
     */
    public void shutdown() {
        executor.shutdown();
        logger.debug("SyntaxHighlightManagerV2 shut down");
    }
}
