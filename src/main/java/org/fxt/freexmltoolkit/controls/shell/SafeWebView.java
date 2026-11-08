package org.fxt.freexmltoolkit.controls.shell;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * HTML previews of documents (HTML files, XSLT output, generated documentation) show content the
 * user did not write. By default a preview therefore runs no JavaScript, loads nothing from the
 * network (a Content-Security-Policy is put into the page: images, styles and fonts only as
 * {@code data:} or local files), opens no popups and does not navigate to remote pages when a link
 * is clicked. The setting {@value #ALLOW_ACTIVE_CONTENT} switches this off (also lockable by the
 * administrator policy). See docs/threat-model.md.
 */
public final class SafeWebView {

    private static final Logger logger = LogManager.getLogger(SafeWebView.class);

    /** Settings key: {@code true} allows JavaScript and remote content in HTML previews. */
    public static final String ALLOW_ACTIVE_CONTENT = "security.htmlPreview.allowActiveContent";

    /** The policy put into a page when active content is not allowed. */
    static final String POLICY = "default-src 'none'; img-src data: file:; style-src 'unsafe-inline' data: file:; "
            + "font-src data: file:; media-src data: file:";

    private static final Pattern HEAD = Pattern.compile("<head(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML = Pattern.compile("<html(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);

    private SafeWebView() {
    }

    /** @return whether the user (or the administrator) allowed JavaScript and remote content */
    public static boolean activeContentAllowed() {
        try {
            return Boolean.parseBoolean(ServiceRegistry.get(PropertiesService.class).get(ALLOW_ACTIVE_CONTENT));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A WebView for previews, configured as the setting says.
     *
     * @return the view; load content with {@link #load(WebView, String)}
     */
    public static WebView create() {
        WebView view = new WebView();
        configure(view.getEngine(), activeContentAllowed());
        return view;
    }

    /** Applies the restrictions to an engine (no-op except popups when {@code allowActive}). */
    static void configure(WebEngine engine, boolean allowActive) {
        engine.setJavaScriptEnabled(allowActive);
        engine.setCreatePopupHandler(features -> null);    // no new windows
        if (allowActive) {
            return;
        }
        engine.locationProperty().addListener((obs, old, location) -> {
            if (isRemote(location)) {
                // a clicked link: stay on the preview instead of loading a remote page
                logger.info("HTML preview: navigation to {} blocked", location);
                Platform.runLater(() -> engine.getLoadWorker().cancel());
            }
        });
    }

    /** Loads {@code html} into a preview created by {@link #create()}. */
    public static void load(WebView view, String html) {
        boolean allowActive = activeContentAllowed();
        view.getEngine().setJavaScriptEnabled(allowActive);
        view.getEngine().loadContent(allowActive ? nullToEmpty(html) : withPolicy(html));
    }

    /** @return the page with a Content-Security-Policy that blocks scripts and remote resources */
    public static String withPolicy(String html) {
        String page = nullToEmpty(html);
        String meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"" + POLICY + "\">";
        Matcher head = HEAD.matcher(page);
        if (head.find()) {
            return page.substring(0, head.end()) + meta + page.substring(head.end());
        }
        Matcher root = HTML.matcher(page);
        if (root.find()) {
            return page.substring(0, root.end()) + "<head>" + meta + "</head>" + page.substring(root.end());
        }
        return "<head>" + meta + "</head>" + page;
    }

    static boolean isRemote(String location) {
        if (location == null) {
            return false;
        }
        String lower = location.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("ftp:");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
