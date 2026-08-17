package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Merges the syntax-highlighting layer with overlay layers — validation errors
 * and search-match highlights — so no layer clobbers another. Overlay ranges are
 * applied with exact character boundaries (syntax spans are split where an
 * overlay starts or ends). Style precedence is CSS-side: {@code search-match}
 * paints a background, {@code validation-error} an underline, so both can show
 * on the same character.
 */
public class CombinedStyleManager {

    private static final Logger logger = LogManager.getLogger(CombinedStyleManager.class);

    /** Style class of the validation-error overlay. */
    public static final String ERROR_STYLE = "validation-error";
    /** Style class of the search-match overlay. */
    public static final String MATCH_STYLE = "search-match";

    private final CodeArea codeArea;

    private StyleSpans<Collection<String>> syntaxStyles;
    /** start → end (exclusive), normalized and non-overlapping per layer. */
    private NavigableMap<Integer, Integer> errorRanges = new TreeMap<>();
    private NavigableMap<Integer, Integer> matchRanges = new TreeMap<>();

    public CombinedStyleManager(CodeArea codeArea) {
        this.codeArea = codeArea;
    }

    /** Sets the syntax layer (called by the highlight manager after each pass). */
    public void setSyntaxStyles(StyleSpans<Collection<String>> styles) {
        this.syntaxStyles = styles;
        applyStyles();
    }

    /** Sets the validation-error overlay: map of start offset → length. */
    public void setErrorRanges(Map<Integer, Integer> ranges) {
        this.errorRanges = toRangeMap(ranges);
        applyStyles();
    }

    /** Clears the validation-error overlay. */
    public void clearErrors() {
        this.errorRanges = new TreeMap<>();
        applyStyles();
    }

    /** Sets the search-match overlay: map of start offset → length. */
    public void setMatchRanges(Map<Integer, Integer> ranges) {
        this.matchRanges = toRangeMap(ranges);
        applyStyles();
    }

    /** Clears the search-match overlay. */
    public void clearMatches() {
        this.matchRanges = new TreeMap<>();
        applyStyles();
    }

    /** @return the current error ranges as start → length (defensive copy). */
    public Map<Integer, Integer> getErrorRanges() {
        Map<Integer, Integer> out = new HashMap<>();
        errorRanges.forEach((start, end) -> out.put(start, end - start));
        return out;
    }

    private static NavigableMap<Integer, Integer> toRangeMap(Map<Integer, Integer> startToLength) {
        NavigableMap<Integer, Integer> map = new TreeMap<>();
        if (startToLength != null) {
            startToLength.forEach((start, length) -> {
                if (start != null && length != null && start >= 0 && length > 0) {
                    map.merge(start, start + length, Math::max);
                }
            });
        }
        return map;
    }

    // ---------------------------------------------------------------------

    /** Recomputes and applies the merged styles on the FX thread. */
    private void applyStyles() {
        Platform.runLater(() -> {
            try {
                String text = codeArea.getText();
                if (text == null || text.isEmpty()) {
                    return;
                }
                codeArea.setStyleSpans(0, mergeStyles(text.length()));
            } catch (Exception e) {
                logger.error("Error applying combined styles", e);
            }
        });
    }

    /**
     * Builds the merged spans over {@code [0, textLength)}: the syntax layer
     * split at every overlay boundary, with overlay classes added exactly on
     * their ranges. Without a syntax layer the overlays render on plain text.
     */
    private StyleSpans<Collection<String>> mergeStyles(int textLength) {
        // Collect all boundaries: syntax span edges + overlay edges.
        List<int[]> syntax = new ArrayList<>(); // {start, end, index-into-styles}
        List<Collection<String>> syntaxClasses = new ArrayList<>();
        int pos = 0;
        if (syntaxStyles != null) {
            for (var span : syntaxStyles) {
                syntax.add(new int[]{pos, pos + span.getLength()});
                syntaxClasses.add(span.getStyle());
                pos += span.getLength();
            }
        }
        int syntaxEnd = pos;
        var boundaries = new java.util.TreeSet<Integer>();
        boundaries.add(0);
        boundaries.add(textLength);
        for (int[] s : syntax) {
            boundaries.add(Math.min(s[0], textLength));
            boundaries.add(Math.min(s[1], textLength));
        }
        for (var layer : List.of(errorRanges, matchRanges)) {
            layer.forEach((start, end) -> {
                if (start < textLength) {
                    boundaries.add(start);
                    boundaries.add(Math.min(end, textLength));
                }
            });
        }

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        Integer prev = null;
        int syntaxIndex = 0;
        boolean added = false;
        for (int boundary : boundaries) {
            if (prev != null && boundary > prev) {
                int segStart = prev;
                Set<String> classes = new HashSet<>();
                // advance to the syntax span containing segStart
                while (syntaxIndex < syntax.size() && syntax.get(syntaxIndex)[1] <= segStart) {
                    syntaxIndex++;
                }
                if (syntaxIndex < syntax.size() && segStart >= syntax.get(syntaxIndex)[0]
                        && segStart < Math.min(syntax.get(syntaxIndex)[1], syntaxEnd)) {
                    classes.addAll(syntaxClasses.get(syntaxIndex));
                }
                if (covered(errorRanges, segStart)) {
                    classes.add(ERROR_STYLE);
                }
                if (covered(matchRanges, segStart)) {
                    classes.add(MATCH_STYLE);
                }
                builder.add(classes.isEmpty() ? Collections.emptyList() : classes,
                        boundary - segStart);
                added = true;
            }
            prev = boundary;
        }
        if (!added) {
            builder.add(Collections.emptyList(), textLength);
        }
        return builder.create();
    }

    private static boolean covered(NavigableMap<Integer, Integer> ranges, int offset) {
        var entry = ranges.floorEntry(offset);
        return entry != null && entry.getValue() > offset;
    }
}
