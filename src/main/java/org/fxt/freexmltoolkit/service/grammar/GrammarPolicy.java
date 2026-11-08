package org.fxt.freexmltoolkit.service.grammar;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Whether documents may be validated against their DTD: the setting {@value #DTD_ALLOWED}
 * (Settings → Security; lockable by the administrator policy). Off, a DOCTYPE is only checked for
 * well-formedness and DTDs are never loaded.
 */
public final class GrammarPolicy {

    /** Settings key: {@code false} disables DTD validation and DTD loading. */
    public static final String DTD_ALLOWED = "security.dtd.allowed";

    private GrammarPolicy() {
    }

    public static boolean dtdAllowed() {
        try {
            return !"false".equalsIgnoreCase(ServiceRegistry.get(PropertiesService.class).get(DTD_ALLOWED));
        } catch (RuntimeException e) {
            return true;
        }
    }
}
