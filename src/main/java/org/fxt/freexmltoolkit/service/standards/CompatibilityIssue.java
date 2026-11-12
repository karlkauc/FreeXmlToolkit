package org.fxt.freexmltoolkit.service.standards;

/**
 * A construct a stylesheet or query uses that its chosen (older) language version does not have.
 *
 * @param line    1-based line, 0 when unknown
 * @param column  1-based column, 0 when unknown
 * @param code    a short, stable code (for example {@code FXT-COMPAT-FUNCTION}, or the processor's error code)
 * @param message what is not available and since which version
 * @param module  the included / imported module the issue is in, {@code null} for the checked text itself
 */
public record CompatibilityIssue(int line, int column, String code, String message, String module) {

    public CompatibilityIssue(int line, int column, String code, String message) {
        this(line, column, code, message, null);
    }

    /** This issue located in {@code module}. */
    public CompatibilityIssue inModule(String module) {
        return new CompatibilityIssue(line, column, code, message, module);
    }

    /** "line 3, column 7: message" (location parts left out when unknown). */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (module != null) {
            sb.append(module).append(", ");
        }
        if (line > 0) {
            sb.append("line ").append(line);
            if (column > 0) {
                sb.append(", column ").append(column);
            }
            sb.append(": ");
        }
        return sb.append(message).toString();
    }
}
