package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.FOPService;
import org.fxt.freexmltoolkit.service.FopEvent;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;
import org.fxt.freexmltoolkit.service.fo.ExternalFoRenderer;
import org.fxt.freexmltoolkit.service.fo.FoProcessorConfig;
import org.fxt.freexmltoolkit.service.fo.FoProcessorException;
import org.fxt.freexmltoolkit.service.fo.FoProcessorStore;
import org.fxt.freexmltoolkit.service.telemetry.QueryFailure;

/**
 * UI-free PDF generation for the PDF/FOP activity: transforms an XML file with
 * an XSL(-FO) stylesheet to PDF via the reused {@link FOPService}, in two stages
 * (XSLT → XSL-FO → PDF) so the intermediate FO, the stylesheet's {@code xsl:message}
 * output and FOP's rendering events are available to the panel. The render stage runs
 * the bundled Apache FOP or an external XSL-FO processor the user configured
 * ({@link PdfOptions#processor()}, {@link FoProcessorStore}). The string variants return
 * {@code "OK: …"} / {@code "ERROR: …"} rather than throwing.
 */
public final class FopRunner {

    private FopRunner() {
    }

    /** Stage of a generation: the XSLT run that produces the XSL-FO. */
    public static final String STAGE_TRANSFORM = "transform";
    /** Stage of a generation: FOP rendering the XSL-FO to PDF. */
    public static final String STAGE_RENDER = "render";

    /** Finds the external processor of a run (empty = the bundled FOP); replaceable in tests. */
    private static volatile Function<String, Optional<FoProcessorConfig>> processorLookup =
            idOrName -> FoProcessorStore.settings().resolve(idOrName);

    /** Test hook: how processor ids / names are resolved ({@code null} restores the settings). */
    static void setProcessorLookup(Function<String, Optional<FoProcessorConfig>> lookup) {
        processorLookup = lookup != null ? lookup : idOrName -> FoProcessorStore.settings().resolve(idOrName);
    }

    /**
     * Where the FO of a run came from, for the placeholders and the base folder of an external
     * processor; every part may be {@code null}.
     *
     * @param xml    the XML input of the XSLT stage
     * @param xsl    the stylesheet of the XSLT stage
     * @param foFile the FO file being rendered
     */
    public record RenderContext(Path xml, Path xsl, Path foFile) {

        /** @return a context without any file */
        public static RenderContext none() {
            return new RenderContext(null, null, null);
        }
    }

    /** @return whether the id / name stands for the bundled Apache FOP */
    public static boolean isBuiltin(String processor) {
        return processor == null || processor.isBlank() || FoProcessorStore.BUILTIN_ID.equalsIgnoreCase(processor.strip());
    }

    /**
     * The panel's generation options (Figma mockup node 49:2): PDF metadata
     * (title/author/subject), PDF/A-1b conformance, and the page size/orientation,
     * which are passed to the stylesheet as the XSLT parameters {@code page-size}
     * and {@code page-orientation} (honoured by stylesheets that declare them), and the
     * processor that renders the FO: {@code null} / {@value FoProcessorStore#BUILTIN_ID} = the
     * bundled Apache FOP, otherwise the id or name of a configured external processor.
     */
    public record PdfOptions(String title, String author, String subject,
                             boolean pdfACompliant, String pageSize, String orientation, String processor) {

        /** Options rendered by the bundled Apache FOP. */
        public PdfOptions(String title, String author, String subject,
                          boolean pdfACompliant, String pageSize, String orientation) {
            this(title, author, subject, pdfACompliant, pageSize, orientation, null);
        }

        public static PdfOptions none() {
            return new PdfOptions("", "", "", false, "", "");
        }

        /** @return these options rendered by another processor */
        public PdfOptions withProcessor(String processorIdOrName) {
            return new PdfOptions(title, author, subject, pdfACompliant, pageSize, orientation, processorIdOrName);
        }

        /** @return the stylesheet parameters this option set contributes. */
        public HashMap<String, String> stylesheetParameters() {
            HashMap<String, String> parameters = new HashMap<>();
            if (pageSize != null && !pageSize.isBlank()) {
                parameters.put("page-size", pageSize);
            }
            if (orientation != null && !orientation.isBlank()) {
                parameters.put("page-orientation", orientation);
            }
            return parameters;
        }

        PDFSettings toSettings() {
            return new PDFSettings(stylesheetParameters(), "",
                    nullToEmpty(author), "", "", nullToEmpty(title), "");
        }
    }

    /**
     * Outcome of a generation: the PDF (when {@code ok}), the intermediate XSL-FO (whenever
     * the XSLT stage succeeded), the stylesheet's messages and FOP's rendering events, and
     * the error text for a failed run. {@code stage} names the stage that ran last
     * ({@link #STAGE_TRANSFORM} / {@link #STAGE_RENDER}) and {@code failure} classifies a failed
     * run for telemetry (processor error code or exception class, never message text);
     * both are {@code null} for a result that did not come from a run.
     */
    public record FopResult(boolean ok, File pdf, String foXml, List<TransformationMessage> messages,
                            List<FopEvent> events, String error, String stage, QueryFailure failure) {

        public FopResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            events = events == null ? List.of() : List.copyOf(events);
        }

        /** A result without stage / failure classification (e.g. the FO of a debug run). */
        public FopResult(boolean ok, File pdf, String foXml, List<TransformationMessage> messages,
                         List<FopEvent> events, String error) {
            this(ok, pdf, foXml, messages, events, error, null, null);
        }

        /** @return {@code true} if there is anything to show in a messages view. */
        public boolean hasNotes() {
            return !messages.isEmpty() || !events.isEmpty();
        }

        /** @return the number of FOP events at WARN level or worse. */
        public long warningCount() {
            return events.stream().filter(FopEvent::isWarningOrWorse).count();
        }

        /** @return the legacy {@code "OK: path"} / {@code "ERROR: message"} text. */
        public String asText() {
            return ok ? "OK: " + pdf.getAbsolutePath() : "ERROR: " + error;
        }

        /** Renders messages and events as a readable report (one entry per line). */
        public String notesText() {
            StringBuilder sb = new StringBuilder();
            if (!messages.isEmpty()) {
                sb.append("XSLT messages (").append(messages.size()).append(")\n");
                for (TransformationMessage m : messages) {
                    sb.append("  ").append(m.getLevel());
                    if (m.getLocation() != null && !m.getLocation().isBlank()) {
                        sb.append(" @ ").append(m.getLocation());
                    }
                    sb.append(": ").append(m.getMessage()).append('\n');
                }
            }
            if (!events.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("Renderer messages (").append(events.size()).append(")\n");
                for (FopEvent e : events) {
                    sb.append("  ").append(e.severity()).append(" [").append(e.shortId()).append("]: ")
                            .append(e.message()).append('\n');
                }
            }
            if (sb.length() == 0) {
                sb.append("No messages: the stylesheet produced no xsl:message output and the renderer reported nothing.\n");
            }
            return sb.toString();
        }
    }

    /** @return {@code "OK: <path>"} on success, otherwise {@code "ERROR: <message>"}. */
    public static String generate(File xmlFile, File xslFile, File pdfOutput) {
        return generate(xmlFile, xslFile, pdfOutput, PdfOptions.none());
    }

    /** @return {@code "OK: <path>"} on success, otherwise {@code "ERROR: <message>"}. */
    public static String generate(File xmlFile, File xslFile, File pdfOutput, PdfOptions options) {
        return generateResult(xmlFile, xslFile, pdfOutput, options).asText();
    }

    /** Runs both stages and returns the structured outcome (never throws). */
    public static FopResult generateResult(File xmlFile, File xslFile, File pdfOutput, PdfOptions options) {
        PdfOptions opts = options == null ? PdfOptions.none() : options;
        FOPService service = new FOPService();
        XsltTransformationResult fo;
        try {
            fo = service.transformToFo(xmlFile, xslFile, opts.stylesheetParameters());
        } catch (Exception e) {
            return new FopResult(false, null, null, List.of(), List.of(), e.getMessage(),
                    STAGE_TRANSFORM, QueryFailure.of(e));
        }
        if (!fo.isSuccess()) {
            return new FopResult(false, null, null, fo.getMessages(), List.of(),
                    "Failed to transform XML using XSL stylesheet: " + fo.getErrorMessage(),
                    STAGE_TRANSFORM, QueryFailure.ofCode(fo.getErrorCode()));
        }
        return render(service, fo.getOutputContent(), fo.getMessages(), pdfOutput, opts,
                new RenderContext(xmlFile.toPath(), xslFile.toPath(), null));
    }

    /** Renders an already produced XSL-FO document (e.g. the result of a debug run) to PDF. */
    public static FopResult renderFo(String foXml, File pdfOutput, PdfOptions options) {
        return renderFo(foXml, pdfOutput, options, RenderContext.none());
    }

    /**
     * Renders an already produced XSL-FO document to PDF.
     *
     * @param context where the FO came from (base folder and placeholders of an external processor)
     */
    public static FopResult renderFo(String foXml, File pdfOutput, PdfOptions options, RenderContext context) {
        return render(new FOPService(), foXml, List.of(), pdfOutput, options == null ? PdfOptions.none() : options,
                context == null ? RenderContext.none() : context);
    }

    private static FopResult render(FOPService service, String foXml, List<TransformationMessage> messages,
                                    File pdfOutput, PdfOptions opts, RenderContext context) {
        Optional<FoProcessorConfig> external;
        try {
            external = isBuiltin(opts.processor()) ? Optional.empty() : processorLookup.apply(opts.processor());
        } catch (RuntimeException e) {
            return new FopResult(false, null, foXml, messages, List.of(), e.getMessage(),
                    STAGE_RENDER, QueryFailure.of(e));
        }
        if (external.isPresent()) {
            return renderExternal(external.get(), foXml, messages, pdfOutput, opts, context);
        }
        try {
            List<FopEvent> events = service.renderFo(foXml, pdfOutput, opts.toSettings(),
                    opts.subject(), opts.pdfACompliant(), null);
            return new FopResult(true, pdfOutput, foXml, messages, events, null, STAGE_RENDER, null);
        } catch (Exception e) {
            return new FopResult(false, null, foXml, messages, List.of(), e.getMessage(),
                    STAGE_RENDER, QueryFailure.of(e));
        }
    }

    private static FopResult renderExternal(FoProcessorConfig processor, String foXml,
                                            List<TransformationMessage> messages, File pdfOutput,
                                            PdfOptions opts, RenderContext context) {
        ExternalFoRenderer.Request request = new ExternalFoRenderer.Request(foXml, context.foFile(),
                pdfOutput.toPath(), context.xml(), context.xsl(), nullToEmpty(opts.title()),
                nullToEmpty(opts.author()), nullToEmpty(opts.subject()), opts.pdfACompliant());
        try {
            List<FopEvent> events = ExternalFoRenderer.render(processor, request);
            return new FopResult(true, pdfOutput, foXml, messages, events, null, STAGE_RENDER, null);
        } catch (FoProcessorException e) {
            return new FopResult(false, null, foXml, messages, e.events(), e.getMessage(),
                    STAGE_RENDER, QueryFailure.of(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FopResult(false, null, foXml, messages, List.of(), processor.name() + " was cancelled.",
                    STAGE_RENDER, QueryFailure.of(e));
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
