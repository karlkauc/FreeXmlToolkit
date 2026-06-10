package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * UI-free transform/query orchestration for the Transform activity: runs an XSLT
 * transformation (reusing {@link XsltTransformationEngine}) or an XPath query
 * (reusing {@link XmlService}) and returns the result as a string. Errors are
 * returned as {@code "ERROR: …"} text rather than thrown, so the panel can show
 * them directly.
 */
public final class TransformRunner {

    private TransformRunner() {
    }

    /** Transforms {@code xml} with {@code xsltContent} (no parameters, XML output). */
    public static String xsltTransform(String xml, String xsltContent) {
        try {
            XsltTransformationResult result =
                    XsltTransformationEngine.getInstance().quickTransform(xml, xsltContent);
            return result.isSuccess() ? result.getOutputContent() : "ERROR: " + result.getErrorMessage();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Transforms {@code xml} with {@code xsltContent}, passing the given stylesheet
     * parameters and producing the chosen output format.
     *
     * @return the transformation output, or {@code "ERROR: …"}
     */
    public static String xsltTransform(String xml, String xsltContent,
                                       java.util.Map<String, Object> parameters,
                                       XsltTransformationEngine.OutputFormat outputFormat) {
        try {
            XsltTransformationResult result = XsltTransformationEngine.getInstance()
                    .transform(xml, xsltContent, parameters, outputFormat);
            return result.isSuccess() ? result.getOutputContent() : "ERROR: " + result.getErrorMessage();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Executes an XQuery against {@code xml} with the given external variables and
     * output format (reuses {@link XsltTransformationEngine#transformXQuery}).
     *
     * @return the query output, or {@code "ERROR: …"}
     */
    public static String runXQuery(String xml, String xqueryContent,
                                   java.util.Map<String, Object> externalVariables,
                                   XsltTransformationEngine.OutputFormat outputFormat) {
        try {
            XsltTransformationResult result = XsltTransformationEngine.getInstance()
                    .transformXQuery(xml, xqueryContent, externalVariables, outputFormat);
            return result.isSuccess() ? result.getOutputContent() : "ERROR: " + result.getErrorMessage();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Runs the transform to completion with tracing + profiling enabled and returns the full
     * {@link XsltTransformationResult} (profile, template matches with per-template timings,
     * call stack, messages). Uses a breakpoint-free debug session so it never pauses.
     *
     * @return the result; check {@link XsltTransformationResult#isSuccess()}
     */
    public static XsltTransformationResult transformForReport(String xml, String xsltContent,
            java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat) {
        try {
            org.fxt.freexmltoolkit.debugger.DebugSession session =
                    new org.fxt.freexmltoolkit.debugger.DebugSession();
            return XsltTransformationEngine.getInstance()
                    .transformWithDebugSession(xml, xsltContent, parameters, outputFormat, session);
        } catch (Exception e) {
            return XsltTransformationResult.error(e.getMessage());
        }
    }

    /**
     * Pattern for the {@code method} attribute of a stylesheet's {@code xsl:output}
     * declaration (any namespace prefix).
     */
    private static final java.util.regex.Pattern XSL_OUTPUT_METHOD = java.util.regex.Pattern.compile(
            "<\\w+:output\\b[^>]*\\bmethod\\s*=\\s*[\"'](?:\\w+:)?(\\w+)[\"']",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Detects the output format a stylesheet produces: the {@code method} of its
     * {@code xsl:output} declaration if present; otherwise the XSLT default rule
     * (a literal {@code <html>} result element implies {@code html}, else {@code xml}).
     */
    public static XsltTransformationEngine.OutputFormat detectXsltOutputFormat(String xsltContent) {
        if (xsltContent == null) {
            return XsltTransformationEngine.OutputFormat.XML;
        }
        java.util.regex.Matcher matcher = XSL_OUTPUT_METHOD.matcher(xsltContent);
        if (matcher.find()) {
            return switch (matcher.group(1).toLowerCase()) {
                case "html" -> XsltTransformationEngine.OutputFormat.HTML;
                case "xhtml" -> XsltTransformationEngine.OutputFormat.XHTML;
                case "text" -> XsltTransformationEngine.OutputFormat.TEXT;
                case "json" -> XsltTransformationEngine.OutputFormat.JSON;
                default -> XsltTransformationEngine.OutputFormat.XML;
            };
        }
        return xsltContent.matches("(?is).*<html[\\s>/].*")
                ? XsltTransformationEngine.OutputFormat.HTML
                : XsltTransformationEngine.OutputFormat.XML;
    }

    /** Evaluates an XPath expression against {@code xml}; returns the result or an error message. */
    public static String runXPath(String xml, String xpath) {
        try {
            String result = ServiceRegistry.get(XmlService.class).getXmlFromXpath(xml, xpath);
            return result != null ? result : "";
        } catch (Throwable t) {
            return "ERROR: " + t.getMessage();
        }
    }

    /** Evaluates a JSONPath expression against {@code json}; returns the result or an error message. */
    public static String runJsonPath(String json, String jsonPath) {
        try {
            return new org.fxt.freexmltoolkit.service.JsonService().executeJsonPathAsString(json, jsonPath);
        } catch (Throwable t) {
            return "ERROR: " + t.getMessage();
        }
    }
}
