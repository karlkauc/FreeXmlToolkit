package org.fxt.freexmltoolkit.service.fo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Starting points for an external XSL-FO processor: a display name, the program names looked up
 * on the {@code PATH} and an argument template (see {@link ArgumentTemplate}). They only fill the
 * dialog; the user can change everything afterwards.
 */
public enum FoProcessorPreset {

    /** Apache FOP installed separately (another version than the bundled one, own fonts / config). */
    APACHE_FOP("Apache FOP (external)", List.of("fop"),
            "-fo ${fo} -pdf ${pdf} [-pdfprofile PDF/A-1b ${pdfa}]",
            // FOP logs through java.util.logging: a timestamp/class line precedes each message
            Pattern.compile("^(\\w{3} \\d{1,2}, \\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\.\\d{1,2}\\.\\d{4}).*")),

    /** Antenna House Formatter's command line program. */
    ANTENNA_HOUSE("Antenna House Formatter", List.of("AHFCmd", "run.sh"),
            "-d ${fo} -o ${pdf}", null),

    /** RenderX XEP. */
    XEP("RenderX XEP", List.of("xep"),
            "-fo ${fo} -pdf ${pdf}", null),

    /** Any other program or wrapper script. */
    CUSTOM("Custom", List.of(),
            "${fo} ${pdf}", null);

    private final String displayName;
    private final List<String> programNames;
    private final String arguments;
    private final Pattern ignoredLines;

    FoProcessorPreset(String displayName, List<String> programNames, String arguments, Pattern ignoredLines) {
        this.displayName = displayName;
        this.programNames = programNames;
        this.arguments = arguments;
        this.ignoredLines = ignoredLines;
    }

    /** @return the name shown in the dialog and used as the default processor name */
    public String displayName() {
        return displayName;
    }

    /** @return program names looked up on the PATH, in order of preference */
    public List<String> programNames() {
        return programNames;
    }

    /** @return the argument template this preset starts with */
    public String arguments() {
        return arguments;
    }

    /** @return output lines that carry no message (log headers), or {@code null} */
    Pattern ignoredLines() {
        return ignoredLines;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** @return the preset of that name, {@link #CUSTOM} for an unknown or missing one */
    public static FoProcessorPreset of(String name) {
        if (name != null) {
            for (FoProcessorPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(name.strip())) {
                    return preset;
                }
            }
        }
        return CUSTOM;
    }
}
