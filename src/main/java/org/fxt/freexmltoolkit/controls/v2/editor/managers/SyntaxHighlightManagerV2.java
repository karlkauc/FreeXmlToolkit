package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
     * Computes syntax highlighting spans for XML text.
     * Delegates to the shared {@link XmlSyntaxHighlighter} implementation.
     *
     * @param text the XML text
     * @return style spans with CSS classes (tagmark, anytag, attribute, avalue, comment)
     */
    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        return XmlSyntaxHighlighter.computeHighlighting(text);
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
