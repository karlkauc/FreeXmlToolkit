package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.FileSearchResult;
import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.TextSearchQuery;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.service.xmledit.TextEdit;

/**
 * UI-free "Replace in Files" planning and disk application. Plans are computed
 * against a fresh read of each file (or the live editor buffer), guarded by the
 * fingerprint captured at search time: a file that changed since the search is
 * refused, never silently clobbered. Disk writes go through a temp file +
 * atomic move, preserving the detected charset and BOM.
 */
public final class ReplaceApplier {

    private ReplaceApplier() {
    }

    /**
     * All replacements of one file as text edits against {@code baseText}.
     * {@code viaEditor} routes application through the editor buffer (open
     * documents); {@code error} is non-null when planning refused the file.
     */
    public record FileReplacePlan(Path file, Charset charset, boolean bom, String baseText,
                                  boolean viaEditor, EditPlan plan, String error) {
    }

    /** Result of applying one file's plan. */
    public record ReplaceOutcome(Path file, int applied, boolean viaEditor, String error) {
    }

    /**
     * Plans replacements for the checked matches of each search result.
     *
     * @param results       the search results (with fingerprints) to replace in
     * @param checkedStarts per file, the start offsets of the matches to replace
     * @param query         the original query (recompiled to re-find the matches)
     * @param template      the replacement text; in regex mode {@code $1}-style
     *                      group references are expanded, otherwise it is literal
     * @param bufferText    live editor-buffer text per file, or null when not open
     */
    public static List<FileReplacePlan> plan(List<FileSearchResult> results,
                                             Map<Path, Set<Integer>> checkedStarts,
                                             TextSearchQuery query, String template,
                                             Function<Path, String> bufferText) {
        Pattern pattern = FileSearchRunner.compile(query);
        List<FileReplacePlan> plans = new ArrayList<>();
        for (FileSearchResult result : results) {
            Set<Integer> starts = checkedStarts.get(result.file());
            if (starts == null || starts.isEmpty() || result.error() != null) {
                continue;
            }
            plans.add(planFile(result, starts, pattern, query.regex(), template, bufferText));
        }
        return plans;
    }

    private static FileReplacePlan planFile(FileSearchResult result, Set<Integer> checkedStarts,
                                            Pattern pattern, boolean regex, String template,
                                            Function<Path, String> bufferText) {
        Path file = result.file();
        String buffer = bufferText != null ? bufferText.apply(file) : null;
        String baseText;
        Charset charset = result.charset();
        boolean bom = result.bom();
        boolean viaEditor = buffer != null;
        if (viaEditor) {
            baseText = buffer;
        } else {
            try {
                EncodingSniffer.Loaded loaded = EncodingSniffer.load(file);
                baseText = loaded.text();
                charset = loaded.charset();
                bom = loaded.bom();
            } catch (IOException e) {
                return refused(file, "Cannot read file: " + e.getMessage());
            }
        }
        // Staleness guard: refuse when the content no longer matches the search snapshot.
        if (baseText.hashCode() != result.fingerprint().textHash()) {
            return refused(file, "File changed since the search — run the search again");
        }
        String safeTemplate = regex ? template : Matcher.quoteReplacement(template);
        List<TextEdit> edits = new ArrayList<>();
        try {
            Matcher m = pattern.matcher(baseText);
            StringBuilder sb = new StringBuilder();
            int appendPos = 0;
            while (m.find()) {
                if (checkedStarts.contains(m.start())) {
                    int gap = m.start() - appendPos;
                    int lenBefore = sb.length();
                    m.appendReplacement(sb, safeTemplate);
                    String expanded = sb.substring(lenBefore + gap);
                    edits.add(new TextEdit(m.start(), m.end(), expanded));
                    appendPos = m.end();
                }
            }
        } catch (RuntimeException e) {
            // e.g. a group reference in the template that the pattern doesn't define
            return refused(file, "Invalid replacement: " + e.getMessage());
        }
        if (edits.isEmpty()) {
            return refused(file, "Matches no longer found — run the search again");
        }
        return new FileReplacePlan(file, charset, bom, baseText, viaEditor,
                new EditPlan(edits), null);
    }

    private static FileReplacePlan refused(Path file, String reason) {
        return new FileReplacePlan(file, StandardCharsets.UTF_8, false, "", false,
                new EditPlan(List.of()), reason);
    }

    /**
     * Applies a (non-editor) plan to disk: writes the fully edited text to a
     * temp file in the same directory and moves it atomically over the original,
     * preserving charset and BOM. Line endings survive because edits operate on
     * the raw decoded string.
     */
    public static ReplaceOutcome applyToDisk(FileReplacePlan plan) {
        if (plan.error() != null) {
            return new ReplaceOutcome(plan.file(), 0, false, plan.error());
        }
        String newText = plan.plan().applyTo(plan.baseText());
        if (!plan.charset().newEncoder().canEncode(newText)) {
            return new ReplaceOutcome(plan.file(), 0, false,
                    "Replacement contains characters not representable in " + plan.charset());
        }
        byte[] body = newText.getBytes(plan.charset());
        byte[] out;
        if (plan.bom()) {
            byte[] bomBytes = bomFor(plan.charset());
            out = new byte[bomBytes.length + body.length];
            System.arraycopy(bomBytes, 0, out, 0, bomBytes.length);
            System.arraycopy(body, 0, out, bomBytes.length, body.length);
        } else {
            out = body;
        }
        try {
            Path tmp = Files.createTempFile(plan.file().getParent(),
                    plan.file().getFileName().toString(), ".tmp");
            try {
                Files.write(tmp, out);
                try {
                    Files.move(tmp, plan.file(), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, plan.file(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            return new ReplaceOutcome(plan.file(), 0, false, "Write failed: " + e.getMessage());
        }
        return new ReplaceOutcome(plan.file(), plan.plan().edits().size(), false, null);
    }

    private static byte[] bomFor(Charset charset) {
        if (charset.equals(StandardCharsets.UTF_16LE)) {
            return new byte[]{(byte) 0xFF, (byte) 0xFE};
        }
        if (charset.equals(StandardCharsets.UTF_16BE)) {
            return new byte[]{(byte) 0xFE, (byte) 0xFF};
        }
        return new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    }
}
