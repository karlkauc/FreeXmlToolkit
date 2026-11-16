package org.fxt.freexmltoolkit.service.fo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.FopEvent;
import org.fxt.freexmltoolkit.service.external.ExternalProcess;

/**
 * Renders XSL-FO with an external processor ({@link FoProcessorConfig}): writes the FO to a file,
 * starts the program with the expanded argument template (no shell), collects its output as
 * {@link FopEvent}s and checks that the output file was written.
 *
 * <p>The FO file is written into the run's base folder (the stylesheet's folder when there is
 * one), so relative references in the FO (images, fonts) resolve as they do next to the
 * stylesheet; when that folder is not writable it goes to the temp folder. It is deleted afterwards.
 */
public final class ExternalFoRenderer {

    private static final Logger logger = LogManager.getLogger(ExternalFoRenderer.class);

    /** Output lines kept per run (the oldest are dropped). */
    static final int MAX_OUTPUT_LINES = ExternalProcess.MAX_OUTPUT_LINES;

    private ExternalFoRenderer() {
    }

    /**
     * What one run renders.
     *
     * @param foXml   the XSL-FO text, or {@code null} when {@code foFile} is to be rendered as it is
     * @param foFile  an FO file on disk (base folder; rendered directly when {@code foXml} is null)
     * @param output  the file to write
     * @param xml     the XML input of the XSLT stage, or {@code null}
     * @param xsl     the stylesheet of the XSLT stage, or {@code null}
     * @param title   PDF title metadata ({@code ""} = none)
     * @param author  PDF author metadata
     * @param subject PDF subject metadata
     * @param pdfA    whether PDF/A was asked for
     */
    public record Request(String foXml, Path foFile, Path output, Path xml, Path xsl,
                          String title, String author, String subject, boolean pdfA) {

        /** @return the folder relative references resolve against */
        public Path baseDir() {
            for (Path candidate : new Path[] {xsl, foFile, xml, output}) {
                if (candidate != null && candidate.toAbsolutePath().getParent() != null) {
                    return candidate.toAbsolutePath().getParent();
                }
            }
            return Path.of("").toAbsolutePath();
        }
    }

    /**
     * Runs the processor.
     *
     * @return the program's messages
     * @throws FoProcessorException when the program is missing, fails, times out or writes no output
     * @throws InterruptedException when the calling thread is interrupted (the program is stopped)
     */
    public static List<FopEvent> render(FoProcessorConfig config, Request request)
            throws FoProcessorException, InterruptedException {
        String templateProblem = ArgumentTemplate.validate(config.arguments());
        if (templateProblem != null) {
            throw new FoProcessorException(config.name() + ": " + templateProblem, List.of());
        }
        Path command = config.resolveExecutable().orElseThrow(() -> new FoProcessorException(
                config.name() + ": program not found"
                        + (config.executable().isEmpty() ? " on the PATH (" + String.join(", ", config.preset().programNames()) + ")"
                        : " (" + config.executable() + ")")
                        + ". Choose it in Settings → XSL-FO Processors.", List.of()));
        Path output = request.output().toAbsolutePath();
        Path baseDir = request.baseDir();
        Path workingDir = config.workingDir().isEmpty() ? baseDir : Path.of(config.workingDir());
        if (!Files.isDirectory(workingDir)) {
            throw new FoProcessorException(config.name() + ": the working folder " + workingDir + " does not exist.",
                    List.of());
        }

        Path foFile = null;
        boolean temporaryFo = request.foXml() != null || request.foFile() == null;
        try {
            Files.createDirectories(output.getParent());
            try {
                Files.deleteIfExists(output); // an old file must not pass for this run's result
            } catch (IOException e) {
                throw new FoProcessorException("Cannot replace " + output + " (is it open in another program?).", e);
            }
            foFile = temporaryFo ? writeFo(request, baseDir) : request.foFile().toAbsolutePath();

            List<String> arguments = ArgumentTemplate.expand(config.arguments(), values(request, foFile, output, baseDir));
            String unsafe = ExternalProcess.unsafeArguments(config.name(), command, arguments);
            if (unsafe != null) {
                throw new FoProcessorException(unsafe, List.of());
            }
            List<String> commandLine = new ArrayList<>();
            commandLine.add(command.toString());
            commandLine.addAll(arguments);
            return run(config, commandLine, workingDir, output);
        } catch (IOException e) {
            throw new FoProcessorException(config.name() + ": " + e.getMessage(), e);
        } finally {
            if (temporaryFo && foFile != null) {
                try {
                    Files.deleteIfExists(foFile);
                } catch (IOException e) {
                    logger.debug("Could not delete the temporary FO {}: {}", foFile, e.getMessage());
                }
            }
        }
    }

    private static List<FopEvent> run(FoProcessorConfig config, List<String> commandLine, Path workingDir, Path output)
            throws IOException, FoProcessorException, InterruptedException {
        ExternalProcess.Outcome outcome = ExternalProcess.run("external FO processor " + config.name(), commandLine,
                workingDir, config.timeoutSeconds());
        List<FopEvent> events = OutputLineParser.parse(outcome.lines(), config.preset(), config.name());
        if (!outcome.finished()) {
            throw new FoProcessorException(config.name() + " did not finish within " + config.timeoutSeconds()
                    + " seconds and was stopped.", events);
        }
        if (outcome.exitCode() != 0) {
            throw new FoProcessorException(config.name() + " failed (exit code " + outcome.exitCode() + ")" + summary(events), events);
        }
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new FoProcessorException(config.name() + " finished without writing " + output.getFileName()
                    + summary(events), events);
        }
        return events;
    }

    /** The FO goes next to the stylesheet when possible, else into the temp folder. */
    private static Path writeFo(Request request, Path baseDir) throws IOException {
        String text = request.foXml() != null ? request.foXml() : "";
        if (request.foXml() == null && request.foFile() != null) {
            text = Files.readString(request.foFile(), StandardCharsets.UTF_8);
        }
        Path file;
        try {
            file = Files.createTempFile(baseDir, ".fxt-render-", ".fo");
        } catch (IOException | SecurityException e) {
            file = Files.createTempFile("fxt-render-", ".fo");
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static Map<String, String> values(Request request, Path foFile, Path output, Path baseDir) {
        Map<String, String> values = new HashMap<>();
        values.put(ArgumentTemplate.FO, foFile.toString());
        values.put(ArgumentTemplate.PDF, output.toString());
        values.put(ArgumentTemplate.OUT, output.toString());
        values.put(ArgumentTemplate.XML, request.xml() == null ? "" : request.xml().toAbsolutePath().toString());
        values.put(ArgumentTemplate.XSL, request.xsl() == null ? "" : request.xsl().toAbsolutePath().toString());
        values.put(ArgumentTemplate.BASE_DIR, baseDir.toString());
        values.put(ArgumentTemplate.TITLE, nullToEmpty(request.title()));
        values.put(ArgumentTemplate.AUTHOR, nullToEmpty(request.author()));
        values.put(ArgumentTemplate.SUBJECT, nullToEmpty(request.subject()));
        values.put(ArgumentTemplate.PDFA, request.pdfA() ? ArgumentTemplate.FLAG_ON : "");
        return values;
    }

    /** @return ": " + the error lines (or the last lines) of the output, or "." */
    private static String summary(List<FopEvent> events) {
        List<String> picked = events.stream().filter(FopEvent::isWarningOrWorse)
                .filter(e -> "ERROR".equals(e.severity())).map(FopEvent::message).limit(5).toList();
        if (picked.isEmpty()) {
            int from = Math.max(0, events.size() - 5);
            picked = events.subList(from, events.size()).stream().map(FopEvent::message).toList();
        }
        return picked.isEmpty() ? "." : ": " + String.join("\n", picked);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
