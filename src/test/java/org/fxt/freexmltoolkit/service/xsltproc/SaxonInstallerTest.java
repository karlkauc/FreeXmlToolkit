package org.fxt.freexmltoolkit.service.xsltproc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.service.progress.CancelledException;
import org.fxt.freexmltoolkit.service.progress.Progress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Downloading Saxon HE 13: checksums, nothing half-written, only missing jars fetched, the processor it
 * creates. The "repository" is the jars Gradle hands the tests ({@code fxt.test.saxon13.classpath}).
 */
public class SaxonInstallerTest {

    @TempDir
    Path dir;

    /** The jars from the Gradle configuration {@code saxon13}, by file name. */
    public static Path saxon13Jar(String fileName) {
        String classpath = System.getProperty("fxt.test.saxon13.classpath", "");
        assumeTrue(!classpath.isBlank(), "fxt.test.saxon13.classpath is not set (run through Gradle)");
        return Stream.of(classpath.split(File.pathSeparator)).map(Path::of)
                .filter(p -> p.getFileName().toString().equals(fileName)).findFirst()
                .orElseThrow(() -> new AssertionError(fileName + " not in " + classpath));
    }

    /** Serves the real jars and records the URLs asked for. */
    static SaxonInstaller.Fetcher realJars(List<URI> asked) {
        return uri -> {
            asked.add(uri);
            String path = uri.getPath();
            return Files.readAllBytes(saxon13Jar(path.substring(path.lastIndexOf('/') + 1)));
        };
    }

    @Test
    void installsCheckedJarsAndCreatesTheProcessor() throws Exception {
        List<URI> asked = new ArrayList<>();
        SaxonInstaller installer = new SaxonInstaller(dir.resolve("13.1"), SaxonInstaller.MAVEN_CENTRAL, realJars(asked));
        assertFalse(installer.isInstalled());
        List<Long> steps = new ArrayList<>();
        installer.install(new Recorder(steps, false));

        assertTrue(installer.isInstalled());
        assertEquals(List.of(
                URI.create("https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/13.1/Saxon-HE-13.1.jar"),
                URI.create("https://repo1.maven.org/maven2/org/xmlresolver/xmlresolver/6.1.0/xmlresolver-6.1.0.jar")), asked);
        assertEquals(SaxonInstaller.downloadSize(), steps.getLast());
        try (Stream<Path> files = Files.list(installer.directory())) {
            assertEquals(2, files.count(), "no temporary files left");
        }

        XsltProcessorConfig config = installer.processorConfig();
        assertEquals("Saxon HE 13.1", config.name());
        assertEquals(XsltProcessorPreset.SAXON_HE, config.preset());
        assertTrue(config.arguments().contains(installer.classpath()), config.arguments());
        assertEquals(null, config.validate());
        assertTrue(config.supportsParameters());
        assertTrue(config.resolveExecutable().isPresent(), "the running JDK's java");

        asked.clear();
        installer.install(Progress.NONE);
        assertTrue(asked.isEmpty(), "installed jars are not fetched again");
    }

    @Test
    void aWrongChecksumInstallsNothing() throws Exception {
        SaxonInstaller installer = new SaxonInstaller(dir, SaxonInstaller.MAVEN_CENTRAL, uri -> new byte[] {1, 2, 3});
        IOException e = assertThrows(IOException.class, () -> installer.install(Progress.NONE));
        assertTrue(e.getMessage().contains("Checksum mismatch"), e.getMessage());
        assertFalse(installer.isInstalled());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void aFailingDownloadAndACancelStopTheInstall() {
        SaxonInstaller failing = new SaxonInstaller(dir, SaxonInstaller.MAVEN_CENTRAL, uri -> {
            throw new IOException("HTTP 503 for " + uri);
        });
        assertThrows(IOException.class, () -> failing.install(Progress.NONE));
        assertFalse(failing.isInstalled());

        SaxonInstaller cancelled = new SaxonInstaller(dir, SaxonInstaller.MAVEN_CENTRAL, uri -> {
            throw new AssertionError("nothing is fetched after a cancel");
        });
        assertThrows(CancelledException.class, () -> cancelled.install(new Recorder(new ArrayList<>(), true)));
    }

    @Test
    void aDamagedJarIsReplaced() throws Exception {
        List<URI> asked = new ArrayList<>();
        SaxonInstaller installer = new SaxonInstaller(dir, SaxonInstaller.MAVEN_CENTRAL, realJars(asked));
        installer.install(Progress.NONE);
        Path resolver = dir.resolve("xmlresolver-6.1.0.jar");
        Files.write(resolver, new byte[(int) Files.size(resolver)]); // same size, wrong content
        asked.clear();
        installer.install(Progress.NONE);
        assertEquals(1, asked.size(), String.valueOf(asked));
        assertEquals(SaxonInstaller.ARTIFACTS.get(1).sha256(), SaxonInstaller.sha256(Files.readAllBytes(resolver)));
    }

    @Test
    void thePresetPointsAtTheDefaultFolder() {
        String arguments = XsltProcessorPreset.SAXON_HE.arguments();
        assertTrue(arguments.contains(SaxonInstaller.defaultDirectory().resolve("Saxon-HE-13.1.jar").toString()), arguments);
        assertTrue(arguments.contains("net.sf.saxon.Transform"), arguments);
        assertTrue(XsltProcessorPreset.SAXON_HE.runsJava());
        assertFalse(XsltProcessorPreset.XSLTPROC.runsJava());
    }

    private record Recorder(List<Long> steps, boolean cancelled) implements Progress {
        @Override
        public void message(String text) {
        }

        @Override
        public void progress(long done, long total) {
            steps.add(done);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
