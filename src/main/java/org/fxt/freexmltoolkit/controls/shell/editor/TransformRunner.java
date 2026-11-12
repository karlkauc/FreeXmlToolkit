package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.telemetry.QueryFailure;

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
     * Runs a stylesheet that may come from a model or an external agent in the sandbox
     * ({@link XsltTransformationEngine#transformSandboxed}): no file is written ({@code xsl:result-document}
     * output is returned), no Java extensions, no network, reads only inside {@code readableRoots}, no
     * environment variables, and the run stops at {@code timeout}.
     */
    public static XsltTransformationEngine.SandboxedTransformation xsltTransformSandboxed(String xml,
            String xmlBaseUri, String xsltContent, String xsltSystemId, java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat, java.time.Duration timeout,
            java.util.List<java.nio.file.Path> readableRoots) {
        return XsltTransformationEngine.getInstance().transformSandboxed(xml, xmlBaseUri, xsltContent, xsltSystemId,
                parameters, outputFormat, timeout, readableRoots);
    }

    /**
     * As {@link #xsltTransformSandboxed(String, String, String, String, java.util.Map,
     * XsltTransformationEngine.OutputFormat, java.time.Duration, java.util.List)} in an XSLT version: 2.0 is checked
     * by {@link org.fxt.freexmltoolkit.service.standards.XsltCompatibilityCheck} first (issues are a compile error,
     * code {@code FXT-COMPAT}); 1.0 runs on the JDK's XSLT 1.0 processor with secure processing, reads only inside
     * {@code readableRoots} and has no {@code xsl:result-document}. {@code null} = 3.0.
     */
    public static XsltTransformationEngine.SandboxedTransformation xsltTransformSandboxed(String xml,
            String xmlBaseUri, String xsltContent, String xsltSystemId, java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat, java.time.Duration timeout,
            java.util.List<java.nio.file.Path> readableRoots,
            org.fxt.freexmltoolkit.service.standards.XsltVersion version) {
        XsltTransformationResult incompatible = xsltIncompatibility(xsltContent, xsltSystemId, version);
        if (incompatible != null) {
            return new XsltTransformationEngine.SandboxedTransformation(incompatible, java.util.Map.of(), false);
        }
        if (version == org.fxt.freexmltoolkit.service.standards.XsltVersion.V1_0) {
            java.util.List<java.nio.file.Path> roots;
            try {
                roots = org.fxt.freexmltoolkit.util.RootedPaths.toRealPaths(readableRoots);
            } catch (java.io.IOException e) {
                return new XsltTransformationEngine.SandboxedTransformation(XsltTransformationResult.error(
                        "A readable folder does not exist: " + e.getMessage()), java.util.Map.of(), false);
            }
            return new XsltTransformationEngine.SandboxedTransformation(
                    org.fxt.freexmltoolkit.service.standards.Xslt10Engine.transform(xml, xmlBaseUri, xsltContent,
                            xsltSystemId, parameters, outputFormat, roots), java.util.Map.of(), false);
        }
        return xsltTransformSandboxed(xml, xmlBaseUri, xsltContent, xsltSystemId, parameters, outputFormat, timeout,
                readableRoots);
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
     * Transforms {@code xml} with {@code xsltContent} and returns the full
     * {@link XsltTransformationResult} (output, phase timings, profile) instead of just
     * the output string — used when the caller records execution statistics.
     *
     * @return the result; check {@link XsltTransformationResult#isSuccess()}
     */
    public static XsltTransformationResult xsltTransformResult(String xml, String xsltContent,
            java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat) {
        return xsltTransformResult(xml, null, xsltContent, null, parameters, outputFormat);
    }

    /**
     * As {@link #xsltTransformResult(String, String, java.util.Map, XsltTransformationEngine.OutputFormat)}
     * for a stylesheet and input that come from files: with {@code xsltSystemId} (the
     * stylesheet's {@code file:} URI) relative {@code xsl:import}/{@code xsl:include}/{@code document()}
     * hrefs resolve against the stylesheet's folder, with {@code xmlBaseUri} relative URIs in the
     * input resolve against the input's folder. Either may be {@code null} (in-memory text).
     */
    public static XsltTransformationResult xsltTransformResult(String xml, String xmlBaseUri,
            String xsltContent, String xsltSystemId,
            java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat) {
        try {
            return XsltTransformationEngine.getInstance()
                    .transform(xml, xsltContent, xsltSystemId, xmlBaseUri, parameters, outputFormat);
        } catch (Exception e) {
            return XsltTransformationResult.error(e.getMessage(), e);
        }
    }

    /**
     * As {@link #xsltTransformResult(String, String, String, String, java.util.Map,
     * XsltTransformationEngine.OutputFormat)} in an XSLT version: 3.0 ({@code null}) on Saxon as before;
     * 2.0 on Saxon after {@link org.fxt.freexmltoolkit.service.standards.XsltCompatibilityCheck} found no XSLT 3.0 /
     * XPath 3.x construct (else a compile error listing them); 1.0 on the JDK's XSLT 1.0 processor.
     */
    public static XsltTransformationResult xsltTransformResult(String xml, String xmlBaseUri,
            String xsltContent, String xsltSystemId,
            java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat,
            org.fxt.freexmltoolkit.service.standards.XsltVersion version) {
        if (version == org.fxt.freexmltoolkit.service.standards.XsltVersion.V1_0) {
            return org.fxt.freexmltoolkit.service.standards.Xslt10Engine.transform(xml, xmlBaseUri, xsltContent,
                    xsltSystemId, parameters, outputFormat, null);
        }
        XsltTransformationResult incompatible = xsltIncompatibility(xsltContent, xsltSystemId, version);
        if (incompatible != null) {
            return incompatible;
        }
        return xsltTransformResult(xml, xmlBaseUri, xsltContent, xsltSystemId, parameters, outputFormat);
    }

    /**
     * The XSLT 2.0 compatibility report as a compile-error result ({@code FXT-COMPAT}, the first issue's line), or
     * {@code null} when the stylesheet fits {@code version} (always for 3.0, 1.0 and {@code null}).
     */
    public static XsltTransformationResult xsltIncompatibility(String xsltContent, String xsltSystemId,
            org.fxt.freexmltoolkit.service.standards.XsltVersion version) {
        if (version != org.fxt.freexmltoolkit.service.standards.XsltVersion.V2_0) {
            return null;
        }
        var issues = org.fxt.freexmltoolkit.service.standards.XsltCompatibilityCheck.check(xsltContent,
                xsltSystemId, version);
        if (issues.isEmpty()) {
            return null;
        }
        var first = issues.stream().filter(i -> i.module() == null).findFirst().orElse(issues.getFirst());
        return XsltTransformationResult.error(
                org.fxt.freexmltoolkit.service.standards.XsltCompatibilityCheck.format(issues), "FXT-COMPAT",
                XsltTransformationResult.PHASE_COMPILE, first.module() == null ? first.line() : -1);
    }

    /**
     * Classifies a transformation result for telemetry: the processor's error code when there is
     * one, otherwise the exception behind the failure (class + stack signature).
     *
     * @return {@code null} for a successful result
     */
    public static QueryFailure failureOf(XsltTransformationResult result) {
        if (result == null) {
            return QueryFailure.UNKNOWN;
        }
        if (result.isSuccess()) {
            return null;
        }
        if (result.getErrorCode() != null) {
            return QueryFailure.ofCode(result.getErrorCode());
        }
        return result.getCause() != null ? QueryFailure.of(result.getCause()) : QueryFailure.UNKNOWN;
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
        return xquery(xml, xqueryContent, externalVariables, outputFormat).text();
    }

    /**
     * A query result as shown to the user plus, for a failed run, its telemetry classification.
     *
     * @param text    the output, or {@code "ERROR: …"}
     * @param failure why the run failed, or {@code null} when it succeeded
     */
    public record QueryRun(String text, QueryFailure failure) {
    }

    /** As {@link #runXQuery}, additionally reporting why a failed run failed. */
    public static QueryRun xquery(String xml, String xqueryContent,
                                  java.util.Map<String, Object> externalVariables,
                                  XsltTransformationEngine.OutputFormat outputFormat) {
        return xquery(xml, xqueryContent, externalVariables, outputFormat, null);
    }

    /**
     * As {@link #xquery(String, String, java.util.Map, XsltTransformationEngine.OutputFormat)} for an XQuery
     * version: below 3.1 the query is first checked by {@link org.fxt.freexmltoolkit.service.standards.XQueryCompatibilityCheck}; issues fail the run
     * (code {@code FXT-COMPAT}) before anything is evaluated.
     */
    public static QueryRun xquery(String xml, String xqueryContent,
                                  java.util.Map<String, Object> externalVariables,
                                  XsltTransformationEngine.OutputFormat outputFormat,
                                  org.fxt.freexmltoolkit.service.standards.XQueryVersion version) {
        String incompatible = xqueryIncompatibility(xqueryContent, version);
        if (incompatible != null) {
            return new QueryRun("ERROR: " + incompatible, QueryFailure.invalidInput("compat.xquery"));
        }
        try {
            XsltTransformationResult result = XsltTransformationEngine.getInstance()
                    .transformXQuery(xml, xqueryContent, externalVariables, outputFormat);
            return result.isSuccess()
                    ? new QueryRun(result.getOutputContent(), null)
                    : new QueryRun("ERROR: " + result.getErrorMessage(), QueryFailure.ofCode(result.getErrorCode()));
        } catch (Exception e) {
            return new QueryRun("ERROR: " + e.getMessage(), QueryFailure.of(e));
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
        return transformForReport(xml, null, xsltContent, null, parameters, outputFormat);
    }

    /**
     * As {@link #transformForReport(String, String, java.util.Map, XsltTransformationEngine.OutputFormat)}
     * with the input's and the stylesheet's locations (see
     * {@link #xsltTransformResult(String, String, String, String, java.util.Map, XsltTransformationEngine.OutputFormat)}).
     */
    public static XsltTransformationResult transformForReport(String xml, String xmlBaseUri,
            String xsltContent, String xsltSystemId,
            java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat) {
        try {
            org.fxt.freexmltoolkit.debugger.DebugSession session =
                    new org.fxt.freexmltoolkit.debugger.DebugSession();
            return XsltTransformationEngine.getInstance()
                    .transformWithDebugSession(xml, xmlBaseUri, xsltContent, xsltSystemId, parameters,
                            outputFormat, session, null);
        } catch (Exception e) {
            return XsltTransformationResult.error(e.getMessage(), e);
        }
    }

    /**
     * Runs the transform with the XSLT profiler ({@link XsltTransformationEngine#profileTransformation}).
     *
     * @param warmupRuns runs before the measured one
     */
    public static XsltTransformationEngine.ProfiledTransformation profile(String xml, String xmlBaseUri,
            String xsltContent, String xsltSystemId, java.util.Map<String, Object> parameters,
            XsltTransformationEngine.OutputFormat outputFormat, int warmupRuns) {
        try {
            return XsltTransformationEngine.getInstance().profileTransformation(xml, xmlBaseUri, xsltContent,
                    xsltSystemId, parameters, outputFormat, warmupRuns, null);
        } catch (Exception e) {
            return new XsltTransformationEngine.ProfiledTransformation(XsltTransformationResult.error(e.getMessage(), e),
                    null);
        }
    }

    /**
     * Runs an XQuery with the profiler ({@link XsltTransformationEngine#profileXQuery}).
     *
     * @param xml           the context document, or {@code null} / blank for none
     * @param querySystemId the query file's URI (module imports, profile locations), or {@code null}
     * @param warmupRuns    runs before the measured one
     */
    public static XsltTransformationEngine.ProfiledTransformation profileXQuery(String xml, String xmlBaseUri,
            String query, String querySystemId, java.util.Map<String, Object> externalVariables,
            XsltTransformationEngine.OutputFormat outputFormat, int warmupRuns) {
        try {
            return XsltTransformationEngine.getInstance().profileXQuery(xml, xmlBaseUri, query, querySystemId,
                    externalVariables, outputFormat, warmupRuns);
        } catch (Exception e) {
            return new XsltTransformationEngine.ProfiledTransformation(XsltTransformationResult.error(e.getMessage(), e),
                    null);
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
        return xpath(xml, xpath).text();
    }

    /** As {@link #runXPath}, additionally reporting why a failed run failed. */
    public static QueryRun xpath(String xml, String xpath) {
        return xpath(xml, xpath, org.fxt.freexmltoolkit.service.standards.XPathVersion.V3_1);
    }

    /** As {@link #xpath(String, String)} in an XPath version (1.0 runs on the JDK's XPath 1.0 engine). */
    public static QueryRun xpath(String xml, String xpath, org.fxt.freexmltoolkit.service.standards.XPathVersion version) {
        try {
            String result = ServiceRegistry.get(XmlService.class).getXmlFromXpath(xml, xpath,
                    version == null ? org.fxt.freexmltoolkit.service.standards.XPathVersion.V3_1 : version);
            return new QueryRun(result != null ? result : "", null);
        } catch (Throwable t) {
            return new QueryRun("ERROR: " + t.getMessage(), QueryFailure.of(t));
        }
    }

    /**
     * The compatibility report of an XQuery for {@code version} as one message, or {@code null} when the query
     * fits (always for 3.1 / {@code null}).
     */
    public static String xqueryIncompatibility(String query,
                                               org.fxt.freexmltoolkit.service.standards.XQueryVersion version) {
        if (version == null || version == org.fxt.freexmltoolkit.service.standards.XQueryVersion.V3_1) {
            return null;
        }
        var issues = org.fxt.freexmltoolkit.service.standards.XQueryCompatibilityCheck.check(query, version);
        return issues.isEmpty() ? null
                : org.fxt.freexmltoolkit.service.standards.XQueryCompatibilityCheck.format(issues, version.label());
    }

    /** Evaluates a JSONPath expression against {@code json}; returns the result or an error message. */
    public static String runJsonPath(String json, String jsonPath) {
        return jsonPath(json, jsonPath).text();
    }

    /** As {@link #runJsonPath}, additionally reporting why a failed run failed. */
    public static QueryRun jsonPath(String json, String jsonPath) {
        try {
            String result = new org.fxt.freexmltoolkit.service.JsonService().executeJsonPathAsString(json, jsonPath);
            // The service reports a broken path or document as text instead of throwing.
            return new QueryRun(result, result != null && result.startsWith("JSONPath error:")
                    ? QueryFailure.invalidInput("jsonpath.error") : null);
        } catch (Throwable t) {
            return new QueryRun("ERROR: " + t.getMessage(), QueryFailure.of(t));
        }
    }
}
