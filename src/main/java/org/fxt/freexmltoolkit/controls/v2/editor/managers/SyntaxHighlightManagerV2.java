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
    private static final long VERY_LARGE_FILE_THRESHOLD = 2 * 1024 * 1024; // 2MB → viewport mode
    private static final long LARGE_FILE_DEBOUNCE_MS = 800;
    private static final int MAX_HIGHLIGHTABLE_LINE_LENGTH = 200 * 1024; // 200KB
    /** Extra paragraphs highlighted above/below the visible range (covers tags spanning the edge). */
    private static final int VIEWPORT_BUFFER_PARAGRAPHS = 80;

    private final CodeArea codeArea;
    private final ScheduledExecutorService scheduler;
    private final long debounceMillis;

    // Optional: CombinedStyleManager for proper syntax+error highlighting
    private CombinedStyleManager styleManager;

    // Debouncing: pending scheduled task (cancelled on new input)
    private Future<?> pendingHighlight;
    private boolean highlightingDisabled = false;
    // Viewport mode: for very large files only the visible region is highlighted (kept fast).
    private volatile boolean viewportMode = false;

    /**
     * Returns whether syntax highlighting is currently disabled.
     * Package-private for testing.
     */
    boolean isHighlightingDisabled() {
        return highlightingDisabled;
    }

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

        // In viewport mode, re-highlight the newly visible region when the user scrolls.
        codeArea.estimatedScrollYProperty().addListener((obs, oldV, newV) -> {
            if (viewportMode) {
                scheduleViewportHighlight();
            }
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

        // Long line guard: disable highlighting for extremely long lines. Viewport mode highlights
        // whole paragraphs, so it cannot tame a single multi-hundred-KB line — keep disabling those.
        if (hasExtremelyLongLine(text)) {
            if (!highlightingDisabled) {
                highlightingDisabled = true;
                logger.info("Syntax highlighting disabled - line exceeds {}KB",
                        MAX_HIGHLIGHTABLE_LINE_LENGTH / 1024);
            }
            viewportMode = false;
            return;
        }

        // Re-enable if previously disabled
        if (highlightingDisabled) {
            highlightingDisabled = false;
            logger.info("Syntax highlighting re-enabled (content size: {}KB)", text.length() / 1024);
        }

        // Cancel any pending highlight task
        if (pendingHighlight != null && !pendingHighlight.isDone()) {
            pendingHighlight.cancel(false);
        }

        long effectiveDebounce = text.length() > LARGE_FILE_THRESHOLD ? LARGE_FILE_DEBOUNCE_MS : debounceMillis;

        // Very large files: highlight only the visible region (viewport mode), so highlighting stays
        // responsive regardless of file size instead of being disabled entirely.
        if (text.length() > VERY_LARGE_FILE_THRESHOLD) {
            if (!viewportMode) {
                viewportMode = true;
                logger.info("Syntax highlighting in viewport mode for large content ({}KB)", text.length() / 1024);
            }
            scheduleViewportHighlight();
            return;
        }

        // Normal files: highlight the whole document.
        viewportMode = false;
        final String textToHighlight = text;
        pendingHighlight = scheduler.schedule(
                () -> processHighlighting(textToHighlight),
                effectiveDebounce,
                TimeUnit.MILLISECONDS
        );
    }

    /** Schedules a debounced re-highlight of the visible region (viewport mode). */
    private void scheduleViewportHighlight() {
        if (pendingHighlight != null && !pendingHighlight.isDone()) {
            pendingHighlight.cancel(false);
        }
        pendingHighlight = scheduler.schedule(
                () -> Platform.runLater(this::highlightVisibleRegion),
                LARGE_FILE_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Highlights only the currently visible paragraph range (plus a buffer) and applies the styles at
     * the corresponding absolute offset. Reads the live {@link CodeArea} content, so it stays correct
     * after edits/round-trips. Must run on the FX thread. Falls back to the top of the document when
     * the viewport is not laid out yet.
     */
    private void highlightVisibleRegion() {
        if (!viewportMode) {
            return;
        }
        try {
            int totalLen = codeArea.getLength();
            int paraCount = codeArea.getParagraphs().size();
            if (totalLen == 0 || paraCount == 0) {
                return;
            }
            int firstPar = 0;
            int lastPar = Math.min(paraCount - 1, VIEWPORT_BUFFER_PARAGRAPHS * 2);
            try {
                int fv = codeArea.firstVisibleParToAllParIndex();
                int lv = codeArea.lastVisibleParToAllParIndex();
                firstPar = Math.max(0, fv - VIEWPORT_BUFFER_PARAGRAPHS);
                lastPar = Math.min(paraCount - 1, lv + VIEWPORT_BUFFER_PARAGRAPHS);
            } catch (Exception notLaidOut) {
                // viewport not available yet — highlight the top chunk as a fallback
            }
            int start = codeArea.getAbsolutePosition(firstPar, 0);
            int end = codeArea.getAbsolutePosition(lastPar, codeArea.getParagraphLength(lastPar));
            if (end <= start || start < 0 || end > totalLen) {
                return;
            }
            String sub = codeArea.getText(start, end);
            StyleSpans<Collection<String>> spans = computeHighlighting(sub);
            if (start + spans.length() <= totalLen) {
                codeArea.setStyleSpans(start, spans);
            }
        } catch (Exception e) {
            logger.debug("Viewport highlighting failed: {}", e.getMessage());
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

    /**
     * Checks if any line in the text exceeds the highlightable line length threshold.
     * Uses a simple char-by-char loop for O(n) performance without Stream allocation.
     *
     * @param text the text to check
     * @return true if any line exceeds {@link #MAX_HIGHLIGHTABLE_LINE_LENGTH}
     */
    private static boolean hasExtremelyLongLine(String text) {
        int lineLength = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (lineLength > MAX_HIGHLIGHTABLE_LINE_LENGTH) {
                    return true;
                }
                lineLength = 0;
            } else {
                lineLength++;
            }
        }
        return lineLength > MAX_HIGHLIGHTABLE_LINE_LENGTH;
    }
}
