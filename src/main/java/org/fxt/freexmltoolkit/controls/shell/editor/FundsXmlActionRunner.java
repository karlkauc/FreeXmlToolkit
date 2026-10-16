package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys;

/**
 * UI-free access to the (conditional) FundsXML extension for the shell. The extension is
 * only surfaced when enabled in the settings ({@code fundsxml.enabled}).
 */
public final class FundsXmlActionRunner {

    private FundsXmlActionRunner() {
    }

    /** @return {@code true} if the FundsXML extension is enabled in the settings. */
    public static boolean isEnabled() {
        try {
            return Boolean.parseBoolean(ServiceRegistry.get(PropertiesService.class)
                    .loadProperties().getProperty(FundsXmlPropertyKeys.ENABLED, "false"));
        } catch (Throwable t) {
            return false;
        }
    }
}
