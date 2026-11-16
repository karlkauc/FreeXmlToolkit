package org.fxt.freexmltoolkit.service.xsltproc;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.external.CommandTemplate;
import org.fxt.freexmltoolkit.service.external.ExternalProcess;

/**
 * Runs one transformation with an external XSLT processor and returns it as the same
 * {@link XsltTransformationResult} Saxon HE gives, so every caller shows it unchanged.
 *
 * <p>Texts that are not saved (the editor's text of a file, a generated document) are written to a hidden
 * temporary file <em>in the original's folder</em>, so relative {@code xsl:include}, {@code xsl:import} and
 * {@code document()} references resolve as they do for the file, and deleted afterwards. The result is read
 * from a temporary output file (encoding from its XML declaration or BOM, else UTF-8). A non-zero exit code,
 * a stopped run or a missing result is an error; the program's output becomes the error message (with line,
 * code and phase where the processor reports them) and messages ({@code xsl:message}).</p>
 */
public final class ExternalXsltRunner {

    private static final Logger logger = LogManager.getLogger(ExternalXsltRunner.class);

    private static final Pattern DECLARED_ENCODING = Pattern.compile(
            "^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    /**
     * One transformation.
     *
     * @param xmlText    the input's text, or {@code null} to use {@code xmlFile} as it is
     * @param xmlFile    the input file (or, with {@code xmlText}, where its text belongs); may be {@code null} with a text
     * @param xslText    the stylesheet's text, or {@code null} to use {@code xslFile} as it is
     * @param xslFile    the stylesheet file (or, with {@code xslText}, where its text belongs)
     * @param parameters stylesheet parameters (name → value, in order)
     * @param format     the output format the caller shows the result as (may be {@code null})
     */
    public record Request(String xmlText, Path xmlFile, String xslText, Path xslFile, Map<String, ?> parameters,
                          XsltTransformationEngine.OutputFormat format) {

        /** @return the folder relative references resolve against (the stylesheet's, else the input's) */
        public Path baseDir() {
            for (Path candidate : new Path[] {xslFile, xmlFile}) {
                if (candidate != null && candidate.toAbsolutePath().getParent() != null) {
                    return candidate.toAbsolutePath().getParent();
                }
            }
            return Path.of("").toAbsolutePath();
        }
    }

    private ExternalXsltRunner() {
    }

    /**
     * Runs the processor. Never throws for a failing program or stylesheet: that is an error result.
     *
     * @throws InterruptedException when the calling thread is interrupted (the program is stopped)
     */
    public static XsltTransformationResult transform(XsltProcessorConfig config, Request request) throws InterruptedException {
        long start = System.currentTimeMillis();
        String problem = config.validate();
        if (problem != null) {
            return XsltTransformationResult.error(config.name() + ": " + problem);
        }
        if (request.xslText() == null && request.xslFile() == null) {
            return XsltTransformationResult.error(config.name() + ": no stylesheet to run.");
        }
        Path command = config.resolveExecutable().orElse(null);
        if (command == null) {
            return XsltTransformationResult.error(config.name() + ": program not found"
                    + (config.executable().isEmpty() ? " on the PATH (" + String.join(", ", config.preset().programNames()) + ")"
                    : " (" + config.executable() + ")") + ". Choose it in Settings → XSLT Processors.");
        }
        Path baseDir = request.baseDir();
        Path workingDir = config.workingDir().isEmpty() ? baseDir : Path.of(config.workingDir());
        if (!Files.isDirectory(workingDir)) {
            return XsltTransformationResult.error(config.name() + ": the working folder " + workingDir + " does not exist.");
        }
        List<Path> temporary = new ArrayList<>();
        try {
            Path xsl = request.xslText() == null ? request.xslFile().toAbsolutePath()
                    : temporary(request.xslText(), request.xslFile(), ".xsl", temporary);
            Path xml = request.xmlText() == null
                    ? (request.xmlFile() == null ? null : request.xmlFile().toAbsolutePath())
                    : temporary(request.xmlText(), request.xmlFile() != null ? request.xmlFile() : request.xslFile(), ".xml", temporary);
            Path output = Files.createTempFile("fxt-xslt-out-", ".out");
            temporary.add(output);
            Files.delete(output); // an old file must not pass for this run's result

            Map<String, String> values = new HashMap<>();
            values.put(XsltProcessorConfig.XML, xml == null ? "" : xml.toString());
            values.put(XsltProcessorConfig.XSL, xsl.toString());
            values.put(XsltProcessorConfig.OUT, output.toString());
            values.put(XsltProcessorConfig.BASE_DIR, baseDir.toString());
            Map<String, ?> parameters = request.parameters() == null ? Map.of() : new LinkedHashMap<>(request.parameters());
            List<String> params = config.parameterTemplate().isEmpty() ? List.of()
                    : CommandTemplate.expandParameters(config.parameterTemplate(), parameters);
            List<String> arguments = XsltProcessorConfig.TEMPLATE.expand(config.arguments(), values,
                    Map.of(XsltProcessorConfig.PARAMS, params));
            String unsafe = ExternalProcess.unsafeArguments(config.name(), command, arguments);
            if (unsafe != null) {
                return XsltTransformationResult.error(unsafe);
            }
            List<String> commandLine = new ArrayList<>();
            commandLine.add(command.toString());
            commandLine.addAll(arguments);

            ExternalProcess.Outcome outcome = ExternalProcess.run("external XSLT processor " + config.name(), commandLine,
                    workingDir, config.timeoutSeconds());
            XsltOutputParser.Parsed parsed = XsltOutputParser.parse(outcome.lines(),
                    xml == null ? null : (request.xmlFile() != null ? request.xmlFile() : xml).getFileName().toString());
            XsltTransformationResult result;
            if (!outcome.finished()) {
                result = XsltTransformationResult.error(config.name() + " did not finish within " + config.timeoutSeconds()
                        + " seconds and was stopped.", null, XsltTransformationResult.PHASE_RUNTIME);
            } else if (outcome.exitCode() != 0 || !Files.isRegularFile(output)) {
                String what = outcome.exitCode() != 0 ? " failed (exit code " + outcome.exitCode() + ")"
                        : " finished without writing a result";
                String detail = parsed.errorMessage() != null ? parsed.errorMessage() : lastLines(outcome.lines());
                result = XsltTransformationResult.error(config.name() + what + (detail.isEmpty() ? "." : ":\n" + detail),
                        parsed.errorCode(), parsed.errorPhase() != null ? parsed.errorPhase() : XsltTransformationResult.PHASE_RUNTIME,
                        parsed.errorLine());
            } else {
                result = XsltTransformationResult.success(read(output), request.format(), null);
            }
            result.setMessages(new ArrayList<>(parsed.messages()));
            result.setExecutionTime(System.currentTimeMillis() - start);
            return result;
        } catch (IllegalArgumentException e) {
            return XsltTransformationResult.error(config.name() + ": " + e.getMessage());
        } catch (IOException e) {
            return XsltTransformationResult.error(config.name() + ": " + e.getMessage(), e);
        } finally {
            for (Path file : temporary) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    logger.debug("Could not delete the temporary file {}: {}", file, e.getMessage());
                }
            }
        }
    }

    /** Writes a text to a hidden temporary file in the folder of {@code original} (else the temp folder). */
    private static Path temporary(String text, Path original, String suffix, List<Path> temporary) throws IOException {
        Path dir = original == null ? null : original.toAbsolutePath().getParent();
        Path file;
        try {
            file = dir != null && Files.isDirectory(dir) && Files.isWritable(dir)
                    ? Files.createTempFile(dir, ".fxt-xslt-", suffix) : Files.createTempFile("fxt-xslt-", suffix);
        } catch (IOException | SecurityException e) {
            file = Files.createTempFile("fxt-xslt-", suffix);
        }
        temporary.add(file);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    /** @return the result text: BOM, else the XML declaration's encoding, else UTF-8 */
    static String read(Path output) throws IOException {
        byte[] bytes = Files.readAllBytes(output);
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.ISO_8859_1);
        Matcher m = DECLARED_ENCODING.matcher(head);
        Charset charset = StandardCharsets.UTF_8;
        if (m.find()) {
            try {
                charset = Charset.forName(m.group(1));
            } catch (RuntimeException e) {
                // unknown name: UTF-8
            }
        }
        return new String(bytes, charset);
    }

    private static String lastLines(List<String> lines) {
        int from = Math.max(0, lines.size() - 5);
        return String.join("\n", lines.subList(from, lines.size())).strip();
    }
}
