package org.fxt.freexmltoolkit.service.ai.agent;

/** One check of a candidate: what was checked, whether it passed, details for the user and the model. */
public record Check(String label, boolean passed, String detail) {

    /** A check that is reported but does not decide the outcome (e.g. differences to a sample). */
    public static Check info(String label, String detail) {
        return new Check(label, true, detail);
    }
}
