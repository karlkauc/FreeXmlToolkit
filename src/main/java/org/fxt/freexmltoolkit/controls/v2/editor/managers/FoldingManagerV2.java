package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * <p>Improvements over the earlier folding manager:</p>
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
    /**
     * Largest region (in hidden lines) that "fold all" folds as a whole; larger ones are opened up into
     * their inner regions (folding costs about 0.5 ms per hidden line, see {@link #foldAll()}).
     */
    static final int MAX_FOLD_ALL_LINES = 1_000;

    private final Map<Integer, FoldingRegion> foldingRegions = new TreeMap<>();

    // Tracks which regions are currently folded
    private final Set<Integer> foldedRegions = new HashSet<>();

    // Pattern to match XML opening tags. Lines containing "/>" contribute no opening tags at all (see
    // calculateFoldingRegions), so the former "(?!.*/>)" lookahead - which rescanned the rest of the line
    // for every match - is checked once per line instead.
    private static final Pattern OPEN_TAG_PATTERN = Pattern.compile(
            "<([a-zA-Z][\\w:.-]*)(?:\\s+[^>]*)?>", Pattern.MULTILINE);

    /** Computes folding regions off the FX thread; one daemon thread shared by all editors. */
    private static final java.util.concurrent.ExecutorService FOLDING_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FoldingRegions");
                t.setDaemon(true);
                return t;
            });

    /** Bumped per async request (FX thread); a result for an older request is dropped. */
    private long asyncRequest;

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
            applyRegions(calculateFoldingRegions(text));
        } catch (Exception e) {
            logger.error("Error updating folding regions: {}", e.getMessage(), e);
        }
    }

    /**
     * Like {@link #updateFoldingRegions(String)}, but the regions are computed on a background thread
     * (a split plus two regular expressions per line: hundreds of ms for multi-MB documents) and applied
     * on the FX thread. Call on the FX thread; a newer request supersedes a pending one.
     *
     * @param text the XML text to analyze (a snapshot taken on the FX thread)
     */
    public void updateFoldingRegionsAsync(String text) {
        if (text == null || text.isEmpty()) {
            ++asyncRequest;
            foldingRegions.clear();
            return;
        }
        updateFoldingRegionsAsync(() -> text);
    }

    /**
     * Same as {@link #updateFoldingRegionsAsync(String)} with the text produced on the worker, e.g.
     * from an immutable document snapshot (copying a document of 150 MB took seconds on the FX thread).
     */
    public void updateFoldingRegionsAsync(java.util.function.Supplier<String> textSource) {
        long request = ++asyncRequest;
        FOLDING_EXECUTOR.execute(() -> {
            Map<Integer, FoldingRegion> regions;
            try {
                String text = textSource.get();
                regions = text == null || text.isEmpty() || hasExtremelyLongLine(text)
                        ? new TreeMap<>() : calculateFoldingRegions(text);
            } catch (RuntimeException e) {
                logger.debug("Could not compute folding regions: {}", e.toString());
                return;
            }
            Platform.runLater(() -> {
                if (request == asyncRequest && !isFoldingInProgress.get()) {
                    applyRegions(regions);
                }
            });
        });
    }

    /** Installs freshly computed regions, keeping the fold state of regions that still exist. FX thread. */
    private void applyRegions(Map<Integer, FoldingRegion> newRegions) {
        try {
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

            // Opening tags; a line with a self-closing tag contributes none (as before)
            if (line.indexOf("/>") < 0) {
                Matcher openMatcher = OPEN_TAG_PATTERN.matcher(line);
                while (openMatcher.find()) {
                    String tagName = openMatcher.group(1);
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
        // Outermost first (TreeMap order). Folding a region makes RichTextFX build a cell for every
        // hidden paragraph (a folded line has no height, so filling the viewport walks through all of
        // them), so regions with more than MAX_FOLD_ALL_LINES lines are not folded themselves - the
        // regions inside them are. A region inside a chosen one is skipped: it is hidden with its parent
        // and stays open, so unfolding the parent shows its content as it was.
        List<FoldingRegion> chosen = new ArrayList<>();
        int lastParagraph = codeArea.getParagraphs().size() - 1;
        int coveredUntil = -1;
        for (var entry : foldingRegions.entrySet()) {
            int line = entry.getKey();
            FoldingRegion region = entry.getValue();
            int endLine = Math.min(region.endLine(), lastParagraph);
            if (line <= coveredUntil || endLine <= line) {
                continue;
            }
            if (foldedRegions.contains(line)) {
                coveredUntil = Math.max(coveredUntil, endLine);
                continue;
            }
            if (endLine - line > MAX_FOLD_ALL_LINES) {
                continue; // too large to fold cheaply: its inner regions are folded instead
            }
            chosen.add(region);
            coveredUntil = endLine;
        }
        if (chosen.isEmpty()) {
            return;
        }
        // One RichTextFX fold per region cost one document replace + caret/cell updates each - quadratic:
        // 1,000 regions took 15 s, 2,000 took 71 s, a 6.8 MB document froze (telemetry). The first region is
        // folded the normal way (it also switches on RichTextFX's skipping of folded lines for the caret),
        // all others get their fold style in a single replace.
        moveCaretOutOf(chosen);
        fold(chosen.getFirst().startLine());
        if (chosen.size() > 1) {
            List<FoldingRegion> rest = chosen.subList(1, chosen.size());
            restyleParagraphs(rest, true);
            for (FoldingRegion region : rest) {
                foldedRegions.add(region.startLine());
            }
        }
        logger.debug("Folded {} regions", chosen.size());
    }

    /**
     * Unfolds all folded regions.
     */
    public void unfoldAll() {
        if (foldedRegions.size() <= 1) {
            for (Integer line : new ArrayList<>(foldedRegions)) {
                unfold(line);
            }
            return;
        }
        // All at once, for the same reason as foldAll
        List<FoldingRegion> folded = new ArrayList<>();
        for (Integer line : new java.util.TreeSet<>(foldedRegions)) {
            FoldingRegion region = foldingRegions.get(line);
            folded.add(region != null ? region : new FoldingRegion(line, line, "", 0));
        }
        restyleParagraphs(folded, false);
        foldedRegions.clear();
        logger.debug("Unfolded all regions");
    }

    /** Moves the caret to the end of a region's first line when it sits in a line that is about to be hidden. */
    private void moveCaretOutOf(List<FoldingRegion> regions) {
        int caretLine = codeArea.getCurrentParagraph();
        for (FoldingRegion region : regions) {
            if (caretLine > region.startLine() && caretLine <= region.endLine()) {
                codeArea.moveTo(region.startLine(), codeArea.getParagraphLength(region.startLine()));
                return;
            }
        }
    }

    /**
     * Adds (or removes) RichTextFX's fold style on the hidden lines of every region with one document
     * replace: the paragraphs from the first region's start to the last region's end are restyled in a
     * copy and put back at once. The text is unchanged (RichTextFX emits no plain-text change for it).
     */
    private void restyleParagraphs(List<FoldingRegion> regions, boolean fold) {
        int lastParagraph = codeArea.getParagraphs().size() - 1;
        int from = Integer.MAX_VALUE;
        int to = -1;
        java.util.BitSet hidden = new java.util.BitSet();
        for (FoldingRegion region : regions) {
            int endLine = Math.min(region.endLine(), lastParagraph);
            if (region.startLine() > lastParagraph) {
                continue;
            }
            from = Math.min(from, region.startLine());
            to = Math.max(to, endLine);
            if (endLine > region.startLine()) {
                hidden.set(region.startLine() + 1, endLine + 1);
            }
        }
        if (to < from) {
            return;
        }
        if (!fold) {
            // Every collapsed line in the span opens, also one of a region that changed since
            for (int i = from; i <= to; i++) {
                if (codeArea.getParagraph(i).getParagraphStyle().contains(FOLD_STYLE)) {
                    hidden.set(i);
                }
            }
        }
        if (hidden.isEmpty()) {
            return;
        }
        int startOffset = codeArea.getAbsolutePosition(from, 0);
        int endOffset = codeArea.getAbsolutePosition(to, codeArea.getParagraphLength(to));
        var document = (org.fxmisc.richtext.model.ReadOnlyStyledDocument<java.util.Collection<String>, String,
                java.util.Collection<String>>) codeArea.subDocument(startOffset, endOffset);
        // mapParagraphs visits the paragraphs in order and builds the tree once; replaceParagraph per line
        // split the document each time (48,000 regions: most of 21 s)
        int[] index = {from};
        final int first = from;
        document = document.mapParagraphs(p -> {
            int line = index[0]++;
            return line >= first && hidden.get(line)
                    ? p.setParagraphStyle(withFoldStyle(p.getParagraphStyle(), fold)) : p;
        });
        // A replace moves the caret to its end: keep the caret and selection where they were
        int anchor = codeArea.getAnchor();
        int caret = codeArea.getCaretPosition();
        codeArea.replace(startOffset, endOffset, document);
        codeArea.selectRange(anchor, caret);
    }

    /** RichTextFX's fold style class ({@code StyleClassedTextArea} adds / checks "collapse"). */
    private static final String FOLD_STYLE = "collapse";

    private static java.util.Collection<String> withFoldStyle(java.util.Collection<String> style, boolean fold) {
        List<String> result = new ArrayList<>(style);
        result.remove(FOLD_STYLE);
        if (fold) {
            result.add(FOLD_STYLE);
        }
        return result;
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
