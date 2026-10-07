package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Parses XSD text for callers on the FX thread without ever touching the network.
 *
 * <p>Remote {@code xs:import}s (http schemaLocation or namespace-URL fallback) are resolved
 * from the local schema cache only. Whatever is missing there is downloaded once per session
 * on the background executor; when that made an import resolvable the caller is notified on
 * the FX thread and can parse again, this time hitting the cache. Lookups that failed are not
 * retried in the same session, so an unreachable server costs one background attempt.</p>
 */
final class XsdUiParser {

    private static final Logger logger = LogManager.getLogger(XsdUiParser.class);

    /** Schema URLs / namespaces whose download was already attempted in this session. */
    private static final Set<String> ATTEMPTED = ConcurrentHashMap.newKeySet();

    private XsdUiParser() {
        // utility
    }

    /**
     * Parses {@code text} with cache-only import resolution.
     *
     * @param text              the XSD source
     * @param path              the document's path on disk, or {@code null} when unsaved
     * @param onRemoteAvailable run on the FX thread after a background download made a skipped
     *                          import resolvable; may be {@code null}
     * @return the parsed schema
     * @throws Exception if the text cannot be parsed
     */
    static XsdSchema parse(String text, Path path, Runnable onRemoteAvailable) throws Exception {
        return parse(text, path, onRemoteAvailable, XsdNodeFactory::new);
    }

    /** As {@link #parse(String, Path, Runnable)}, with the factory source injectable for tests. */
    static XsdSchema parse(String text, Path path, Runnable onRemoteAvailable,
                           Supplier<XsdNodeFactory> factories) throws Exception {
        XsdNodeFactory factory = factories.get();
        factory.setRemoteDownloadsAllowed(false);
        XsdSchema schema = parse(factory, text, path);

        Set<String> deferred = factory.getDeferredRemoteLookups();
        boolean anyNew = false;
        for (String lookup : deferred) {
            anyNew |= ATTEMPTED.add(lookup);
        }
        if (anyNew) {
            FxtGui.executorService.submit(() -> download(text, path, deferred, onRemoteAvailable, factories));
        }
        return schema;
    }

    private static void download(String text, Path path, Set<String> deferred, Runnable onRemoteAvailable,
                                 Supplier<XsdNodeFactory> factories) {
        try {
            // A full parse with downloads enabled fills the schema cache, transitively.
            parse(factories.get(), text, path);

            XsdNodeFactory check = factories.get();
            check.setRemoteDownloadsAllowed(false);
            parse(check, text, path);
            boolean resolvedSomething = !check.getDeferredRemoteLookups().containsAll(deferred);
            if (resolvedSomething && onRemoteAvailable != null) {
                Platform.runLater(onRemoteAvailable);
            }
        } catch (Exception e) {
            logger.debug("Background download of remote imports failed: {}", e.getMessage());
        }
    }

    private static XsdSchema parse(XsdNodeFactory factory, String text, Path path) throws Exception {
        return path != null
                ? factory.fromStringWithSchemaFile(text, path, path.getParent())
                : factory.fromString(text);
    }

    /** For tests: forget which downloads were already attempted. */
    static void resetForTesting() {
        ATTEMPTED.clear();
    }
}
