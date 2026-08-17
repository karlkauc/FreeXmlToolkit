package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;

import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.FileReplacePlan;
import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.ReplaceOutcome;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathEditException;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;

/**
 * UI-free engine for XPath search over a folder: walks the same glob-filtered
 * file set as the text search and evaluates the XPath per file. Files that fail
 * to parse (or where the XPath errors) become error entries — the batch keeps
 * going, mirroring the app's graceful-degradation convention.
 */
public final class XPathBatchRunner {

    private XPathBatchRunner() {
    }

    /** One file's XPath matches, with the exact text snapshot they were located in. */
    public record FileXPathResult(Path file, Charset charset, boolean bom, String baseText,
                                  boolean fromEditorBuffer, List<NodeMatch> matches, String error) {
    }

    /**
     * Evaluates {@code query} against every glob-matched file under {@code root}.
     * Files without matches are omitted; parse/XPath failures are kept as error
     * entries. {@code bufferOverride} supplies live editor text for open files.
     */
    public static List<FileXPathResult> search(Path root, List<String> globs, XPathQuery query,
                                               Function<Path, String> bufferOverride,
                                               IntConsumer onFileDone, BooleanSupplier cancelled) {
        List<Path> files = FileSearchRunner.collectFiles(root, globs, cancelled);
        List<FileXPathResult> results = new ArrayList<>();
        int done = 0;
        for (Path file : files) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            FileXPathResult result = searchFile(file, query, bufferOverride);
            if (result.error() != null || !result.matches().isEmpty()) {
                results.add(result);
            }
            done++;
            if (onFileDone != null) {
                onFileDone.accept(done);
            }
        }
        return results;
    }

    /** Evaluates the query against one file (or its editor buffer). */
    public static FileXPathResult searchFile(Path file, XPathQuery query,
                                             Function<Path, String> bufferOverride) {
        String buffer = bufferOverride != null ? bufferOverride.apply(file) : null;
        String text;
        Charset charset = StandardCharsets.UTF_8;
        boolean bom = false;
        boolean fromBuffer = buffer != null;
        if (fromBuffer) {
            text = buffer;
        } else {
            try {
                EncodingSniffer.Loaded loaded = EncodingSniffer.load(file);
                text = loaded.text();
                charset = loaded.charset();
                bom = loaded.bom();
            } catch (IOException e) {
                return new FileXPathResult(file, charset, false, "", false, List.of(),
                        "Cannot read file: " + e.getMessage());
            }
        }
        try {
            List<NodeMatch> matches = XPathMatchLocator.locate(text, query);
            return new FileXPathResult(file, charset, bom, text, fromBuffer, matches, null);
        } catch (XPathEditException e) {
            return new FileXPathResult(file, charset, bom, text, fromBuffer, List.of(),
                    e.getMessage());
        }
    }

    /**
     * Applies a per-file edit plan to disk. The file is re-read first and refused
     * when it no longer equals the snapshot the matches were located in.
     */
    public static ReplaceOutcome applyToDisk(FileXPathResult result, EditPlan plan) {
        try {
            EncodingSniffer.Loaded current = EncodingSniffer.load(result.file());
            if (!current.text().equals(result.baseText())) {
                return new ReplaceOutcome(result.file(), 0, false,
                        "File changed since the search — run the search again");
            }
        } catch (IOException e) {
            return new ReplaceOutcome(result.file(), 0, false, "Cannot read file: " + e.getMessage());
        }
        return ReplaceApplier.applyToDisk(new FileReplacePlan(result.file(), result.charset(),
                result.bom(), result.baseText(), false, plan, null));
    }
}
