package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * {@link CombinedStyleManager} merges syntax spans with the error and search overlays in one linear pass,
 * and restyles only the changed ranges when just an overlay changes (search typing on large files, and
 * viewport mode, where there is no syntax layer to rebuild from).
 */
@ExtendWith(ApplicationExtension.class)
class CombinedStyleManagerTest {

    private CodeArea area;
    private CombinedStyleManager manager;

    @Start
    void start(Stage stage) {
        area = new CodeArea();
        manager = new CombinedStyleManager(area);
        stage.setScene(new Scene(area, 400, 300));
        stage.show();
    }

    private static Collection<String> styleAt(CodeArea area, int offset) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> area.getStyleAtPosition(offset + 1));
    }

    @Test
    void fullMergeMatchesTheFormerAlgorithm() {
        Random random = new Random(7);
        for (int round = 0; round < 60; round++) {
            int length = 50 + random.nextInt(400);
            StyleSpans<Collection<String>> syntax = randomSpans(random, length + random.nextInt(21) - 10);
            NavigableMap<Integer, Integer> errors = randomRanges(random, length);
            NavigableMap<Integer, Integer> matches = randomRanges(random, length);

            List<Collection<String>> actual = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                area.replaceText("x".repeat(length));
                manager.setErrorRanges(lengths(errors));
                manager.setMatchRanges(lengths(matches));
                manager.setSyntaxStyles(syntax);
                manager.applyNow();
                return perChar(area.getStyleSpans(0, length), length);
            });
            List<Collection<String>> expected = perChar(legacyMerge(syntax, errors, matches, length), length);
            assertEquals(expected, actual, "round " + round);
        }
    }

    @Test
    void clearingTheSearchKeepsViewportHighlighting() {
        // viewport mode: the highlighter styles the text directly, there is no syntax layer
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText("<root>text</root>");
            area.setStyle(0, 6, List.of("tagmark"));
            manager.setMatchRanges(Map.of(1, 4));
            manager.applyNow();
            return null;
        });
        assertTrue(styleAt(area, 1).containsAll(List.of("tagmark", CombinedStyleManager.MATCH_STYLE)));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            manager.clearMatches();
            manager.applyNow();
            return null;
        });
        assertEquals(List.of("tagmark"), List.copyOf(styleAt(area, 1)),
                "removing the overlay must leave the highlighting underneath intact");
    }

    @Test
    void anOverlayOnlyChangeLeavesOtherTextUntouched() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText("0123456789");
            StyleSpansBuilder<Collection<String>> syntax = new StyleSpansBuilder<>();
            syntax.add(List.of("anytag"), 10);
            manager.setSyntaxStyles(syntax.create());
            manager.applyNow();
            area.setStyle(8, 10, List.of("marker")); // not an overlay range: an overlay update must not touch it
            manager.setErrorRanges(Map.of(2, 3));
            manager.applyNow();
            return null;
        });
        assertTrue(styleAt(area, 2).containsAll(List.of("anytag", CombinedStyleManager.ERROR_STYLE)));
        assertEquals(List.of("anytag"), List.copyOf(styleAt(area, 5)));
        assertEquals(List.of("marker"), List.copyOf(styleAt(area, 8)));
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private static StyleSpans<Collection<String>> randomSpans(Random random, int length) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        String[] classes = {"tagmark", "anytag", "attribute", "avalue", "comment"};
        int remaining = Math.max(1, length);
        while (remaining > 0) {
            int len = Math.min(remaining, 1 + random.nextInt(25));
            builder.add(random.nextInt(4) == 0 ? List.of() : List.of(classes[random.nextInt(classes.length)]), len);
            remaining -= len;
        }
        return builder.create();
    }

    private static NavigableMap<Integer, Integer> randomRanges(Random random, int length) {
        NavigableMap<Integer, Integer> ranges = new TreeMap<>();
        int count = random.nextInt(5);
        for (int i = 0; i < count; i++) {
            int start = random.nextInt(length);
            ranges.merge(start, start + 1 + random.nextInt(30), Math::max);
        }
        return ranges;
    }

    private static Map<Integer, Integer> lengths(NavigableMap<Integer, Integer> ranges) {
        Map<Integer, Integer> out = new HashMap<>();
        ranges.forEach((start, end) -> out.put(start, end - start));
        return out;
    }

    private static List<Collection<String>> perChar(StyleSpans<Collection<String>> spans, int length) {
        List<Collection<String>> out = new ArrayList<>();
        for (var span : spans) {
            for (int i = 0; i < span.getLength() && out.size() < length; i++) {
                out.add(Set.copyOf(span.getStyle()));
            }
        }
        while (out.size() < length) {
            out.add(Set.of());
        }
        return out;
    }

    /** The merge before 2026-10-19 (boundary set over the whole text), as the oracle. */
    private static StyleSpans<Collection<String>> legacyMerge(StyleSpans<Collection<String>> syntaxStyles,
                                                              NavigableMap<Integer, Integer> errorRanges,
                                                              NavigableMap<Integer, Integer> matchRanges,
                                                              int textLength) {
        List<int[]> syntax = new ArrayList<>();
        List<Collection<String>> syntaxClasses = new ArrayList<>();
        int pos = 0;
        for (var span : syntaxStyles) {
            syntax.add(new int[]{pos, pos + span.getLength()});
            syntaxClasses.add(span.getStyle());
            pos += span.getLength();
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
        for (int boundary : boundaries) {
            if (prev != null && boundary > prev) {
                int segStart = prev;
                Set<String> classes = new HashSet<>();
                while (syntaxIndex < syntax.size() && syntax.get(syntaxIndex)[1] <= segStart) {
                    syntaxIndex++;
                }
                if (syntaxIndex < syntax.size() && segStart >= syntax.get(syntaxIndex)[0]
                        && segStart < Math.min(syntax.get(syntaxIndex)[1], syntaxEnd)) {
                    classes.addAll(syntaxClasses.get(syntaxIndex));
                }
                if (covered(errorRanges, segStart)) {
                    classes.add(CombinedStyleManager.ERROR_STYLE);
                }
                if (covered(matchRanges, segStart)) {
                    classes.add(CombinedStyleManager.MATCH_STYLE);
                }
                builder.add(classes.isEmpty() ? Collections.emptyList() : classes, boundary - segStart);
            }
            prev = boundary;
        }
        return builder.create();
    }

    private static boolean covered(NavigableMap<Integer, Integer> ranges, int offset) {
        var entry = ranges.floorEntry(offset);
        return entry != null && entry.getValue() > offset;
    }
}
