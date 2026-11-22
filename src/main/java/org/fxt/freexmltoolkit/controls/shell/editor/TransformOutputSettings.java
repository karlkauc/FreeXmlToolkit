package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;

/**
 * How the shell serializes transform and query results where the stylesheet or query does not say: indented or not,
 * with or without an XML declaration (Transform panel → Output). The command line keeps the engine's defaults.
 */
public final class TransformOutputSettings {

    /** Indent results (default on). */
    public static final String INDENT_KEY = "transform.output.indent";
    /** Leave out the XML declaration (default off). */
    public static final String OMIT_DECLARATION_KEY = "transform.output.omitDeclaration";

    private TransformOutputSettings() {
    }

    /** @return whether results are indented where the stylesheet does not say */
    public static boolean indent() {
        return !"false".equalsIgnoreCase(get(INDENT_KEY));
    }

    /** @return whether the XML declaration is left out where the stylesheet does not say */
    public static boolean omitDeclaration() {
        return "true".equalsIgnoreCase(get(OMIT_DECLARATION_KEY));
    }

    /** Stores both choices and hands them to the engine. */
    public static void set(boolean indent, boolean omitDeclaration) {
        put(INDENT_KEY, String.valueOf(indent));
        put(OMIT_DECLARATION_KEY, String.valueOf(omitDeclaration));
        apply();
    }

    /** Hands the stored choices to the engine (before a run from the shell). */
    public static void apply() {
        XsltTransformationEngine.getInstance()
                .setOutputOptions(new XsltTransformationEngine.OutputOptions(indent(), omitDeclaration()));
    }

    /** @return whether an administrator locked {@code key} */
    static boolean locked(String key) {
        try {
            return AdminPolicy.current().isLocked(key);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String get(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void put(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (RuntimeException e) {
            // not stored: the choice holds until the next start
        }
    }
}
