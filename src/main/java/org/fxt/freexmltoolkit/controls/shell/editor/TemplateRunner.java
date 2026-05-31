package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.TemplateRepository;

/**
 * UI-free access to XML templates for the shell, reusing {@link TemplateRepository}
 * (storage) and {@link XmlTemplate#processTemplate(Map)} (parameter substitution).
 */
public final class TemplateRunner {

    private TemplateRunner() {
    }

    /** @return all available templates (built-in + user). */
    public static List<XmlTemplate> list() {
        return TemplateRepository.getInstance().getAllTemplates();
    }

    /**
     * Renders a template with the given parameter values (missing ones fall back to
     * the template's defaults).
     *
     * @return the processed content, or {@code "ERROR: …"} (e.g. a required parameter is missing)
     */
    public static String render(XmlTemplate template, Map<String, String> parameters) {
        try {
            return template.processTemplate(parameters == null ? Map.of() : parameters);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
