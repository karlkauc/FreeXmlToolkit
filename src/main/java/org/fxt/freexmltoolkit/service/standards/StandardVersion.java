package org.fxt.freexmltoolkit.service.standards;

/**
 * A selectable version of one of the XML standards (XPath, XQuery, XSLT, XSD, XML, Schematron query binding).
 * Every implementation is an enum whose constant order runs from the oldest to the newest version.
 */
public interface StandardVersion {

    /** The text shown in the UI (for example "XPath 2.0"). */
    String label();

    /** The short value used in settings, project files and on the command line (for example "2.0"). */
    String cliValue();

    /** Whether this is the version FreeXmlToolkit uses when the user chose nothing (today's behaviour). */
    boolean isDefault();

    /**
     * Parses {@code value} against the {@link #cliValue()} of {@code type}'s constants (also accepting the
     * constant's name), or returns {@code fallback} for a blank or unknown value.
     */
    static <E extends Enum<E> & StandardVersion> E parse(Class<E> type, String value, E fallback) {
        E parsed = parseStrict(type, value);
        return parsed != null ? parsed : fallback;
    }

    /** Like {@link #parse} but returns {@code null} for a blank or unknown value. */
    static <E extends Enum<E> & StandardVersion> E parseStrict(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        for (E constant : type.getEnumConstants()) {
            if (constant.cliValue().equalsIgnoreCase(v) || constant.name().equalsIgnoreCase(v)) {
                return constant;
            }
        }
        return null;
    }

    /** The comma-separated list of accepted values, for help and error texts. */
    static <E extends Enum<E> & StandardVersion> String acceptedValues(Class<E> type) {
        StringBuilder sb = new StringBuilder();
        for (E constant : type.getEnumConstants()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(constant.cliValue());
        }
        return sb.toString();
    }
}
