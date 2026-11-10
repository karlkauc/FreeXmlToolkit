package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.context.AiContext;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks before data leaves the machine (decision D7): the first use of a remote provider needs an explicit
 * confirmation naming the host; the request is shown before it is sent the first time per provider, and every time
 * while "Preview before sending" is on (unless the user chose "Don't show again" for that provider). FX thread only.
 */
public final class AiConsent {

    static String remoteConfirmedKey(String id) {
        return "ai.provider." + id + ".remoteConfirmed";
    }

    static String seenKey(String id) {
        return "ai.provider." + id + ".previewSeen";
    }

    static String skipKey(String id) {
        return "ai.provider." + id + ".previewSkip";
    }

    private AiConsent() {
    }

    /** @return whether the request may be sent */
    public static boolean confirm(ProviderConfig provider, boolean local, AiContext context) {
        var properties = PropertiesServiceImpl.getInstance();
        if (!local && !Boolean.parseBoolean(properties.get(remoteConfirmedKey(provider.id())))) {
            boolean ok = DialogHelper.showConfirmation(I18n.t("Ai.remote.title"),
                    I18n.t("Ai.remote.header", provider.name(), provider.host()),
                    I18n.t("Ai.remote.content", context.level().name()));
            if (!ok) {
                return false;
            }
            properties.set(remoteConfirmedKey(provider.id()), "true");
        }
        boolean seen = Boolean.parseBoolean(properties.get(seenKey(provider.id())));
        boolean skip = Boolean.parseBoolean(properties.get(skipKey(provider.id())));
        boolean show = !seen || (AiSettings.current().previewAlways() && !skip);
        if (!show) {
            return true;
        }
        AiSendPreviewDialog dialog = new AiSendPreviewDialog(provider, local, context);
        boolean send = dialog.showAndWait().orElse(false);
        if (send) {
            properties.set(seenKey(provider.id()), "true");
            if (dialog.dontShowAgain()) {
                properties.set(skipKey(provider.id()), "true");
            }
        }
        return send;
    }
}
