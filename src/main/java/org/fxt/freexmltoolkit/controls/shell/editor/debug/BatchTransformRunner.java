package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * UI-free multi-file transform runner: applies one stylesheet / XQuery to each XML file
 * independently, collecting a {@link BatchFileResult} per file. Run off the UI thread.
 */
public final class BatchTransformRunner {

    private BatchTransformRunner() {
    }

    /** Applies {@code xsltContent} to every file; errors are captured per file, never thrown. */
    public static List<BatchFileResult> runXsltBatch(List<File> files, String xsltContent,
            Map<String, Object> parameters, OutputFormat format) {
        List<BatchFileResult> results = new ArrayList<>();
        for (File file : files) {
            long start = System.nanoTime();
            try {
                String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String out = TransformRunner.xsltTransform(xml, xsltContent, parameters, format);
                long ms = (System.nanoTime() - start) / 1_000_000;
                boolean ok = out != null && !out.startsWith("ERROR");
                results.add(new BatchFileResult(file, ok ? out : null, ok, ok ? null : out, ms));
            } catch (Exception e) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                results.add(new BatchFileResult(file, null, false, "ERROR: " + e.getMessage(), ms));
            }
        }
        return results;
    }

    /** Runs {@code xqueryContent} against every file's context independently. */
    public static List<BatchFileResult> runXQueryBatch(List<File> files, String xqueryContent,
            Map<String, Object> externalVariables, OutputFormat format) {
        List<BatchFileResult> results = new ArrayList<>();
        for (File file : files) {
            long start = System.nanoTime();
            try {
                String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String out = TransformRunner.runXQuery(xml, xqueryContent, externalVariables, format);
                long ms = (System.nanoTime() - start) / 1_000_000;
                boolean ok = out != null && !out.startsWith("ERROR");
                results.add(new BatchFileResult(file, ok ? out : null, ok, ok ? null : out, ms));
            } catch (Exception e) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                results.add(new BatchFileResult(file, null, false, "ERROR: " + e.getMessage(), ms));
            }
        }
        return results;
    }

    /**
     * Writes each successful result to {@code targetDir} as {@code <basename>.<extension>}.
     *
     * @return the number of files written
     */
    public static int writeAll(List<BatchFileResult> results, Path targetDir, String extension) {
        int written = 0;
        for (BatchFileResult r : results) {
            if (!r.ok() || r.output() == null) {
                continue;
            }
            String base = r.file().getName().replaceFirst("\\.[^.]+$", "");
            Path out = targetDir.resolve(base + "." + extension);
            try {
                Files.writeString(out, r.output(), StandardCharsets.UTF_8);
                written++;
            } catch (Exception ignored) {
                // skip unwritable files; caller reports the written count
            }
        }
        return written;
    }
}
