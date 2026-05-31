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
