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
        installHiddenLineSupport();
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
            hiddenLinesChanged();

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
        FoldingRegion region = foldingRegions.get(lineIndex);
        boolean tooLarge = !isFolded && region != null && isTooLargeToFold(region);
        Tooltip tooltip = new Tooltip(isFolded ? "Unfold region"
                : tooLarge ? "Too large to fold here (" + (region.endLine() - region.startLine())
                        + " lines) - use the Tree or Graphic view"
                : "Fold region");
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
     * Folds a region at the specified line — PURELY VISUALLY: the paragraphs after the region's start line
     * get RichTextFX's "collapse" paragraph style (hidden by the library's {@code .styled-text-area .collapse}
     * CSS, a collapsed paragraph has no height). The document text is NOT modified, so validation, XPath and
     * save always see the complete XML.
     * <p>
     * RichTextFX's own {@code foldParagraphs} is not used: it builds the view's cell of every hidden line
     * and, once used, walks all hidden lines whenever the caret enters a folded block - folding
     * {@code <Funds>} (24,000 lines) froze the application. The styles are set in one document replace, the
     * patched Flowless view skips hidden lines ({@link #hiddenItems}), and the caret skips folded blocks here.
     *
     * @param line 0-based line number of the region start
     */
    public void fold(int line) {
        FoldingRegion region = foldingRegions.get(line);
        if (region == null || foldedRegions.contains(line)) {
            return;
        }
        int endLine = Math.min(region.endLine(), codeArea.getParagraphs().size() - 1);
        if (endLine <= line) {
            return;
        }
        if (isTooLargeToFold(region)) {
            logger.info("Not folding line {}: {} lines are too many without the view's hidden-line support",
                    line, endLine - line);
            return;
        }
        foldRegions(List.of(region));
    }

    /**
     * Unfolds a region at the specified line by removing the "collapse" paragraph style from its hidden
     * lines (regions folded inside it open too). The document text is untouched.
     *
     * @param line 0-based line number of the region start
     */
    public void unfold(int line) {
        if (!foldedRegions.contains(line)) {
            return;
        }
        FoldingRegion region = foldingRegions.get(line);
        unfoldRegions(List.of(region != null ? region : new FoldingRegion(line, line, "", 0)));
    }

    /**
     * Folds all foldable regions: the outermost ones (TreeMap order), each as a whole. A region inside a
     * chosen one is skipped: it is hidden with its parent and stays open, so unfolding the parent shows its
     * content as it was. All of them are folded in one document replace.
     */
    public void foldAll() {
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
            if (isTooLargeToFold(region)) {
                continue; // without hidden-line support: its inner regions are folded instead
            }
            chosen.add(region);
            coveredUntil = endLine;
        }
        foldRegions(chosen);
        logger.debug("Folded {} regions", chosen.size());
    }

    /**
     * Unfolds all folded regions (in one document replace).
     */
    public void unfoldAll() {
        List<FoldingRegion> folded = new ArrayList<>();
        for (Integer line : new java.util.TreeSet<>(foldedRegions)) {
            FoldingRegion region = foldingRegions.get(line);
            folded.add(region != null ? region : new FoldingRegion(line, line, "", 0));
        }
        unfoldRegions(folded);
        logger.debug("Unfolded all regions");
    }

    /**
     * Folds all regions at a specific indentation level (in one document replace).
     *
     * @param level the indentation level (0 for root)
     */
    public void foldLevel(int level) {
        List<FoldingRegion> chosen = new ArrayList<>();
        for (Map.Entry<Integer, FoldingRegion> entry : foldingRegions.entrySet()) {
            FoldingRegion region = entry.getValue();
            if (region.indentLevel() == level && !foldedRegions.contains(entry.getKey())
                    && !isTooLargeToFold(region)) {
                chosen.add(region);
            }
        }
        foldRegions(chosen);
        logger.debug("Folded all regions at level {}", level);
    }

    // ==================== Folding in one replace ====================

    /**
     * Largest region (in hidden lines) that is folded while the view cannot skip hidden lines (no
     * {@link #hasHiddenLineSupport() hidden-line support}): such a view builds a cell per hidden line,
     * about 0.5 ms each, plus line numbers and fold arrows.
     */
    static final int MAX_UNSUPPORTED_FOLD_LINES = 2_000;

    /** @return whether folding the region would freeze a view without hidden-line support */
    boolean isTooLargeToFold(FoldingRegion region) {
        return !hasHiddenLineSupport() && region.endLine() - region.startLine() > MAX_UNSUPPORTED_FOLD_LINES;
    }

    private void foldRegions(List<FoldingRegion> regions) {
        if (regions.isEmpty() || !isFoldingInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            moveCaretOutOf(regions);
            // The state first: the replace rebuilds the lines' cells, and their fold arrows read it
            for (FoldingRegion region : regions) {
                foldedRegions.add(region.startLine());
            }
            restyleParagraphs(regions, true);
            hiddenLinesChanged();
            refreshFoldArrows();
        } catch (RuntimeException e) {
            logger.error("Error folding {} region(s): {}", regions.size(), e.getMessage(), e);
        } finally {
            isFoldingInProgress.set(false);
        }
    }

    private void unfoldRegions(List<FoldingRegion> regions) {
        if (regions.isEmpty() || !isFoldingInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            // The state first: the replace rebuilds the lines' cells, and their fold arrows read it
            for (FoldingRegion region : regions) {
                // Regions folded inside an unfolded one are open now as well
                foldedRegions.removeIf(l -> l >= region.startLine() && l <= region.endLine());
            }
            restyleParagraphs(regions, false);
            hiddenLinesChanged();
            refreshFoldArrows();
        } catch (RuntimeException e) {
            logger.error("Error unfolding {} region(s): {}", regions.size(), e.getMessage(), e);
        } finally {
            isFoldingInProgress.set(false);
        }
    }

    /**
     * Redraws the fold arrows of the visible lines (the paragraph graphics): a cell built during the
     * replace, or one RichTextFX kept, would otherwise show the arrow of the state before.
     */
    private void refreshFoldArrows() {
        var factory = codeArea.getParagraphGraphicFactory();
        if (factory != null) {
            codeArea.setParagraphGraphicFactory(null);
            codeArea.setParagraphGraphicFactory(factory);
        }
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
     * copy and put back at once. The text is unchanged (RichTextFX emits no plain-text change for it,
     * {@code TextEdits} ignores it, the plain-text undo manager records nothing).
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

    // ==================== Hidden lines (view + caret) ====================

    /** The area's view, patched to skip hidden lines; {@code null} when the area has none. */
    private org.fxmisc.flowless.VirtualFlow<?, ?> virtualFlow;

    /**
     * The collapsed lines, exactly: a bit per paragraph that carries the fold style. Kept in step with every
     * paragraph change of the area (shifted, the changed paragraphs re-read), so the view and the caret ask
     * it in O(1) and jump over a whole hidden block with {@code nextClearBit}.
     */
    private final java.util.BitSet hiddenLines = new java.util.BitSet();

    /** Guards the caret listener against its own moves. */
    private boolean skippingCaret;

    /** @return whether the view skips hidden lines (the patched Flowless view was found) */
    public boolean hasHiddenLineSupport() {
        return virtualFlow != null;
    }

    /**
     * Tells the area's view which lines are hidden (the patched Flowless {@code VirtualFlow}, a child of the
     * code area) and installs the caret skipping.
     */
    private void installHiddenLineSupport() {
        for (Node child : codeArea.getChildrenUnmodifiable()) {
            if (child instanceof org.fxmisc.flowless.VirtualFlow<?, ?> flow) {
                virtualFlow = flow;
                break;
            }
        }
        if (virtualFlow == null) {
            logger.warn("No VirtualFlow in the code area: large regions are not folded");
            return;
        }
        org.reactfx.collection.LiveList.observeQuasiChanges(codeArea.getParagraphs(), change -> {
            for (var modification : change) {
                paragraphsChanged(modification.getFrom(), modification.getRemovedSize(),
                        modification.getAddedSize());
            }
        });
        virtualFlow.setHiddenItems(new org.fxmisc.flowless.HiddenItems() {
            @Override
            public boolean isHidden(int itemIndex) {
                return itemIndex >= 0 && hiddenLines.get(itemIndex);
            }

            @Override
            public int hiddenRunEnd(int itemIndex) {
                return hiddenLines.nextClearBit(itemIndex) - 1;
            }

            @Override
            public int hiddenRunStart(int itemIndex) {
                return hiddenLines.previousClearBit(itemIndex) + 1;
            }

            @Override
            public int hiddenBefore(int itemIndex) {
                return itemIndex <= 0 ? 0 : hiddenLines.get(0, itemIndex).cardinality();
            }

            @Override
            public int hiddenCount() {
                return hiddenLines.cardinality();
            }

            @Override
            public int itemIndexOfShown(int shownIndex) {
                return FoldingManagerV2.this.itemIndexOfShown(shownIndex);
            }
        });
        codeArea.currentParagraphProperty().addListener((obs, oldLine, newLine) ->
                skipFoldedBlock(oldLine, newLine));
    }

    /**
     * Keeps {@link #hiddenLines} in step with a change of the area's paragraphs: the bits after the
     * change move by the change in size, the changed paragraphs' styles are read again.
     */
    private void paragraphsChanged(int from, int removed, int added) {
        int delta = added - removed;
        if (delta != 0) {
            int tailStart = from + removed;
            if (hiddenLines.nextSetBit(tailStart) >= 0) {
                java.util.BitSet tail = hiddenLines.get(tailStart, Math.max(tailStart, hiddenLines.length()));
                hiddenLines.clear(from, Math.max(from, hiddenLines.length()));
                for (int bit = tail.nextSetBit(0); bit >= 0; bit = tail.nextSetBit(bit + 1)) {
                    hiddenLines.set(tailStart + delta + bit);
                }
            } else {
                hiddenLines.clear(from, Math.max(from, hiddenLines.length()));
            }
        }
        var paragraphs = codeArea.getParagraphs();
        int end = Math.min(from + added, paragraphs.size());
        for (int i = from; i < end; i++) {
            hiddenLines.set(i, paragraphs.get(i).getParagraphStyle().contains(FOLD_STYLE));
        }
    }

    /** Tells the view that the hidden lines changed (the bits follow the paragraph changes themselves). */
    private void hiddenLinesChanged() {
        if (virtualFlow != null) {
            virtualFlow.hiddenItemsChanged();
        }
    }

    /** @return the line that is the {@code shownIndex}-th shown line (for jumps to a scroll position) */
    int itemIndexOfShown(int shownIndex) {
        int line = -1;
        for (int shown = 0; shown <= shownIndex; shown++) {
            line = hiddenLines.nextClearBit(line + 1);
        }
        return Math.max(0, line);
    }

    /** Test hook: behaves like a view without hidden-line support (the guard for large regions applies). */
    void disableHiddenLineSupportForTest() {
        if (virtualFlow != null) {
            virtualFlow.setHiddenItems(org.fxmisc.flowless.HiddenItems.NONE);
            virtualFlow = null;
        }
    }

    /** @return the collapsed lines (tests) */
    java.util.BitSet hiddenLinesForTest() {
        return (java.util.BitSet) hiddenLines.clone();
    }

    /**
     * Keeps the caret out of folded blocks: moving down onto a hidden line puts it on the line after the
     * block, moving up on the block's start line (the visible line that holds the fold). A selection is
     * extended the same way.
     */
    private void skipFoldedBlock(Number oldLine, Number newLine) {
        if (skippingCaret || newLine == null || !hiddenLines.get(newLine.intValue())) {
            return;
        }
        int line = newLine.intValue();
        boolean down = oldLine == null || line > oldLine.intValue();
        int lastParagraph = codeArea.getParagraphs().size() - 1;
        int after = hiddenLines.nextClearBit(line);
        int before = hiddenLines.previousClearBit(line);
        int target = down && after <= lastParagraph ? after : before;
        if (target < 0) {
            target = after <= lastParagraph ? after : -1;
        }
        if (target < 0) {
            return;
        }
        int column = Math.min(codeArea.getCaretColumn(), codeArea.getParagraphLength(target));
        skippingCaret = true;
        try {
            if (codeArea.getSelection().getLength() > 0) {
                codeArea.moveTo(target, column, org.fxmisc.richtext.NavigationActions.SelectionPolicy.EXTEND);
            } else {
                codeArea.moveTo(target, column);
            }
        } finally {
            skippingCaret = false;
        }
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
