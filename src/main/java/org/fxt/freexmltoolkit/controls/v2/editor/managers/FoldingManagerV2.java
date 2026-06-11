package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;

/**
 * Code folding manager for XmlCodeEditorV2.
 *
 * <p>Manages XML element folding in the code editor, providing:</p>
 * <ul>
 *   <li>Automatic detection of foldable XML regions</li>
 *   <li>PURELY VISUAL fold/unfold via RichTextFX paragraph folding — the
 *       document text is never modified, so validation/XPath/save always see
 *       the complete XML</li>
 *   <li>Visual fold indicators (+/- buttons)</li>
 *   <li>Batch operations (fold all, unfold all)</li>
 * </ul>
 *
 * <p>Ported from the original XmlCodeFoldingManager with improvements:</p>
 * <ul>
 *   <li>Uses standalone FoldingRegion class</li>
 *   <li>AtomicBoolean for thread-safe fold operation tracking</li>
 *   <li>Cleaner separation of UI creation from logic</li>
 * </ul>
 */
public class FoldingManagerV2 {

    private static final Logger logger = LogManager.getLogger(FoldingManagerV2.class);

    private final CodeArea codeArea;

    // Maximum line length before folding is skipped (prevents O(n^2) regex behavior)
    private static final int MAX_SAFE_LINE_LENGTH_FOR_FOLDING = 100 * 1024; // 100KB

    // Maps line numbers to folding regions (TreeMap for sorted iteration)
    private final Map<Integer, FoldingRegion> foldingRegions = new TreeMap<>();

    // Tracks which regions are currently folded
    private final Set<Integer> foldedRegions = new HashSet<>();

    // Pattern to match XML opening tags (excluding self-closing)
    private static final Pattern OPEN_TAG_PATTERN = Pattern.compile(
            "<([a-zA-Z][\\w:.-]*)(?:\\s+[^>]*)?>(?!.*/>)", Pattern.MULTILINE);

    // Pattern to match XML closing tags
    private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile(
            "</([a-zA-Z][\\w:.-]*)>", Pattern.MULTILINE);

    // Minimum lines required for a foldable region
    private static final int MIN_FOLDABLE_LINES = 2;

    // Atomic flag to prevent recursive updates during folding operations
    private final AtomicBoolean isFoldingInProgress = new AtomicBoolean(false);

    /**
     * Creates a new FoldingManagerV2.
     *
     * @param codeArea the code area to manage folding for
     */
    public FoldingManagerV2(CodeArea codeArea) {
        this.codeArea = codeArea;
        logger.info("FoldingManagerV2 created");
    }

    /**
     * Updates folding regions based on the current text.
     * Called when text changes to re-detect foldable XML elements.
     *
     * @param text the XML text to analyze
     */
    public void updateFoldingRegions(String text) {
        if (text == null || text.isEmpty()) {
            foldingRegions.clear();
            return;
        }

        // Guard: skip folding if any line is extremely long (prevents O(n^2) regex)
        if (hasExtremelyLongLine(text)) {
            logger.info("Skipping folding - text contains line exceeding {}KB",
                    MAX_SAFE_LINE_LENGTH_FOR_FOLDING / 1024);
            foldingRegions.clear();
            return;
        }

        // Don't update during folding operations
        if (isFoldingInProgress.get()) {
            return;
        }

        try {
            Map<Integer, FoldingRegion> newRegions = calculateFoldingRegions(text);

            // Preserve fold state for existing regions
            Set<Integer> currentlyFolded = new HashSet<>(foldedRegions);

            // Avoid refreshing if nothing changed (prevents viewport jumps)
            if (foldingRegions.equals(newRegions)) {
                return;
            }

            foldingRegions.clear();
            foldingRegions.putAll(newRegions);

            // Restore fold state for regions that still exist
            foldedRegions.clear();
            for (Integer line : currentlyFolded) {
                if (foldingRegions.containsKey(line)) {
                    foldedRegions.add(line);
                }
            }

            logger.debug("Updated folding regions: {} foldable, {} folded",
                        foldingRegions.size(), foldedRegions.size());

            // Force CodeArea to recreate paragraph graphics with new folding regions
            Platform.runLater(() -> {
                try {
                    // Trigger paragraph graphic refresh by modifying and restoring a dummy property
                    var currentFactory = codeArea.getParagraphGraphicFactory();
                    codeArea.setParagraphGraphicFactory(null);
                    codeArea.setParagraphGraphicFactory(currentFactory);
                    logger.debug("Paragraph graphics refreshed after folding update");
                } catch (Exception ex) {
                    logger.warn("Could not refresh paragraph graphics: {}", ex.getMessage());
                }
            });

        } catch (Exception e) {
            logger.error("Error updating folding regions: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculates folding regions from XML text using stack-based tag matching.
     *
     * @param text the XML text to analyze
     * @return map of line number to FoldingRegion
     */
    private Map<Integer, FoldingRegion> calculateFoldingRegions(String text) {
        Map<Integer, FoldingRegion> regions = new TreeMap<>();
        String[] lines = text.split("\n");

        // Stack to track open elements (per tag name for nested same-name elements)
        Map<String, Stack<ElementPosition>> elementStacks = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            final int lineNumber = i;

            // Check for opening tags
            Matcher openMatcher = OPEN_TAG_PATTERN.matcher(line);
            while (openMatcher.find()) {
                String tagName = openMatcher.group(1);

                // Skip self-closing tags (handled by regex, but double-check)
                if (!line.contains("/>")) {
                    ElementPosition pos = new ElementPosition(tagName, lineNumber, openMatcher.start());
                    elementStacks.computeIfAbsent(tagName, k -> new Stack<>()).push(pos);
                }
            }

            // Check for closing tags
            Matcher closeMatcher = CLOSE_TAG_PATTERN.matcher(line);
            while (closeMatcher.find()) {
                String tagName = closeMatcher.group(1);

                Stack<ElementPosition> tagStack = elementStacks.get(tagName);
                if (tagStack != null && !tagStack.isEmpty()) {
                    ElementPosition openPos = tagStack.pop();

                    // Create folding region if it spans multiple lines
                    if (lineNumber - openPos.line >= MIN_FOLDABLE_LINES) {
                        FoldingRegion region = new FoldingRegion(
                                openPos.line,
                                lineNumber,
                                tagName,
                                calculateIndentLevel(lines[openPos.line])
                        );
                        regions.put(openPos.line, region);
                    }
                }
            }
        }

        // Handle unclosed elements (partial/incomplete documents)
        for (Stack<ElementPosition> stack : elementStacks.values()) {
            while (!stack.isEmpty()) {
                ElementPosition openPos = stack.pop();
                if (lines.length - 1 - openPos.line >= MIN_FOLDABLE_LINES) {
                    FoldingRegion region = new FoldingRegion(
                            openPos.line,
                            lines.length - 1,
                            openPos.tagName,
                            calculateIndentLevel(lines[openPos.line])
                    );
                    regions.put(openPos.line, region);
                }
            }
        }

        return regions;
    }

    /**
     * Creates a fold indicator button for a specific line.
     * Returns null if the line is not foldable and not currently folded.
     *
     * @param lineIndex 0-based line index
     * @return fold button Node, or null if not applicable
     */
    public Node createFoldIndicator(int lineIndex) {
        // Check if this line has a foldable region OR is currently folded
        boolean isFoldable = foldingRegions.containsKey(lineIndex);
        boolean isFolded = foldedRegions.contains(lineIndex);

        logger.debug("createFoldIndicator for line {}: isFoldable={}, isFolded={}, totalRegions={}",
                lineIndex, isFoldable, isFolded, foldingRegions.size());

        if (!isFoldable && !isFolded) {
            return null;
        }

        logger.debug("Creating fold button for line {}", lineIndex);
        Button button = new Button();
        button.setPrefSize(12, 12);
        button.setMinSize(12, 12);
        button.setMaxSize(12, 12);
        button.getStyleClass().add("fold-button");

        // Create triangle indicator
        Polygon triangle = new Polygon();

        if (isFolded) {
            // Right-pointing triangle: ▶ (folded state)
            triangle.getPoints().addAll(
                    2.0, 2.0,
                    2.0, 10.0,
                    10.0, 6.0
            );
        } else {
            // Down-pointing triangle: ▼ (unfolded state)
            triangle.getPoints().addAll(
                    2.0, 2.0,
                    10.0, 2.0,
                    6.0, 10.0
            );
        }

        triangle.setFill(Color.GRAY);
        button.setGraphic(triangle);

        // Handle click to toggle fold
        button.setOnAction(event -> toggleFold(lineIndex));

        // Add tooltip
        Tooltip tooltip = new Tooltip(isFolded ? "Unfold region" : "Fold region");
        Tooltip.install(button, tooltip);

        return button;
    }

    /**
     * Toggles the fold state of a region at the specified line.
     *
     * @param line 0-based line number
     */
    public void toggleFold(int line) {
        FoldingRegion region = foldingRegions.get(line);
        if (region == null) {
            return;
        }

        if (foldedRegions.contains(line)) {
            unfold(line);
        } else {
            fold(line);
        }
    }

    /**
     * Folds a region at the specified line — PURELY VISUALLY, via RichTextFX
     * paragraph folding ({@code foldParagraphs}): the paragraphs after the
     * region's start line get the "collapse" paragraph style (hidden by the
     * library's built-in {@code .styled-text-area .collapse} CSS). The document
     * text is NOT modified, so validation, XPath and save always see the
     * complete XML. (The previous implementation replaced the folded lines with
     * a {@code <name.../>} placeholder in the real text, which made the
     * document schema-invalid and broke XPath while folded.)
     *
     * @param line 0-based line number of the region start
     */
    public void fold(int line) {
        FoldingRegion region = foldingRegions.get(line);
        if (region == null || foldedRegions.contains(line)) {
            return;
        }

        if (!isFoldingInProgress.compareAndSet(false, true)) {
            return; // Already folding
        }

        try {
            int endLine = Math.min(region.endLine(), codeArea.getParagraphs().size() - 1);
            if (endLine <= line) {
                return;
            }
            // Move the caret out of the range that is about to be hidden.
            int caret = codeArea.getCaretPosition();
            int hideStart = codeArea.getAbsolutePosition(line, codeArea.getParagraphLength(line));
            int hideEnd = codeArea.getAbsolutePosition(endLine, codeArea.getParagraphLength(endLine));
            if (caret > hideStart && caret <= hideEnd) {
                codeArea.moveTo(hideStart);
            }

            // Hides paragraphs line+1..endLine "into" the (still visible) start line.
            codeArea.foldParagraphs(line, endLine);
            foldedRegions.add(line);

            logger.debug("Folded region at line {}: {}", line, region);
        } catch (Exception e) {
            logger.error("Error folding region at line {}: {}", line, e.getMessage(), e);
        } finally {
            isFoldingInProgress.set(false);
        }
    }

    /**
     * Unfolds a region at the specified line by removing the "collapse"
     * paragraph style from the hidden block. The document text is untouched.
     *
     * @param line 0-based line number of the region start
     */
    public void unfold(int line) {
        if (!foldedRegions.contains(line)) {
            return;
        }

        if (!isFoldingInProgress.compareAndSet(false, true)) {
            return; // Already folding/unfolding
        }

        try {
            // Unfolds the folded block following the (visible) start paragraph.
            codeArea.unfoldParagraphs(line);
            foldedRegions.remove(line);

            logger.debug("Unfolded region at line {}", line);
        } catch (Exception e) {
            logger.error("Error unfolding region at line {}: {}", line, e.getMessage(), e);
        } finally {
            isFoldingInProgress.set(false);
        }
    }

    /**
     * Folds all foldable regions.
     */
    public void foldAll() {
        // Outermost first (TreeMap order): folding a parent also hides nested
        // regions; folding the nested ones afterwards is a harmless style add.
        for (Integer line : new ArrayList<>(foldingRegions.keySet())) {
            if (!foldedRegions.contains(line)) {
                fold(line);
            }
        }
        logger.debug("Folded all regions");
    }

    /**
     * Unfolds all folded regions.
     */
    public void unfoldAll() {
        for (Integer line : new ArrayList<>(foldedRegions)) {
            unfold(line);
        }
        logger.debug("Unfolded all regions");
    }

    /**
     * Folds all regions at a specific indentation level.
     *
     * @param level the indentation level (0 for root)
     */
    public void foldLevel(int level) {
        for (Map.Entry<Integer, FoldingRegion> entry : foldingRegions.entrySet()) {
            if (entry.getValue().indentLevel() == level && !foldedRegions.contains(entry.getKey())) {
                fold(entry.getKey());
            }
        }

        logger.debug("Folded all regions at level {}", level);
    }

    /**
     * Checks if a line is foldable.
     *
     * @param lineIndex 0-based line index
     * @return true if the line has a foldable region
     */
    public boolean isFoldable(int lineIndex) {
        return foldingRegions.containsKey(lineIndex);
    }

    /**
     * Checks if a line is currently folded.
     *
     * @param lineIndex 0-based line index
     * @return true if the line is folded
     */
    public boolean isFolded(int lineIndex) {
        return foldedRegions.contains(lineIndex);
    }

    /**
     * Gets all foldable regions.
     *
     * @return unmodifiable map of line number to FoldingRegion
     */
    public Map<Integer, FoldingRegion> getFoldableRegions() {
        return Collections.unmodifiableMap(foldingRegions);
    }

    /**
     * Gets all currently folded lines.
     *
     * @return unmodifiable set of folded line numbers
     */
    public Set<Integer> getFoldedLines() {
        return Collections.unmodifiableSet(foldedRegions);
    }

    // Helper methods

    /**
     * Calculates the indentation level of a line.
     * Assumes 4 spaces or 1 tab per indent level.
     *
     * @param line the line text
     * @return indentation level (0 for no indent)
     */
    private int calculateIndentLevel(String line) {
        int spaces = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                spaces++;
            } else if (c == '\t') {
                spaces += 4;
            } else {
                break;
            }
        }
        return spaces / 4; // 4 spaces per indent level
    }

    /**
     * Checks if any line in the text exceeds the safe length threshold.
     * Uses a simple char-by-char loop for O(n) performance without Stream allocation.
     *
     * @param text the text to check
     * @return true if any line exceeds {@link #MAX_SAFE_LINE_LENGTH_FOR_FOLDING}
     */
    private static boolean hasExtremelyLongLine(String text) {
        int lineLength = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (lineLength > MAX_SAFE_LINE_LENGTH_FOR_FOLDING) {
                    return true;
                }
                lineLength = 0;
            } else {
                lineLength++;
            }
        }
        return lineLength > MAX_SAFE_LINE_LENGTH_FOR_FOLDING;
    }

    /**
     * Helper record to track XML element positions during parsing.
     */
    private record ElementPosition(String tagName, int line, int column) {
    }
}
