package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.SchematronService;
import org.fxt.freexmltoolkit.service.SchematronServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * UI-free validation orchestration for the Validation activity: validates XML
 * against an XSD (Xerces via {@link XmlService}) and/or a Schematron file
 * ({@link SchematronService}), returning a unified {@link ValidationProblem} list.
 * Both stages degrade gracefully on errors so one failing stage never hides the
 * other.
 */
public final class ValidationRunner {

    private ValidationRunner() {
    }

    /**
     * @param xml        the XML content
     * @param xsd        the XSD to validate against, or {@code null} (well-formedness only)
     * @param schematron the Schematron file, or {@code null} to skip Schematron
     * @return all problems found (empty if valid)
     */
    public static List<ValidationProblem> run(String xml, File xsd, File schematron) {
        List<ValidationProblem> problems = new ArrayList<>();
        try {
            for (SAXParseException e : ServiceRegistry.get(XmlService.class).validateText(xml, xsd)) {
                problems.add(new ValidationProblem("XSD", "error", e.getLineNumber(), e.getMessage()));
            }
        } catch (Throwable ignored) {
            // XSD validation unavailable (e.g. no service registry) — skip
        }
        if (schematron != null) {
            try {
                SchematronService service = new SchematronServiceImpl();
                for (SchematronService.SchematronValidationError e : service.validateXml(xml, schematron)) {
                    problems.add(new ValidationProblem("Schematron",
                            e.severity() != null ? e.severity() : "error", e.lineNumber(), e.message()));
                }
            } catch (Exception ignored) {
                // invalid/unloadable schematron — skip its stage
            }
        }
        return problems;
    }
}
