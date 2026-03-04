package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter;

/**
 * Syntax highlighting manager for XmlCodeEditorV2.
 * Clean-room V2 implementation with configurable debouncing.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>XML syntax highlighting (tags, attributes, values, comments, CDATA)</li>
 *   <li>Proper debouncing via ScheduledExecutorService (cancels pending tasks)</li>
 *   <li>Large file mode: longer debounce for files &gt; 500KB</li>
 *   <li>Very large file guard: disables highlighting for files &gt; 2MB</li>
 *   <li>CSS-based styling for flexibility</li>
 * </ul>
 */
public class SyntaxHighlightManagerV2 {

    private static final Logger logger = LogManager.getLogger(SyntaxHighlightManagerV2.class);

    private static final long LARGE_FILE_THRESHOLD = 500 * 1024;        // 500KB
    private static final long VERY_LARGE_FILE_THRESHOLD = 2 * 1024 * 1024; // 2MB
    private static final long LARGE_FILE_DEBOUNCE_MS = 800;

    private final CodeArea codeArea;
    private final ScheduledExecutorService scheduler;
    private final long debounceMillis;

    // Optional: CombinedStyleManager for proper syntax+error highlighting
    private CombinedStyleManager styleManager;

    // Debouncing: pending scheduled task (cancelled on new input)
    private Future<?> pendingHighlight;
    private boolean highlightingDisabled = false;

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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SyntaxHighlightV2");
            t.setDaemon(true);
            return t;
        });

        logger.info("SyntaxHighlightManagerV2 created (debounce: {}ms)", debounceMillis);
    }

    /**
     * Applies syntax highlighting to the given text.
     * Uses proper debouncing: cancels any pending highlight task before scheduling a new one.
     * For large files (&gt; 500KB), uses a longer debounce. For very large files (&gt; 2MB),
     * disables highlighting entirely.
     *
     * @param text the text to highlight
     */
    public void applySyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Very large file guard: disable highlighting
        if (text.length() > VERY_LARGE_FILE_THRESHOLD) {
            if (!highlightingDisabled) {
                highlightingDisabled = true;
                logger.info("Syntax highlighting disabled for very large content ({}KB)", text.length() / 1024);
            }
            return;
        }

        // Re-enable if file shrunk below threshold
        if (highlightingDisabled) {
            highlightingDisabled = false;
            logger.info("Syntax highlighting re-enabled (content size: {}KB)", text.length() / 1024);
        }

        // Cancel any pending highlight task
        if (pendingHighlight != null && !pendingHighlight.isDone()) {
            pendingHighlight.cancel(false);
        }

        // Use longer debounce for large files
        long effectiveDebounce = text.length() > LARGE_FILE_THRESHOLD ? LARGE_FILE_DEBOUNCE_MS : debounceMillis;

        // Schedule new highlight with debounce delay
        final String textToHighlight = text;
        pendingHighlight = scheduler.schedule(
                () -> processHighlighting(textToHighlight),
                effectiveDebounce,
                TimeUnit.MILLISECONDS
        );
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

        // Cancel pending and process immediately
        if (pendingHighlight != null && !pendingHighlight.isDone()) {
            pendingHighlight.cancel(false);
        }

        scheduler.execute(() -> processHighlighting(text));
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
        scheduler.shutdown();
        logger.debug("SyntaxHighlightManagerV2 shut down");
    }
}
