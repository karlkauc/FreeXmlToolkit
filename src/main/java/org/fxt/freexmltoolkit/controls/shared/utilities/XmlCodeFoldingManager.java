package org.fxt.freexmltoolkit.controls.shared.utilities;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages code folding for XML elements in the code editor.
 * Provides collapsible regions for XML elements with visual indicators.
 */
public class XmlCodeFoldingManager {

    private static final Logger logger = LogManager.getLogger(XmlCodeFoldingManager.class);

    private final CodeArea codeArea;

    // Maps line numbers to folding regions
    private final Map<Integer, FoldingRegion> foldingRegions = new TreeMap<>();

    // Tracks which regions are currently folded
    private final Set<Integer> foldedRegions = new HashSet<>();

    // Maps collapsed regions to their placeholder text
    private final Map<Integer, String> collapsedText = new HashMap<>();

    // Pattern to match XML elements
    private static final Pattern OPEN_TAG_PATTERN = Pattern.compile(
            "<([a-zA-Z][\\w:.-]*)(?:\\s+[^>]*)?>(?!.*/>)", Pattern.MULTILINE);
    private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile(
            "</([a-zA-Z][\\w:.-]*)>", Pattern.MULTILINE);

    // Minimum lines required for a foldable region
    private static final int MIN_FOLDABLE_LINES = 2;

    // Flag to prevent recursive updates during folding operations
    private boolean isUpdatingFromFoldOperation = false;

    public XmlCodeFoldingManager(CodeArea codeArea) {
        this.codeArea = codeArea;
        initialize();
    }

    private void initialize() {
        // Update folding regions when text changes
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!isUpdatingFromFoldOperation) {
                updateFoldingRegions(newText);
            }
        });

        // Set up custom paragraph graphic factory with fold indicators
        setupFoldingIndicators();
    }

    /**
     * Updates folding regions based on the current text
     */
    public void updateFoldingRegions(String text) {
        if (text == null || text.isEmpty()) {
            foldingRegions.clear();
            return;
        }

        try {
            Map<Integer, FoldingRegion> newRegions = calculateFoldingRegions(text);

            // Preserve fold state for existing regions
            Set<Integer> currentlyFolded = new HashSet<>(foldedRegions);

            // If nothing changed, avoid refreshing paragraph graphics to prevent viewport jumps
            if (foldingRegions.equals(newRegions)) {
                return;
            }

            foldingRegions.clear();
            foldingRegions.putAll(newRegions);

            // Restore fold state for regions that still exist
            for (Integer line : currentlyFolded) {
                if (foldingRegions.containsKey(line)) {
                    foldedRegions.add(line);
                }
            }

            // Update the display
            refreshFoldingDisplay();

        } catch (Exception e) {
            logger.error("Error updating folding regions: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculates folding regions from XML text
     */
    private Map<Integer, FoldingRegion> calculateFoldingRegions(String text) {
        Map<Integer, FoldingRegion> regions = new TreeMap<>();
        String[] lines = text.split("\n");

        // Stack to track open elements
        Stack<ElementPosition> elementStack = new Stack<>();
        Map<String, Stack<ElementPosition>> elementStacks = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            final int lineNumber = i;

            // Check for opening tags
            Matcher openMatcher = OPEN_TAG_PATTERN.matcher(line);
            while (openMatcher.find()) {
                String tagName = openMatcher.group(1);

                // Skip self-closing tags
                if (!line.contains("/>")) {
                    ElementPosition pos = new ElementPosition(tagName, lineNumber, openMatcher.start());

                    elementStacks.computeIfAbsent(tagName, k -> new Stack<>()).push(pos);
                    elementStack.push(pos);
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

        // Handle unclosed elements (partial documents)
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
     * Sets up folding indicators in the line number area
     */
    protected void setupFoldingIndicators() {
        IntFunction<Node> graphicFactory = line -> {
            HBox hbox = new HBox(2);
            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Add folding indicator if this line has a foldable region
            if (foldingRegions.containsKey(line)) {
                Button foldButton = createFoldButton(line);
                hbox.getChildren().add(foldButton);
            } else {
                // Add spacer to maintain alignment
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                spacer.setPrefSize(12, 12);
                hbox.getChildren().add(spacer);
            }

            // Add line number
            Node lineNumber = LineNumberFactory.get(codeArea).apply(line);
            hbox.getChildren().add(lineNumber);

            return hbox;
        };

        codeArea.setParagraphGraphicFactory(graphicFactory);
    }

    /**
     * Creates a fold/unfold button for a specific line
     */
    private Button createFoldButton(int line) {
        Button button = new Button();
        button.setPrefSize(12, 12);
        button.setMinSize(12, 12);
        button.setMaxSize(12, 12);
        button.getStyleClass().add("fold-button");

        // Create triangle indicator
        Polygon triangle = new Polygon();
        boolean isFolded = foldedRegions.contains(line);

        if (isFolded) {
            // Right-pointing triangle for folded state
            triangle.getPoints().addAll(2.0, 2.0,
                    2.0, 10.0,
                    10.0, 6.0);
        } else {
            // Down-pointing triangle for unfolded state
            triangle.getPoints().addAll(2.0, 2.0,
                    10.0, 2.0,
                    6.0, 10.0);
        }

        triangle.setFill(Color.GRAY);
        button.setGraphic(triangle);

        // Handle click to toggle fold
        button.setOnAction(event -> toggleFold(line));

        // Add tooltip
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(
                isFolded ? "Unfold region" : "Fold region"
        );
        javafx.scene.control.Tooltip.install(button, tooltip);

        return button;
    }

    /**
     * Toggles the fold state of a region
     */
    public void toggleFold(int line) {
        FoldingRegion region = foldingRegions.get(line);
        if (region == null) return;

        if (foldedRegions.contains(line)) {
            unfold(line);
        } else {
            fold(line);
        }
    }

    /**
     * Folds a region at the specified line
     */
    public void fold(int line) {
        FoldingRegion region = foldingRegions.get(line);
        if (region == null) return;

        try {
            isUpdatingFromFoldOperation = true;
            int oldCaret = codeArea.getCaretPosition();
            int oldAnchor = codeArea.getAnchor();
            String text = codeArea.getText();
            String[] lines = text.split("\n");

            // Calculate the text to hide
            int startLine = region.startLine;
            int endLine = Math.min(region.endLine, lines.length - 1);

            // Create placeholder text
            String placeholder = String.format("<%s.../>", region.elementName);

            // Store the original text
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

            // Mark as folded
            foldedRegions.add(line);

            // Refresh display
            refreshFoldingDisplay();

            logger.debug("Folded region at line {}: {}", line, region);

        } catch (Exception e) {
            logger.error("Error folding region at line {}: {}", line, e.getMessage());
        } finally {
            isUpdatingFromFoldOperation = false;
        }
    }

    /**
     * Unfolds a region at the specified line
     */
    public void unfold(int line) {
        if (!foldedRegions.contains(line)) return;

        String originalText = collapsedText.get(line);
        if (originalText == null) return;

        try {
            isUpdatingFromFoldOperation = true;
            int oldCaret = codeArea.getCaretPosition();
            int oldAnchor = codeArea.getAnchor();
            FoldingRegion region = foldingRegions.get(line);
            String text = codeArea.getText();
            String[] lines = text.split("\n");

            // Find the placeholder
            String placeholder = String.format("<%s.../>", region.elementName);
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

                // Remove from folded set
                foldedRegions.remove(line);
                collapsedText.remove(line);

                // Refresh display
                refreshFoldingDisplay();

                logger.debug("Unfolded region at line {}", line);
            }

        } catch (Exception e) {
            logger.error("Error unfolding region at line {}: {}", line, e.getMessage());
        } finally {
            isUpdatingFromFoldOperation = false;
        }
    }

    /**
     * Folds all foldable regions
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
        int len = codeArea.getLength();
        int restoreCaret = Math.max(0, Math.min(oldCaret, len));
        int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
        if (restoreCaret != restoreAnchor) {
            codeArea.selectRange(restoreAnchor, restoreCaret);
        } else {
            codeArea.moveTo(restoreCaret);
        }
    }

    /**
     * Unfolds all folded regions
     */
    public void unfoldAll() {
        List<Integer> foldedLines = new ArrayList<>(foldedRegions);
        int oldCaret = codeArea.getCaretPosition();
        int oldAnchor = codeArea.getAnchor();
        for (Integer line : foldedLines) {
            unfold(line);
        }
        int len = codeArea.getLength();
        int restoreCaret = Math.max(0, Math.min(oldCaret, len));
        int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
        if (restoreCaret != restoreAnchor) {
            codeArea.selectRange(restoreAnchor, restoreCaret);
        } else {
            codeArea.moveTo(restoreCaret);
        }
    }

    /**
     * Folds all regions at a specific level
     */
    public void foldLevel(int level) {
        for (Map.Entry<Integer, FoldingRegion> entry : foldingRegions.entrySet()) {
            if (entry.getValue().indentLevel == level && !foldedRegions.contains(entry.getKey())) {
                fold(entry.getKey());
            }
        }
    }

    /**
     * Gets the current folding regions
     */
    public Map<Integer, FoldingRegion> getFoldingRegions() {
        return Collections.unmodifiableMap(foldingRegions);
    }

    /**
     * Gets the currently folded regions
     */
    public Set<Integer> getFoldedRegions() {
        return Collections.unmodifiableSet(foldedRegions);
    }

    // Helper methods

    private int getLineStartPosition(int line) {
        String text = codeArea.getText();
        String[] lines = text.split("\n");
        int pos = 0;
        for (int i = 0; i < line && i < lines.length; i++) {
            pos += lines[i].length() + 1; // +1 for newline
        }
        return pos;
    }

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
        return spaces / 4; // Assuming 4 spaces per indent level
    }

    public void refreshFoldingDisplay() {
        // Trigger a refresh of the paragraph graphics
        // Trigger a refresh - RichTextFX doesn't have recreateParagraphGraphics
        // We'll use a workaround by updating the paragraph graphic factory
        var currentFactory = codeArea.getParagraphGraphicFactory();
        codeArea.setParagraphGraphicFactory(null);
        codeArea.setParagraphGraphicFactory(currentFactory);
    }

    // Inner classes

    /**
         * Represents a foldable region in the code
         * @param startLine The starting line number
         * @param endLine The ending line number
         * @param elementName The name of the element
         * @param indentLevel The indentation level
         */
        public record FoldingRegion(int startLine, int endLine, String elementName, int indentLevel) {

        @Override
            public String toString() {
                return String.format("FoldingRegion[%d-%d, %s, level=%d]",
                        startLine, endLine, elementName, indentLevel);
            }
        }

    /**
         * Tracks the position of an XML element
         */
        private record ElementPosition(String tagName, int line, int column) {
    }
}