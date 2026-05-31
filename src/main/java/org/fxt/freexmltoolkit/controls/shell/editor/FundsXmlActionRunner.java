package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys;

/**
 * UI-free access to the (conditional) FundsXML extension for the shell, reusing
 * {@link FundsXmlExtensionService}. The extension is only surfaced when enabled
 * in the settings ({@code fundsxml.enabled}); the update check is network-bound.
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

    /** Checks GitHub for a newer FundsXML schema release. @return a status message. */
    public static String checkForUpdate() {
        try {
            GitHubRelease latest = FundsXmlExtensionService.getInstance().checkForUpdates();
            return latest == null
                    ? "FundsXML schema is up to date."
                    : "FundsXML update available: " + latest.tagName();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
