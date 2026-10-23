package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.FOPService;
import org.fxt.freexmltoolkit.service.FopEvent;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * UI-free PDF generation for the PDF/FOP activity: transforms an XML file with
 * an XSL(-FO) stylesheet to PDF via the reused {@link FOPService}, in two stages
 * (XSLT → XSL-FO → PDF) so the intermediate FO, the stylesheet's {@code xsl:message}
 * output and FOP's rendering events are available to the panel. The string
 * variants return {@code "OK: …"} / {@code "ERROR: …"} rather than throwing.
 */
public final class FopRunner {

    private FopRunner() {
    }

    /**
     * The panel's generation options (Figma mockup node 49:2): PDF metadata
     * (title/author/subject), PDF/A-1b conformance, and the page size/orientation,
     * which are passed to the stylesheet as the XSLT parameters {@code page-size}
     * and {@code page-orientation} (honoured by stylesheets that declare them).
     */
    public record PdfOptions(String title, String author, String subject,
                             boolean pdfACompliant, String pageSize, String orientation) {

        public static PdfOptions none() {
            return new PdfOptions("", "", "", false, "", "");
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
     * the error text for a failed run.
     */
    public record FopResult(boolean ok, File pdf, String foXml, List<TransformationMessage> messages,
                            List<FopEvent> events, String error) {

        public FopResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            events = events == null ? List.of() : List.copyOf(events);
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
                sb.append("FOP events (").append(events.size()).append(")\n");
                for (FopEvent e : events) {
                    sb.append("  ").append(e.severity()).append(" [").append(e.shortId()).append("]: ")
                            .append(e.message()).append('\n');
                }
            }
            if (sb.length() == 0) {
                sb.append("No messages: the stylesheet produced no xsl:message output and FOP raised no events.\n");
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
            return new FopResult(false, null, null, List.of(), List.of(), e.getMessage());
        }
        if (!fo.isSuccess()) {
            return new FopResult(false, null, null, fo.getMessages(), List.of(),
                    "Failed to transform XML using XSL stylesheet: " + fo.getErrorMessage());
        }
        return render(service, fo.getOutputContent(), fo.getMessages(), pdfOutput, opts);
    }

    /** Renders an already produced XSL-FO document (e.g. the result of a debug run) to PDF. */
    public static FopResult renderFo(String foXml, File pdfOutput, PdfOptions options) {
        return render(new FOPService(), foXml, List.of(), pdfOutput, options == null ? PdfOptions.none() : options);
    }

    private static FopResult render(FOPService service, String foXml, List<TransformationMessage> messages,
                                    File pdfOutput, PdfOptions opts) {
        try {
            List<FopEvent> events = service.renderFo(foXml, pdfOutput, opts.toSettings(),
                    opts.subject(), opts.pdfACompliant(), null);
            return new FopResult(true, pdfOutput, foXml, messages, events, null);
        } catch (Exception e) {
            return new FopResult(false, null, foXml, messages, List.of(), e.getMessage());
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
