package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.service.NamespaceSchemaDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Types of a remote {@code xs:import} that had to be downloaded in the background must reach
 * the views that list the active schema's types: {@link EditorHost} re-parses the affected
 * tab and bumps {@link EditorHost#schemaImportsRevisionProperty()}.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostRemoteImportRefreshTest {

    private static final String NS_REMOTE = "https://example.com/refresh-remote";

    private static final String REMOTE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="https://example.com/refresh-remote">
              <xs:simpleType name="RType"><xs:restriction base="xs:string"/></xs:simpleType>
            </xs:schema>
            """;

    private static final String MAIN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="https://example.com/refresh-main">
              <xs:import namespace="https://example.com/refresh-remote" schemaLocation="missing.xsd"/>
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

    /** Stands in for the network: the download blocks until released, then the schema is cached. */
    private static final class FakeDownloader extends NamespaceSchemaDownloader {
        private final Path cachedFile;
        private final AtomicBoolean downloaded = new AtomicBoolean();
        final CountDownLatch release = new CountDownLatch(1);

        FakeDownloader(Path cachedFile) {
            super(null, null);
            this.cachedFile = cachedFile;
        }

        @Override
        public Optional<ResolvedNamespaceSchema> resolve(String namespace, String schemaLocation) {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void reset() {
        XsdUiParser.resetForTesting();
    }

    private FakeDownloader openMain(Path tmp) throws Exception {
        XsdUiParser.resetForTesting();
        Path cached = Files.writeString(tmp.resolve("cached.xsd"), REMOTE);
        Path main = Files.writeString(tmp.resolve("main.xsd"), MAIN);
        FakeDownloader downloader = new FakeDownloader(cached);
        XsdUiParser.setFactoriesForTesting(() -> {
            XsdNodeFactory factory = new XsdNodeFactory();
            factory.setRemoteNamespaceFallbackEnabled(true);
            factory.setNamespaceSchemaDownloader(downloader);
            return factory;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(main));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:import")).orElse(false));
        return downloader;
    }

    private List<String> typeNames() {
        return WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
    }

    private void awaitRevision() throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.schemaImportsRevisionProperty().get() > 0));
    }

    @Test
    void textModeListingSeesImportedTypesAfterTheDownload(@TempDir Path tmp) throws Exception {
        FakeDownloader downloader = openMain(tmp);

        assertFalse(typeNames().contains("RType"), "the first listing must not wait for the download");
        assertEquals(0, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.schemaImportsRevisionProperty().get()));

        downloader.release.countDown();
        awaitRevision();
        assertTrue(typeNames().contains("RType"), "after the signal the listing resolves from the cache");
    }

    @Test
    void structuredModelIsReparsedEvenWhenAnotherCallerStartedTheDownload(@TempDir Path tmp) throws Exception {
        FakeDownloader downloader = openMain(tmp);

        // The Text-mode listing starts the download; the Tree model is parsed while it runs.
        assertFalse(typeNames().contains("RType"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        assertFalse(typeNames().contains("RType"));

        downloader.release.countDown();
        awaitRevision();
        assertTrue(typeNames().contains("RType"), "the Tree model must be re-parsed with the import");
        assertEquals(ViewMode.TREE, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeViewModeProperty().get()));
    }
}
