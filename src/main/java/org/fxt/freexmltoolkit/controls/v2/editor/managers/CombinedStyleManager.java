package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;

/**
 * Combined style manager that merges syntax highlighting and validation error highlighting.
 * This prevents one from overwriting the other.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Combines XML syntax highlighting with validation errors</li>
 *   <li>Validation errors overlay on syntax highlighting</li>
 *   <li>Thread-safe updates</li>
 * </ul>
 */
public class CombinedStyleManager {

    private static final Logger logger = LogManager.getLogger(CombinedStyleManager.class);

    private final CodeArea codeArea;

    // Current styles
    private StyleSpans<Collection<String>> syntaxStyles;
    private Map<Integer, Integer> errorRanges = new HashMap<>(); // start -> length

    /**
     * Creates a new CombinedStyleManager.
     *
     * @param codeArea the code area
     */
    public CombinedStyleManager(CodeArea codeArea) {
        this.codeArea = codeArea;
        logger.info("CombinedStyleManager created");
    }

    /**
     * Sets the syntax highlighting styles.
     *
     * @param styles the syntax highlighting styles
     */
    public void setSyntaxStyles(StyleSpans<Collection<String>> styles) {
        this.syntaxStyles = styles;
        applyStyles();
    }

    /**
     * Sets the validation error ranges.
     *
     * @param errorRanges map of error start position to error length
     */
    public void setErrorRanges(Map<Integer, Integer> errorRanges) {
        this.errorRanges = new HashMap<>(errorRanges);
        applyStyles();
    }

    /**
     * Clears all error ranges.
     */
    public void clearErrors() {
        this.errorRanges.clear();
        applyStyles();
    }

    /**
     * Applies combined styles (syntax + errors) to the CodeArea.
     */
    private void applyStyles() {
        Platform.runLater(() -> {
            try {
                String text = codeArea.getText();
                if (text == null || text.isEmpty()) {
                    return;
                }

                // If no syntax styles, just apply error styles
                if (syntaxStyles == null) {
                    applyErrorStylesOnly(text);
                    return;
                }

                // Merge syntax styles with error styles
                StyleSpans<Collection<String>> combined = mergeStyles(text);
                codeArea.setStyleSpans(0, combined);

                logger.debug("Applied combined styles (syntax + {} errors)", errorRanges.size());

            } catch (Exception e) {
                logger.error("Error applying combined styles", e);
            }
        });
    }

    /**
     * Applies only error styles when no syntax highlighting is available.
     *
     * @param text the text
     */
    private void applyErrorStylesOnly(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();

        int currentPos = 0;

        // Sort error positions
        List<Integer> sortedPositions = new ArrayList<>(errorRanges.keySet());
        Collections.sort(sortedPositions);

        for (int errorPos : sortedPositions) {
            int errorLength = errorRanges.get(errorPos);

            // Add normal text before error
            if (errorPos > currentPos) {
                builder.add(Collections.emptyList(), errorPos - currentPos);
            }

            // Add error style
            builder.add(Collections.singleton("validation-error"), errorLength);
            currentPos = errorPos + errorLength;
        }

        // Add remaining text
        if (currentPos < text.length()) {
            builder.add(Collections.emptyList(), text.length() - currentPos);
        }

        codeArea.setStyleSpans(0, builder.create());
    }

    /**
     * Merges syntax highlighting styles with error styles.
     * Error styles are added as additional classes to existing syntax styles.
     *
     * @param text the text
     * @return merged StyleSpans
     */
    private StyleSpans<Collection<String>> mergeStyles(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();

        int pos = 0;

        // Iterate through syntax styles
        for (var span : syntaxStyles) {
            int spanLength = span.getLength();
            Collection<String> syntaxClasses = span.getStyle();

            // Check if this span overlaps with any error ranges
            Set<String> mergedClasses = new HashSet<>(syntaxClasses);
            boolean hasError = false;

            for (Map.Entry<Integer, Integer> error : errorRanges.entrySet()) {
                int errorStart = error.getKey();
                int errorEnd = errorStart + error.getValue();

                // Check for overlap
                if (!(pos >= errorEnd || (pos + spanLength) <= errorStart)) {
                    mergedClasses.add("validation-error");
                    hasError = true;
                    break;
                }
            }

            builder.add(mergedClasses, spanLength);
            pos += spanLength;
        }

        return builder.create();
    }

    /**
     * Gets the current error ranges.
     *
     * @return map of error start position to error length
     */
    public Map<Integer, Integer> getErrorRanges() {
        return new HashMap<>(errorRanges);
    }
}
