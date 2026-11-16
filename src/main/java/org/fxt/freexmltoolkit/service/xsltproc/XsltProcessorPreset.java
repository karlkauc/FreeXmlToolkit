package org.fxt.freexmltoolkit.service.xsltproc;

import java.util.List;

/**
 * Starting points for an external XSLT processor: the programs looked up on the PATH, the argument
 * template ({@code ${xml} ${xsl} ${out} ${baseDir} ${params}}) and how one stylesheet parameter is passed
 * ({@code {name}}, {@code {value}}). The user can change every part.
 */
public enum XsltProcessorPreset {

    /**
     * Saxon PE / EE (or any Saxon) on the command line: {@code java} with the Saxon jar; the jar path in the
     * template has to be set. The licence file goes next to the jar.
     */
    SAXON("Saxon PE/EE (Java)", List.of("java"),
            "-cp \"saxon-pe-12.jar\" net.sf.saxon.Transform -s:${xml} -xsl:${xsl} -o:${out} ${params}",
            "{name}={value}"),

    /** libxslt's {@code xsltproc} (XSLT 1.0). */
    XSLTPROC("xsltproc (libxslt)", List.of("xsltproc"),
            "${params} --output ${out} ${xsl} ${xml}",
            "--stringparam {name} {value}"),

    /** Altova RaptorXML (Server). */
    RAPTORXML("Altova RaptorXML", List.of("raptorxml", "raptorxmlserver", "RaptorXML", "RaptorXMLServer"),
            "xslt --input=${xml} --output=${out} ${params} ${xsl}",
            "--param={name}:'{value}'"),

    /** Any other program or wrapper script. */
    CUSTOM("Custom", List.of(),
            "${xml} ${xsl} ${out} ${params}",
            "{name}={value}");

    private final String displayName;
    private final List<String> programNames;
    private final String arguments;
    private final String parameters;

    XsltProcessorPreset(String displayName, List<String> programNames, String arguments, String parameters) {
        this.displayName = displayName;
        this.programNames = programNames;
        this.arguments = arguments;
        this.parameters = parameters;
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

    /** @return the parameter template this preset starts with */
    public String parameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** @return the preset of that name, {@link #CUSTOM} for an unknown or missing one */
    public static XsltProcessorPreset of(String name) {
        if (name != null) {
            for (XsltProcessorPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(name.strip())) {
                    return preset;
                }
            }
        }
        return CUSTOM;
    }
}
