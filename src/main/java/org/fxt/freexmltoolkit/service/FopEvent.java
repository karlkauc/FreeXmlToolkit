package org.fxt.freexmltoolkit.service;

/**
 * One event Apache FOP raised while rendering (missing font substituted, content overflow,
 * unknown property, …). Collected per render so the PDF/FOP panel can show why a PDF does
 * not look as expected instead of swallowing the warnings in the log.
 *
 * @param severity {@code INFO}, {@code WARN}, {@code ERROR} or {@code FATAL}
 * @param id       FOP's event id (e.g. {@code org.apache.fop.fonts.FontEventListener.fontSubstituted})
 * @param message  the localised event text
 */
public record FopEvent(String severity, String id, String message) {

    public FopEvent {
        if (severity == null) severity = "INFO";
        if (id == null) id = "";
        if (message == null) message = "";
    }

    /** @return {@code true} for WARN and above. */
    public boolean isWarningOrWorse() {
        return !"INFO".equals(severity);
    }

    /** @return the last dotted segment of the id, e.g. {@code fontSubstituted}. */
    public String shortId() {
        int dot = id.lastIndexOf('.');
        return dot >= 0 ? id.substring(dot + 1) : id;
    }
}
