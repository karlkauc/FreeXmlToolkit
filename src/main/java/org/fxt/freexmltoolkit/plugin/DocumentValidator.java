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
     * @param severity     {@code error}, {@code warning} or {@code info} (a hint)
     * @param line         1-based, 0 when unknown
     * @param rule         the rule's code, or {@code null}
     * @param category     a {@code ProblemCategory} name, or {@code null} (derived)
     * @param subjectKind  what the problem is about: {@code ISIN}, {@code LEI}, {@code ASSET}, {@code RECORD}, or
     *                     {@code null}
     * @param subjectKey   its identifier, or {@code null}
     * @param subjectLabel its readable name, or {@code null}
     */
    record Problem(String severity, int line, String message, String rule, String category, String subjectKind,
                   String subjectKey, String subjectLabel) {

        /** A problem without rule, category or subject. */
        public Problem(String severity, int line, String message) {
            this(severity, line, message, null, null, null, null, null);
        }

        /**
         * @return a copy with the rule that failed (a code shown and grouped by in the PROBLEMS panel)
         */
        public Problem withRule(String ruleCode) {
            return new Problem(severity, line, message, ruleCode, category, subjectKind, subjectKey, subjectLabel);
        }

        /**
         * @param categoryKey a {@code ProblemCategory} name ({@code FMA_ASSETS}, {@code PLUGIN_RULE} …); unknown names
         *                    are derived like problems without one
         */
        public Problem withCategory(String categoryKey) {
            return new Problem(severity, line, message, rule, categoryKey, subjectKind, subjectKey, subjectLabel);
        }

        /**
         * @param kind  {@code ISIN}, {@code LEI}, {@code ASSET} (an id that is neither: decided by the key's form when
         *              {@code ASSET} is given) or {@code RECORD}
         * @param key   the identifier the problem is about (grouping, collective fixes)
         * @param label a readable name, or {@code null}
         */
        public Problem withSubject(String kind, String key, String label) {
            return new Problem(severity, line, message, rule, category, kind, key, label);
        }
    }
}
