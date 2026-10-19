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
        this.syntaxChanged = true;
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

    /** A new syntax layer arrived since the last apply (otherwise only overlays changed). */
    private boolean syntaxChanged;
    /** An apply is queued for the next pulse: several layer updates in one pulse apply once. */
    private boolean applyQueued;
    /** The overlays as last applied, so an overlay-only update restyles just what changed. */
    private NavigableMap<Integer, Integer> appliedErrors = new TreeMap<>();
    private NavigableMap<Integer, Integer> appliedMatches = new TreeMap<>();

    /** Queues the merged styles for the FX thread (coalesced per pulse). */
    private void applyStyles() {
        if (applyQueued) {
            return;
        }
        applyQueued = true;
        Platform.runLater(() -> {
            applyQueued = false;
            try {
                applyNow();
            } catch (Exception e) {
                logger.error("Error applying combined styles", e);
            }
        });
    }

    /**
     * Applies the layers. A new syntax layer restyles the whole text in one linear pass (directly when
     * there are no overlays). An overlay-only change restyles just the ranges that gained or lost an
     * overlay, on top of the styles the text already has: rebuilding the whole document's spans for every
     * search keystroke froze large files, and in viewport mode (no syntax layer) it wiped the highlighting.
     */
    void applyNow() {
        int textLength = codeArea.getLength();
        if (textLength == 0) {
            return;
        }
        if (syntaxChanged && syntaxStyles != null) {
            syntaxChanged = false;
            StyleSpans<Collection<String>> base = fitted(syntaxStyles, textLength);
            codeArea.setStyleSpans(0, errorRanges.isEmpty() && matchRanges.isEmpty() ? base : withOverlays(base, 0));
        } else {
            for (int[] region : changedRegions(textLength)) {
                StyleSpans<Collection<String>> current = codeArea.getStyleSpans(region[0], region[1]);
                codeArea.setStyleSpans(region[0], withOverlays(withoutOverlays(current), region[0]));
            }
        }
        appliedErrors = errorRanges;
        appliedMatches = matchRanges;
    }

    /** {@code styles} cut or padded (unstyled) to exactly {@code length}. */
    private static StyleSpans<Collection<String>> fitted(StyleSpans<Collection<String>> styles, int length) {
        if (styles.length() == length) {
            return styles;
        }
        if (styles.length() > length) {
            return styles.subView(0, length);
        }
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        builder.addAll(styles);
        builder.add(Collections.emptyList(), length - styles.length());
        return builder.create();
    }

    /** The ranges covered by an overlay now or at the last apply, merged and clipped to the text. */
    private List<int[]> changedRegions(int textLength) {
        NavigableMap<Integer, Integer> all = new TreeMap<>();
        for (var layer : List.of(appliedErrors, appliedMatches, errorRanges, matchRanges)) {
            layer.forEach((start, end) -> all.merge(start, end, Math::max));
        }
        List<int[]> regions = new ArrayList<>();
        for (var range : all.entrySet()) {
            int start = range.getKey();
            int end = Math.min(range.getValue(), textLength);
            if (start >= end) {
                continue;
            }
            int[] last = regions.isEmpty() ? null : regions.get(regions.size() - 1);
            if (last != null && start <= last[1]) {
                last[1] = Math.max(last[1], end);
            } else {
                regions.add(new int[]{start, end});
            }
        }
        return regions;
    }

    /** {@code styles} without the overlay classes (what the syntax layer painted). */
    private static StyleSpans<Collection<String>> withoutOverlays(StyleSpans<Collection<String>> styles) {
        return styles.mapStyles(style -> style.contains(ERROR_STYLE) || style.contains(MATCH_STYLE)
                ? style.stream().filter(c -> !ERROR_STYLE.equals(c) && !MATCH_STYLE.equals(c)).toList()
                : style);
    }

    /**
     * Adds the overlay classes to {@code base}, which starts at text offset {@code offset}: base spans are
     * split exactly where an overlay starts or ends. One pass over the spans.
     */
    private StyleSpans<Collection<String>> withOverlays(StyleSpans<Collection<String>> base, int offset) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = offset;
        boolean added = false;
        for (var span : base) {
            int spanEnd = pos + span.getLength();
            int cur = pos;
            while (cur < spanEnd) {
                int next = Math.min(spanEnd, Math.min(nextBoundary(errorRanges, cur), nextBoundary(matchRanges, cur)));
                boolean error = covered(errorRanges, cur);
                boolean match = covered(matchRanges, cur);
                Collection<String> style = span.getStyle();
                if (error || match) {
                    Set<String> classes = new HashSet<>(style);
                    if (error) {
                        classes.add(ERROR_STYLE);
                    }
                    if (match) {
                        classes.add(MATCH_STYLE);
                    }
                    style = classes;
                }
                builder.add(style, next - cur);
                added = true;
                cur = next;
            }
            pos = spanEnd;
        }
        if (!added) {
            builder.add(Collections.emptyList(), 0);
        }
        return builder.create();
    }

    /** @return the first overlay boundary (a range start or end) after {@code offset}, or MAX_VALUE */
    private static int nextBoundary(NavigableMap<Integer, Integer> ranges, int offset) {
        int next = Integer.MAX_VALUE;
        var containing = ranges.floorEntry(offset);
        if (containing != null && containing.getValue() > offset) {
            next = containing.getValue();
        }
        Integer nextStart = ranges.higherKey(offset);
        if (nextStart != null) {
            next = Math.min(next, nextStart);
        }
        return next;
    }

    private static boolean covered(NavigableMap<Integer, Integer> ranges, int offset) {
        var entry = ranges.floorEntry(offset);
        return entry != null && entry.getValue() > offset;
    }
}
