package org.fxt.freexmltoolkit.service.standards;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

/**
 * Typed access to the {@code standards.*} settings (Settings → Standards). The version choice is an expert
 * feature: while {@code standards.expertMode} is off (the default) every operation uses
 * {@link StandardsProfile#LATEST} and the UI shows no version controls.
 *
 * <p>Tools remember their own choice under {@code standards.tool.<tool>.<component>}; an unset tool choice
 * falls back to the project binding and then to the global default ({@link StandardsProfile#resolve}).
 */
public final class StandardsSettings {

    public static final String KEY_EXPERT_MODE = "standards.expertMode";
    public static final String KEY_XPATH = "standards.xpath";
    public static final String KEY_XQUERY = "standards.xquery";
    public static final String KEY_XSLT = "standards.xslt";
    public static final String KEY_XSD = "standards.xsd";
    public static final String KEY_XML = "standards.xml";
    public static final String KEY_SCHEMATRON = "standards.schematronBinding";

    /** Tool ids for {@link #toolChoice} / {@link #setToolChoice}. */
    public static final String TOOL_QUERY_CONSOLE = "queryConsole";
    public static final String TOOL_XPATH_SEARCH = "xpathSearch";
    public static final String TOOL_TRANSFORM = "transform";
    public static final String TOOL_VALIDATION = "validation";

    private final Function<String, String> getter;
    private final BiConsumer<String, String> setter;

    /**
     * @param getter reads one setting ({@code null} = not set)
     * @param setter stores one setting ({@code null} value = remove); may be {@code null} for read-only use
     */
    public StandardsSettings(Function<String, String> getter, BiConsumer<String, String> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    /** The settings of this installation. */
    public static StandardsSettings current() {
        org.fxt.freexmltoolkit.service.PropertiesService props = PropertiesServiceImpl.getInstance();
        return new StandardsSettings(props::get, (k, v) -> props.set(k, v == null ? "" : v));
    }

    /** {@code standards.expertMode}: whether versions can be chosen at all (default off). */
    public boolean expertMode() {
        return Boolean.parseBoolean(get(KEY_EXPERT_MODE));
    }

    /** The global defaults; {@link StandardsProfile#LATEST} while the expert mode is off. */
    public StandardsProfile global() {
        if (!expertMode()) {
            return StandardsProfile.LATEST;
        }
        return new StandardsProfile(
                XPathVersion.parse(get(KEY_XPATH)),
                XQueryVersion.parse(get(KEY_XQUERY)),
                XsltVersion.parse(get(KEY_XSLT)),
                XsdVersionMode.parse(get(KEY_XSD)),
                XmlVersionMode.parse(get(KEY_XML)),
                SchematronBinding.parse(get(KEY_SCHEMATRON)));
    }

    /**
     * The choice a tool remembered ({@link StandardsProfile#EMPTY} components = "default"); always
     * {@link StandardsProfile#EMPTY} while the expert mode is off.
     */
    public StandardsProfile toolChoice(String tool) {
        if (!expertMode()) {
            return StandardsProfile.EMPTY;
        }
        return new StandardsProfile(
                StandardVersion.parseStrict(XPathVersion.class, get(toolKey(tool, "xpath"))),
                StandardVersion.parseStrict(XQueryVersion.class, get(toolKey(tool, "xquery"))),
                StandardVersion.parseStrict(XsltVersion.class, get(toolKey(tool, "xslt"))),
                StandardVersion.parseStrict(XsdVersionMode.class, get(toolKey(tool, "xsd"))),
                StandardVersion.parseStrict(XmlVersionMode.class, get(toolKey(tool, "xml"))),
                StandardVersion.parseStrict(SchematronBinding.class, get(toolKey(tool, "schematron"))));
    }

    /** What a tool runs with: global ← project ← the tool's own choice. */
    public StandardsProfile forTool(String tool, StandardsProfile project) {
        if (!expertMode()) {
            return StandardsProfile.LATEST;
        }
        return StandardsProfile.resolve(global(), project, toolChoice(tool), null);
    }

    /** Stores the tool's choice for one component ({@code null} = back to the default). */
    public void setToolChoice(String tool, String component, StandardVersion value) {
        set(toolKey(tool, component), value == null ? null : value.cliValue());
    }

    /** The settings key of a tool's choice for {@code component} (xpath, xquery, xslt, xsd, xml, schematron). */
    public static String toolKey(String tool, String component) {
        return "standards.tool." + tool + "." + component;
    }

    private String get(String key) {
        String value = getter.apply(key);
        return value == null || value.isBlank() ? null : value;
    }

    private void set(String key, String value) {
        if (setter != null) {
            setter.accept(key, value);
        }
    }
}
