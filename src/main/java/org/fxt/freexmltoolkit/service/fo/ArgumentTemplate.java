package org.fxt.freexmltoolkit.service.fo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.external.CommandTemplate;

/**
 * The argument template of an external XSL-FO processor, turned into the argument list of one run.
 *
 * <ul>
 *   <li>Arguments are separated by whitespace; double quotes keep spaces in one argument.
 *       The list goes to the program directly, never through a shell.</li>
 *   <li>Placeholders {@code ${name}} are replaced inside an argument; a path with spaces stays
 *       one argument. Names: {@value #FO}, {@value #PDF} (also {@value #OUT}), {@value #XML},
 *       {@value #XSL}, {@value #BASE_DIR}, {@value #TITLE}, {@value #AUTHOR}, {@value #SUBJECT},
 *       {@value #PDFA}.</li>
 *   <li>An optional group {@code [ … ]} is left out when one of its placeholders is empty;
 *       {@value #PDFA} is empty unless PDF/A was asked for and expands to nothing otherwise, so
 *       {@code [-pdfprofile PDF/A-1b ${pdfa}]} adds a flag only for PDF/A.</li>
 *   <li>An argument that expands to an empty text outside a group is dropped.</li>
 * </ul>
 */
public final class ArgumentTemplate {

    /** The XSL-FO document to render. */
    public static final String FO = "fo";
    /** The PDF to write. */
    public static final String PDF = "pdf";
    /** Same as {@value #PDF}. */
    public static final String OUT = "out";
    /** The XML input of the XSLT stage (empty when rendering a finished FO). */
    public static final String XML = "xml";
    /** The stylesheet of the XSLT stage (empty when rendering a finished FO). */
    public static final String XSL = "xsl";
    /** The folder relative references are resolved against. */
    public static final String BASE_DIR = "baseDir";
    /** PDF title metadata. */
    public static final String TITLE = "title";
    /** PDF author metadata. */
    public static final String AUTHOR = "author";
    /** PDF subject metadata. */
    public static final String SUBJECT = "subject";
    /** Non-empty (but expanding to nothing) when PDF/A was asked for. */
    public static final String PDFA = "pdfa";

    /** Every placeholder name, in the order the dialog lists them. */
    public static final List<String> NAMES = List.of(FO, PDF, OUT, XML, XSL, BASE_DIR, TITLE, AUTHOR, SUBJECT, PDFA);

    /** Marker value of {@value #PDFA} when PDF/A is asked for: present, but expands to nothing. */
    public static final String FLAG_ON = CommandTemplate.FLAG_ON;

    private static final CommandTemplate TEMPLATE = new CommandTemplate(NAMES);

    private ArgumentTemplate() {
    }

    /**
     * Expands a template.
     *
     * @param template the template text
     * @param values   placeholder values (missing or {@code null} = empty)
     * @return the arguments
     * @throws IllegalArgumentException for an unknown placeholder, an unclosed quote or bracket
     */
    public static List<String> expand(String template, Map<String, String> values) {
        return TEMPLATE.expand(template, values);
    }

    /**
     * @return the placeholder names used in the template
     * @throws IllegalArgumentException for an unknown placeholder or a syntax error
     */
    public static Set<String> placeholders(String template) {
        return TEMPLATE.placeholders(template);
    }

    /** @return {@code null} when the template is valid, else the problem in plain words */
    public static String validate(String template) {
        try {
            Set<String> used = placeholders(template);
            if (!used.contains(FO)) {
                return "The arguments must contain ${fo}, the XSL-FO document to render.";
            }
            if (!used.contains(PDF) && !used.contains(OUT)) {
                return "The arguments must contain ${pdf}, the PDF to write.";
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
