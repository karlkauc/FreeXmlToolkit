package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.query.DocumentNamespaces;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;

/**
 * Settings of XPath / XQuery runs in the Query Console and query tabs (Settings → Queries): the time limit, whether
 * unprefixed names match the document's default namespace, and the hit count while typing.
 */
public final class QuerySettings {

    /** Time limit of a run in seconds; 0 = none. */
    public static final String TIMEOUT_KEY = "query.timeoutSeconds";
    /** Unprefixed element names match the document's default namespace. */
    public static final String DEFAULT_NAMESPACE_KEY = "query.useDocumentDefaultNamespace";
    /** Count the hits while typing. */
    public static final String LIVE_PREVIEW_KEY = "query.livePreview";

    static final int DEFAULT_TIMEOUT_SECONDS = 60;

    static {
        // completion proposes names that match under the current choice (*:Name when unprefixed names do not)
        org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XmlDocumentElementExtractor
                .unprefixedMatchesDefault = QuerySettings::useDocumentDefaultNamespace;
    }

    private QuerySettings() {
    }

    /** Loads the class, which connects completion to the setting (called by the query inputs). */
    static void install() {
        // the static initializer does the work
    }

    /** @return the time limit of a run in seconds, 0 for none */
    public static int timeoutSeconds() {
        String value = get(TIMEOUT_KEY);
        try {
            return value == null || value.isBlank() ? DEFAULT_TIMEOUT_SECONDS : Math.max(0, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    /** @return whether unprefixed element names match the document's default namespace (default on) */
    public static boolean useDocumentDefaultNamespace() {
        return !"false".equalsIgnoreCase(get(DEFAULT_NAMESPACE_KEY));
    }

    /** Switches {@link #useDocumentDefaultNamespace()} and stores it. */
    public static void setUseDocumentDefaultNamespace(boolean on) {
        set(DEFAULT_NAMESPACE_KEY, String.valueOf(on));
    }

    /** @return whether the hits are counted while typing (default on) */
    public static boolean livePreview() {
        return !"false".equalsIgnoreCase(get(LIVE_PREVIEW_KEY));
    }

    /**
     * The default namespace a run on a document with {@code bindings} declares: the document's, when the setting is
     * on and the engine can bind it (not XPath 1.0); else {@code null}.
     */
    public static String defaultNamespace(DocumentNamespaces.Bindings bindings, XPathVersion version) {
        if (bindings == null || bindings.defaultNamespace() == null || version == XPathVersion.V1_0
                || !useDocumentDefaultNamespace()) {
            return null;
        }
        return bindings.defaultNamespace();
    }

    private static String get(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void set(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (RuntimeException e) {
            // not stored: the choice holds until the next start
        }
    }
}
