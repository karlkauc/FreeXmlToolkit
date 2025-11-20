package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code folding manager for XmlCodeEditorV2.
 *
 * <p>Manages XML element folding in the code editor, providing:</p>
 * <ul>
 *   <li>Automatic detection of foldable XML regions</li>
 *   <li>Fold/unfold operations with text preservation</li>
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

    // Maps line numbers to folding regions (TreeMap for sorted iteration)
    private final Map<Integer, FoldingRegion> foldingRegions = new TreeMap<>();

    // Tracks which regions are currently folded
    private final Set<Integer> foldedRegions = new HashSet<>();

    // Maps collapsed regions to their original text
    private final Map<Integer, String> collapsedText = new HashMap<>();

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
     * Folds a region at the specified line.
     * Replaces the folded lines with a placeholder like &lt;elementName.../&gt;
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
            int oldCaret = codeArea.getCaretPosition();
            int oldAnchor = codeArea.getAnchor();

            // Save current viewport position
            int currentParagraph = codeArea.getCurrentParagraph();
            double estimatedScrollY = codeArea.getEstimatedScrollY();

            String text = codeArea.getText();
            String[] lines = text.split("\n");

            // Calculate the text to hide
            int startLine = region.startLine();
            int endLine = Math.min(region.endLine(), lines.length - 1);

            // Create placeholder text
            String placeholder = String.format("<%s.../>", region.elementName());

            // Store the original hidden text
            StringBuilder hiddenText = new StringBuilder();
            for (int i = startLine + 1; i <= endLine; i++) {
                hiddenText.append(lines[i]);
                if (i < endLine) {
                    hiddenText.append("\n");
                }
            }
            collapsedText.put(line, hiddenText.toString());

            // Calculate positions
            int startPos = getLineStartPosition(startLine);
            int endPos = getLineEndPosition(endLine);

            // Replace the content with placeholder
            String beforeFold = text.substring(0, getLineEndPosition(startLine));
            String afterFold = endPos < text.length() ? text.substring(endPos) : "";

            String newText = beforeFold + " " + placeholder + afterFold;

            // Update the text
            codeArea.replaceText(newText);

            // Restore caret/selection as best as possible
            int len = codeArea.getLength();
            int restoreCaret = Math.max(0, Math.min(oldCaret, len));
            int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
            if (restoreCaret != restoreAnchor) {
                codeArea.selectRange(restoreAnchor, restoreCaret);
            } else {
                codeArea.moveTo(restoreCaret);
            }

            // Restore viewport position - prevent jumping to top
            javafx.application.Platform.runLater(() -> {
                try {
                    // Restore the paragraph that was visible before folding
                    int newParagraph = Math.min(currentParagraph, codeArea.getParagraphs().size() - 1);
                    codeArea.showParagraphAtTop(newParagraph);
                    codeArea.scrollYBy(estimatedScrollY - codeArea.getEstimatedScrollY());
                } catch (Exception ex) {
                    logger.debug("Could not restore viewport position: {}", ex.getMessage());
                }
            });

            // Mark as folded
            foldedRegions.add(line);

            logger.debug("Folded region at line {}: {}", line, region);

        } catch (Exception e) {
            logger.error("Error folding region at line {}: {}", line, e.getMessage(), e);
        } finally {
            isFoldingInProgress.set(false);
        }
    }

    /**
     * Unfolds a region at the specified line.
     * Restores the original text that was hidden.
     *
     * @param line 0-based line number of the region start
     */
    public void unfold(int line) {
        if (!foldedRegions.contains(line)) {
            return;
        }

        String originalText = collapsedText.get(line);
        if (originalText == null) {
            return;
        }

        if (!isFoldingInProgress.compareAndSet(false, true)) {
            return; // Already folding/unfolding
        }

        try {
            int oldCaret = codeArea.getCaretPosition();
            int oldAnchor = codeArea.getAnchor();

            // Save current viewport position
            int currentParagraph = codeArea.getCurrentParagraph();
            double estimatedScrollY = codeArea.getEstimatedScrollY();

            FoldingRegion region = foldingRegions.get(line);
            String text = codeArea.getText();

            // Find the placeholder
            String placeholder = String.format("<%s.../>", region.elementName());
            int placeholderIndex = text.indexOf(placeholder, getLineStartPosition(line));

            if (placeholderIndex != -1) {
                // Replace placeholder with original text
                String beforeUnfold = text.substring(0, placeholderIndex - 1); // Remove extra space
                String afterUnfold = text.substring(placeholderIndex + placeholder.length());

                String newText = beforeUnfold + "\n" + originalText + afterUnfold;

                // Update the text
                codeArea.replaceText(newText);

                // Restore caret/selection as best as possible
                int len = codeArea.getLength();
                int restoreCaret = Math.max(0, Math.min(oldCaret, len));
                int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
                if (restoreCaret != restoreAnchor) {
                    codeArea.selectRange(restoreAnchor, restoreCaret);
                } else {
                    codeArea.moveTo(restoreCaret);
                }

                // Restore viewport position - prevent jumping to top
                javafx.application.Platform.runLater(() -> {
                    try {
                        // Restore the paragraph that was visible before unfolding
                        int newParagraph = Math.min(currentParagraph, codeArea.getParagraphs().size() - 1);
                        codeArea.showParagraphAtTop(newParagraph);
                        codeArea.scrollYBy(estimatedScrollY - codeArea.getEstimatedScrollY());
                    } catch (Exception ex) {
                        logger.debug("Could not restore viewport position: {}", ex.getMessage());
                    }
                });

                // Remove from folded set
                foldedRegions.remove(line);
                collapsedText.remove(line);

                logger.debug("Unfolded region at line {}", line);
            }

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
        List<Integer> foldableLines = new ArrayList<>(foldingRegions.keySet());
        int oldCaret = codeArea.getCaretPosition();
        int oldAnchor = codeArea.getAnchor();

        for (Integer line : foldableLines) {
            if (!foldedRegions.contains(line)) {
                fold(line);
            }
        }

        // Restore caret
        int len = codeArea.getLength();
        int restoreCaret = Math.max(0, Math.min(oldCaret, len));
        int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
        if (restoreCaret != restoreAnchor) {
            codeArea.selectRange(restoreAnchor, restoreCaret);
        } else {
            codeArea.moveTo(restoreCaret);
        }

        logger.debug("Folded all regions");
    }

    /**
     * Unfolds all folded regions.
     */
    public void unfoldAll() {
        List<Integer> foldedLines = new ArrayList<>(foldedRegions);
        int oldCaret = codeArea.getCaretPosition();
        int oldAnchor = codeArea.getAnchor();

        for (Integer line : foldedLines) {
            unfold(line);
        }

        // Restore caret
        int len = codeArea.getLength();
        int restoreCaret = Math.max(0, Math.min(oldCaret, len));
        int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
        if (restoreCaret != restoreAnchor) {
            codeArea.selectRange(restoreAnchor, restoreCaret);
        } else {
            codeArea.moveTo(restoreCaret);
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
     * Gets the character position of the start of a line.
     *
     * @param line 0-based line index
     * @return character position in the text
     */
    private int getLineStartPosition(int line) {
        String text = codeArea.getText();
        String[] lines = text.split("\n");
        int pos = 0;
        for (int i = 0; i < line && i < lines.length; i++) {
            pos += lines[i].length() + 1; // +1 for newline
        }
        return pos;
    }

    /**
     * Gets the character position of the end of a line.
     *
     * @param line 0-based line index
     * @return character position in the text
     */
    private int getLineEndPosition(int line) {
        String text = codeArea.getText();
        String[] lines = text.split("\n");
        int pos = 0;
        for (int i = 0; i <= line && i < lines.length; i++) {
            pos += lines[i].length();
            if (i < line) {
                pos++; // +1 for newline
            }
        }
        return pos;
    }

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
     * Helper record to track XML element positions during parsing.
     */
    private record ElementPosition(String tagName, int line, int column) {
    }
}
