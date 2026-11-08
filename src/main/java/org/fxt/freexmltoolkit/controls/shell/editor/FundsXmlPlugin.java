package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginMenuItem;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlStartupSync;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlUpdateChecker;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * The FundsXML extension as a plugin — the reference for the plugin API: the FundsXML activity
 * (shown while the extension is enabled), the Help menu links, the startup sync of the schema
 * content, and the one-time hint when a FundsXML document is opened while the extension is off.
 * Registered in {@code META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin}.
 */
public final class FundsXmlPlugin implements FxtPlugin {

    private static final Logger logger = LogManager.getLogger(FundsXmlPlugin.class);

    /** The plugin's and the activity's id. */
    public static final String ID = "fundsxml";

    /** Fetches the content after the hint turned the extension on; replaceable in tests. */
    private static volatile Consumer<String> contentFetcher = FundsXmlPlugin::fetchContent;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "FundsXML";
    }

    @Override
    public String description() {
        return "FundsXML schemas, examples and tools for fund data (activity, Help links, schema updates).";
    }

    @Override
    public void register(PluginContext context) {
        context.addActivity(new PluginActivity(ID, "FundsXML", "bi-file-earmark-code", Workflow.FUNDSXML,
                ui -> new FundsXmlPanel(ui.editorHost()), FundsXmlRunner::isEnabled));
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "fundsxml-site", "FundsXML Website",
                "bi-globe", ui -> ProjectLinks.openInBrowser(ProjectLinks.FUNDSXML_SITE_URL), FundsXmlRunner::isEnabled));
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "fundsxml-docs", "FundsXML Schema Docs",
                "bi-file-earmark-text", ui -> ProjectLinks.openInBrowser(ProjectLinks.FUNDSXML_SCHEMA_DOCS_URL),
                FundsXmlRunner::isEnabled));
        context.addStartupTask(FundsXmlPlugin::startupSync);
        context.addDocumentListener((ui, path, content) -> {
            if (FundsXmlHint.shouldOffer(content)) {
                showHint(ui);
            }
        });
    }

    /** Shows the one-time hint above the editor (UX plan 5.5). */
    public static void showHint(PluginUi ui) {
        FundsXmlHint.markShown();
        ui.showEditorBar(new FundsXmlHintBar(() -> enableFromHint(ui), () -> { }));
    }

    /**
     * "Enable FundsXML extensions" in the hint: the same as ticking the box in Settings and saving it —
     * the flag is stored, the rail gets its FundsXML button and the content is fetched.
     */
    static void enableFromHint(PluginUi ui) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(FundsXmlPropertyKeys.ENABLED, "true");
        } catch (RuntimeException e) {
            logger.warn("Could not enable the FundsXML extension: {}", e.getMessage());
            return;
        }
        ui.refreshContributions();
        contentFetcher.accept("hint-enable");
    }

    /** Downloads the FundsXML content (or registers the cached one) after the extension was turned on. */
    public static void fetchContent(String trigger) {
        var coordinator = FundsXmlDownloadCoordinator.getInstance();
        if (FundsXmlExtensionService.getInstance().isCacheEmpty()) {
            coordinator.startBackgroundDownload(trigger);
        } else {
            coordinator.runRegisterOnly();
        }
    }

    /** Test seam: replaces the content download started by the hint. */
    public static void setContentFetcherForTesting(Consumer<String> fetcher) {
        contentFetcher = fetcher == null ? FundsXmlPlugin::fetchContent : fetcher;
    }

    /**
     * Startup sync: downloads missing content, installs updates in the background, or just
     * re-registers cached content — silently (log + toast, never a dialog). Runs on a background
     * thread, where the blocking GitHub call inside {@code determineAction()} is acceptable.
     */
    static void startupSync() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            var service = ServiceRegistry.get(FundsXmlExtensionService.class);
            var checker = new FundsXmlUpdateChecker(props, service, FundsXmlCache.getInstance());
            var sync = new FundsXmlStartupSync(props::loadProperties, service, checker);
            dispatch(sync.determineAction(), FundsXmlDownloadCoordinator.getInstance());
        } catch (Throwable t) {
            logger.warn("FundsXML startup sync failed: {}", t.getMessage());
        }
    }

    /** Maps a startup-sync decision onto coordinator work. */
    public static void dispatch(FundsXmlStartupSync.Action action, FundsXmlDownloadCoordinator coordinator) {
        switch (action) {
            case DOWNLOAD_INITIAL, DOWNLOAD_UPDATE -> coordinator.startBackgroundDownload("startup");
            case REGISTER_ONLY -> coordinator.runRegisterOnly();
            // Even with the feature off, stale seeded entries must be cleaned up.
            case NONE -> coordinator.runCleanupOnly();
            default -> { }
        }
    }
}
