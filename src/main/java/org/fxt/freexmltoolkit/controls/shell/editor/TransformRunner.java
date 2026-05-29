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

    /** Transforms {@code xml} with {@code xsltContent}; returns output or an error message. */
    public static String xsltTransform(String xml, String xsltContent) {
        try {
            XsltTransformationResult result =
                    XsltTransformationEngine.getInstance().quickTransform(xml, xsltContent);
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
}
