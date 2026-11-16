package org.fxt.freexmltoolkit.service.xsltproc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * Reads what an external XSLT processor printed: error blocks (with the first error's line, code and
 * phase) and the other lines as messages ({@code xsl:message} goes to standard error with Saxon, xsltproc
 * and RaptorXML). Knows the formats of Saxon ({@code Static error at … on line 3 column 20 of a.xsl:} with
 * the code on the next line), xsltproc ({@code a.xsl:3: parser error}, {@code runtime error: file a.xsl line 5})
 * and RaptorXML; anything else with "error" in front counts as an error too.
 */
public final class XsltOutputParser {

    /**
     * What the output said.
     *
     * @param messages     the lines that are no errors ({@code xsl:message}, warnings, information)
     * @param errorMessage the error blocks joined (at most {@link #MAX_ERROR_LINES} lines), or {@code null}
     * @param errorCode    the first error's code ({@code XTDE0045}), or {@code null}
     * @param errorPhase   {@link XsltTransformationResult#PHASE_COMPILE}, {@code PHASE_INPUT} or
     *                     {@code PHASE_RUNTIME}, or {@code null} when there is no error
     * @param errorLine    the first error's line, or -1
     */
    public record Parsed(List<TransformationMessage> messages, String errorMessage, String errorCode, String errorPhase,
                         int errorLine) {
    }

    static final int MAX_ERROR_LINES = 12;

    private static final Pattern ERROR_START = Pattern.compile(
            "(?i)^(?:\\S+:\\d+:\\s*)?(?:static |fatal |runtime |compilation |xpath |type |dynamic |xslt |parser |namespace )?"
                    + "error\\b.*|(?i)^\\S+:\\d+:\\s*(?:parser|namespace|validity) error\\b.*|(?i)^errors? (?:were|was) reported\\b.*");
    private static final Pattern WARNING_START = Pattern.compile("(?i)^(?:\\S+:\\d+:\\s*)?warning\\b.*");
    private static final Pattern ON_LINE = Pattern.compile("(?i)\\bline (\\d+)");
    private static final Pattern FILE_LINE = Pattern.compile("^(\\S+?):(\\d+):");
    private static final Pattern CODE = Pattern.compile("\\b([A-Z]{4}\\d{4})\\b");

    private XsltOutputParser() {
    }

    /**
     * @param lines   the program's output lines
     * @param xmlName the input file's name (to tell an input error from a stylesheet error), or {@code null}
     */
    public static Parsed parse(List<String> lines, String xmlName) {
        List<TransformationMessage> messages = new ArrayList<>();
        List<String> errorLines = new ArrayList<>();
        String firstError = null;
        String kind = null; // the block the previous line belongs to: "error", "warning" or null
        StringBuilder warning = null;
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                kind = null;
                continue;
            }
            boolean continuation = Character.isWhitespace(raw.charAt(0)) && kind != null;
            if (continuation && "error".equals(kind)) {
                errorLines.add(line.strip());
                continue;
            }
            if (continuation && warning != null) {
                warning.append(' ').append(line.strip());
                continue;
            }
            if (warning != null) {
                messages.add(new TransformationMessage("WARNING", warning.toString(), -1, null));
                warning = null;
            }
            if (ERROR_START.matcher(line).matches()) {
                kind = "error";
                if (firstError == null) {
                    firstError = line;
                }
                errorLines.add(line.strip());
            } else if (WARNING_START.matcher(line).matches()) {
                kind = "warning";
                warning = new StringBuilder(line.strip());
            } else {
                kind = null;
                messages.add(new TransformationMessage("INFO", line, -1, null));
            }
        }
        if (warning != null) {
            messages.add(new TransformationMessage("WARNING", warning.toString(), -1, null));
        }
        if (firstError == null) {
            return new Parsed(List.copyOf(messages), null, null, null, -1);
        }
        String all = String.join("\n", errorLines.subList(0, Math.min(errorLines.size(), MAX_ERROR_LINES)));
        return new Parsed(List.copyOf(messages), all, code(errorLines), phase(all, xmlName), line(errorLines));
    }

    /** @return the code of the first error block (Saxon puts it on the line after the location) */
    private static String code(List<String> errorLines) {
        for (String line : errorLines) {
            Matcher m = CODE.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static int line(List<String> errorLines) {
        for (String line : errorLines) {
            Matcher m = ON_LINE.matcher(line);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            Matcher f = FILE_LINE.matcher(line);
            if (f.find()) {
                return Integer.parseInt(f.group(2));
            }
        }
        return -1;
    }

    private static String phase(String text, String xmlName) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("sxxp0003") || lower.contains("xml parser")
                || (xmlName != null && !xmlName.isEmpty() && lower.contains(xmlName.toLowerCase(java.util.Locale.ROOT))
                && lower.contains("parser error"))) {
            return XsltTransformationResult.PHASE_INPUT;
        }
        if (lower.contains("static error") || lower.contains("compilation") || lower.contains("parser error")
                || lower.contains("xtse")) {
            return XsltTransformationResult.PHASE_COMPILE;
        }
        return XsltTransformationResult.PHASE_RUNTIME;
    }
}
