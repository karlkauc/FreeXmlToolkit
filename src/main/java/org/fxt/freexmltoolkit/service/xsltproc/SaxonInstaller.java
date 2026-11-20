package org.fxt.freexmltoolkit.service.xsltproc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.progress.Progress;

/**
 * Downloads a newer Saxon HE than the bundled one, to run as an external XSLT processor
 * ({@link XsltProcessorPreset#SAXON_HE}) in a process of its own.
 *
 * <p>The bundled Saxon HE stays on 12.x because XML Calabash (XProc) is built against it, and two Saxon
 * versions cannot share one JVM (same class names). Saxon {@value #VERSION} therefore runs with the
 * application's {@code java} ({@link org.fxt.freexmltoolkit.util.JavaRuntime}) and the jars this class
 * fetches from Maven Central into {@code ~/.freeXmlToolkit/saxon/<version>/}. Every jar is checked against a
 * SHA-256 fixed in this class, written to a temporary file and moved into place only when it matches. The
 * download runs only when the user asks for it; HTTP goes through the application's {@code ConnectionService}.</p>
 */
public final class SaxonInstaller {

    private static final Logger logger = LogManager.getLogger(SaxonInstaller.class);

    /** The Saxon HE version this class installs. */
    public static final String VERSION = "13.1";
    /** The name of the processor {@link #processorConfig()} creates. */
    public static final String PROCESSOR_NAME = "Saxon HE " + VERSION;
    /** Where the jars come from. */
    public static final URI MAVEN_CENTRAL = URI.create("https://repo1.maven.org/maven2/");

    /**
     * One jar.
     *
     * @param path   path below the repository root
     * @param sha256 expected SHA-256 (hex)
     * @param size   expected size in bytes
     */
    record Artifact(String path, String sha256, long size) {
        String fileName() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    /** Saxon HE and the one library its command line needs (htmlparser and jline are optional). */
    static final List<Artifact> ARTIFACTS = List.of(
            new Artifact("net/sf/saxon/Saxon-HE/13.1/Saxon-HE-13.1.jar",
                    "91986ee8d80c4bafce10decfff01e618c00bd6368f0103da59b2b88b19cf75d3", 6_259_327),
            new Artifact("org/xmlresolver/xmlresolver/6.1.0/xmlresolver-6.1.0.jar",
                    "bf787a469cfc591e3a896dc8ef388c2c8bce11500cc7d37aed16113728382033", 181_738));

    /** Fetches one URL; a status other than 200 is an {@link IOException}. */
    @FunctionalInterface
    public interface Fetcher {
        byte[] fetch(URI uri) throws IOException;
    }

    private final Path directory;
    private final URI repository;
    private final Fetcher fetcher;

    /**
     * @param directory  where the jars go (created when needed)
     * @param repository the Maven repository root (ends with {@code /})
     * @param fetcher    fetches one URL
     */
    public SaxonInstaller(Path directory, URI repository, Fetcher fetcher) {
        this.directory = directory;
        this.repository = repository;
        this.fetcher = fetcher;
    }

    /** @return the installer of the application: {@code ~/.freeXmlToolkit/saxon/<version>/}, {@code ConnectionService} */
    public static SaxonInstaller standard() {
        return new SaxonInstaller(defaultDirectory(), MAVEN_CENTRAL, uri -> {
            var connection = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.ConnectionService.class);
            var response = connection.fetchBinary(uri);
            if (response.status() != 200) {
                throw new IOException("HTTP " + response.status() + " for " + uri);
            }
            return response.body();
        });
    }

    /** @return {@code ~/.freeXmlToolkit/saxon/<version>/} */
    public static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "saxon", VERSION);
    }

    /** @return the folder the jars are in */
    public Path directory() {
        return directory;
    }

    /** @return the bytes a full download takes */
    public static long downloadSize() {
        return ARTIFACTS.stream().mapToLong(Artifact::size).sum();
    }

    /** @return whether every jar is there with the expected size (the checksum was checked when it was written) */
    public boolean isInstalled() {
        for (Artifact artifact : ARTIFACTS) {
            Path file = directory.resolve(artifact.fileName());
            try {
                if (!Files.isRegularFile(file) || Files.size(file) != artifact.size()) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    /** @return the jars as a class path ({@link File#pathSeparator}) */
    public String classpath() {
        return classpath(directory);
    }

    /** @return the class path of the jars in a folder */
    static String classpath(Path directory) {
        return ARTIFACTS.stream().map(a -> directory.resolve(a.fileName()).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * Downloads the jars that are missing or wrong. A jar is written to a temporary file in the folder and
     * moved into place only when its SHA-256 matches; on failure or cancel nothing half-written stays.
     *
     * @throws IOException           when a download fails or a checksum does not match
     * @throws org.fxt.freexmltoolkit.service.progress.CancelledException when the progress is cancelled
     */
    public void install(Progress progress) throws IOException {
        Files.createDirectories(directory);
        long total = downloadSize();
        long done = 0;
        for (Artifact artifact : ARTIFACTS) {
            progress.checkCancelled();
            Path target = directory.resolve(artifact.fileName());
            if (!matches(target, artifact)) {
                progress.message("Downloading " + artifact.fileName());
                progress.progress(done, total);
                URI uri = repository.resolve(artifact.path());
                logger.info("Downloading {}", uri);
                byte[] bytes = fetcher.fetch(uri);
                progress.checkCancelled();
                String actual = sha256(bytes);
                if (!artifact.sha256().equalsIgnoreCase(actual)) {
                    throw new IOException("Checksum mismatch for " + artifact.fileName() + " (expected "
                            + artifact.sha256() + ", got " + actual + ")");
                }
                Path temp = Files.createTempFile(directory, "." + artifact.fileName(), ".part");
                try {
                    Files.write(temp, bytes);
                    move(temp, target);
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            done += artifact.size();
            progress.progress(done, total);
        }
        progress.message("Saxon HE " + VERSION + " installed");
    }

    /** @return a new external processor that runs this Saxon with the application's {@code java} */
    public XsltProcessorConfig processorConfig() {
        return new XsltProcessorConfig(null, PROCESSOR_NAME, XsltProcessorPreset.SAXON_HE, "",
                arguments(classpath()), XsltProcessorPreset.SAXON_HE.parameters(), "",
                XsltProcessorConfig.DEFAULT_TIMEOUT_SECONDS);
    }

    /** @return the {@link XsltProcessorPreset#SAXON_HE} argument template for a class path */
    static String arguments(String classpath) {
        return "-cp \"" + classpath + "\" net.sf.saxon.Transform -s:${xml} -xsl:${xsl} -o:${out} ${params}";
    }

    private static boolean matches(Path file, Artifact artifact) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == artifact.size()
                    && artifact.sha256().equalsIgnoreCase(sha256(Files.readAllBytes(file)));
        } catch (IOException e) {
            return false;
        }
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
