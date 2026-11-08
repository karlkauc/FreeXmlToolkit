package org.fxt.freexmltoolkit.plugin;

import java.util.List;

/**
 * Additional checks a plugin runs on XML documents after XSD and Schematron validation — in the
 * Validation panel, with F5 and in {@code fxt-cli validate}. Runs on a background thread.
 */
public interface DocumentValidator {

    /** @return the source column of its problems ({@code FundsXML}, {@code ISO 20022 rules}) */
    String name();

    /**
     * @param documentName the file name, or {@code null} for an unsaved document
     * @return whether the validator checks this document (cheap: look at the root element, a namespace)
     */
    boolean accepts(String content, String documentName);

    /** @return the problems found; an empty list when the document passes */
    List<Problem> validate(String content, String documentName);

    /**
     * A problem.
     *
     * @param severity {@code error}, {@code warning} or {@code info}
     * @param line     1-based, 0 when unknown
     */
    record Problem(String severity, int line, String message) {
    }
}
