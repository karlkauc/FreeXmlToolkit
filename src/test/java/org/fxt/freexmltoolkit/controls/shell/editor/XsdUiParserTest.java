package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.NamespaceSchemaDownloader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * {@link XsdUiParser} must never download on the calling thread: remote imports are fetched
 * on the background executor, and the caller is told when a re-parse will resolve them.
 */
@ExtendWith(ApplicationExtension.class)
class XsdUiParserTest {

    private static final String NS_REMOTE = "https://example.com/ui-parser-remote";

    private static final String REMOTE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="https://example.com/ui-parser-remote">
              <xs:simpleType name="RType"><xs:restriction base="xs:string"/></xs:simpleType>
            </xs:schema>
            """;

    private static final String MAIN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="https://example.com/ui-parser-main">
              <xs:import namespace="https://example.com/ui-parser-remote" schemaLocation="missing.xsd"/>
            </xs:schema>
            """;

    /** Stands in for the network: the schema is "cached" only after {@code resolve} ran. */
    private static final class FakeDownloader extends NamespaceSchemaDownloader {
        private final Path cachedFile;
        private final boolean reachable;
        final AtomicBoolean downloaded = new AtomicBoolean();
        final AtomicInteger downloadCalls = new AtomicInteger();
        volatile String downloadThread;

        FakeDownloader(Path cachedFile, boolean reachable) {
            super(null, null);
            this.cachedFile = cachedFile;
            this.reachable = reachable;
        }

        @Override
        public Optional<ResolvedNamespaceSchema> resolve(String namespace, String schemaLocation) {
            downloadCalls.incrementAndGet();
            downloadThread = Thread.currentThread().getName();
            if (!reachable) {
                return Optional.empty();
            }
            downloaded.set(true);
            return resolveFromCache(namespace);
        }

        @Override
        public Optional<ResolvedNamespaceSchema> resolveFromCache(String namespace) {
            return downloaded.get() && NS_REMOTE.equals(namespace)
                    ? Optional.of(new ResolvedNamespaceSchema(REMOTE, cachedFile, "cache:" + cachedFile))
                    : Optional.empty();
        }
    }

    @Start
    void start(Stage stage) {
        // only the FX toolkit is needed
    }

    @BeforeEach
    void reset() {
        XsdUiParser.resetForTesting();
    }

    private static Supplier<XsdNodeFactory> factoriesWith(NamespaceSchemaDownloader downloader) {
        return () -> {
            XsdNodeFactory factory = new XsdNodeFactory();
            factory.setRemoteNamespaceFallbackEnabled(true);
            factory.setNamespaceSchemaDownloader(downloader);
            return factory;
        };
    }

    @Test
    void downloadsInBackgroundAndNotifiesOnFxThread(@TempDir Path tmp) throws Exception {
        Path cached = Files.writeString(tmp.resolve("cached.xsd"), REMOTE);
        Path main = Files.writeString(tmp.resolve("main.xsd"), MAIN);
        FakeDownloader downloader = new FakeDownloader(cached, true);
        Supplier<XsdNodeFactory> factories = factoriesWith(downloader);

        CountDownLatch notified = new CountDownLatch(1);
        AtomicBoolean notifiedOnFxThread = new AtomicBoolean();
        String caller = Thread.currentThread().getName();

        XsdSchema first = XsdUiParser.parse(MAIN, main, () -> {
            notifiedOnFxThread.set(Platform.isFxApplicationThread());
            notified.countDown();
        }, factories);

        assertFalse(first.getImportedSchemas().containsKey(NS_REMOTE),
                "the first parse must not wait for the download");
        assertTrue(notified.await(10, TimeUnit.SECONDS), "caller must be told when the import became resolvable");
        assertTrue(notifiedOnFxThread.get());
        assertNotEquals(caller, downloader.downloadThread, "download must not run on the calling thread");

        XsdSchema second = XsdUiParser.parse(MAIN, main, null, factories);
        assertTrue(second.getImportedSchemas().containsKey(NS_REMOTE), "re-parse resolves from the cache");
    }

    @Test
    void unreachableSchemaIsAttemptedOnlyOncePerSession(@TempDir Path tmp) throws Exception {
        Path main = Files.writeString(tmp.resolve("main.xsd"), MAIN);
        FakeDownloader downloader = new FakeDownloader(tmp.resolve("unused.xsd"), false);
        Supplier<XsdNodeFactory> factories = factoriesWith(downloader);
        AtomicBoolean notified = new AtomicBoolean();

        XsdUiParser.parse(MAIN, main, () -> notified.set(true), factories);
        long deadline = System.currentTimeMillis() + 10_000;
        while (downloader.downloadCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, downloader.downloadCalls.get());

        XsdUiParser.parse(MAIN, main, () -> notified.set(true), factories);
        Thread.sleep(500);
        assertEquals(1, downloader.downloadCalls.get(), "a failed lookup must not be retried in the same session");
        assertFalse(notified.get());
    }
}
