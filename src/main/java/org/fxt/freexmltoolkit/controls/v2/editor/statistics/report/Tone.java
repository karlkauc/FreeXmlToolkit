package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

/** Visual emphasis of a report row or fact — writers map it to a colour where the format allows. */
public enum Tone {
    NONE("", ""),
    ERROR("#dc3545", "#fdecea"),
    WARNING("#fd7e14", "#fff3e0"),
    INFO("#17a2b8", "#e6f6f9"),
    SUGGESTION("#b08900", "#fff8e1"),
    OK("#28a745", "#e8f5e9");

    private final String foreground;
    private final String background;

    Tone(String foreground, String background) {
        this.foreground = foreground;
        this.background = background;
    }

    /** @return the text colour as a CSS hex value, or {@code ""} for {@link #NONE}. */
    public String foreground() {
        return foreground;
    }

    /** @return a light fill colour as a CSS hex value, or {@code ""} for {@link #NONE}. */
    public String background() {
        return background;
    }
}
