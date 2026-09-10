package org.fxt.freexmltoolkit.service.sampleaudit;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import javax.xml.validation.Schema;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SampleXmlLimits;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.GlobalElement;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.SampleResult;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.SampleStatus;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.SchemaResult;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Worker JVM of the real-world sample XML audit: audits ONE schema and writes {@code schema.json}
 * and {@code samples.jsonl} into its output directory. Launched by
 * {@link SampleXmlGeneratorRealWorldAuditTest}; it runs outside JUnit so that a runaway generator
 * (endless processing, OutOfMemoryError) only takes down this schema's audit.
 *
 * <p>Arguments: {@code corpusDir folder mainRelativePath xsdVersion targetNamespace|- outputDir}.</p>
 */
public final class SampleXmlAuditWorker {

    static final int MAX_OCCURRENCES = 2;
    private static final long LARGE_SAMPLE_CHARS = 2_000_000;
    private static final int MAX_STUCK_THREADS = 3;
    private static final String[] GENERATORS = {"plain", "realistic"};
    private static final boolean[] MANDATORY_ONLY = {true, false};
    private static final String EMPTY_LIBRARY_BUNDLE = "{\"version\":1,\"entries\":[]}";

    static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private final String folder;
    private final Path folderDir;
    private final Path mainXsd;
    private final boolean xsd11;
    private final Path outDir;
    private final long sampleTimeoutSeconds = Long.getLong("sample.audit.sampleTimeoutSeconds", 120);
    private final long validationTimeoutSeconds = Long.getLong("sample.audit.validationTimeoutSeconds", 120);
    private final long processTimeoutSeconds = 60 * Long.getLong("sample.audit.processTimeoutMinutes", 15);
    private final int maxRoots = Integer.getInteger("sample.audit.maxRootsPerSchema", Integer.MAX_VALUE);

    private final SchemaResult result = new SchemaResult();
    private final Map<String, int[]> tally = new TreeMap<>();
    private Map<String, Boolean> abstractByName = Map.of();
    private Schema schema;
    private int stuckThreads;

    private SampleXmlAuditWorker(String[] args) {
        Path corpusDir = Path.of(args[0]);
        this.folder = args[1];
        this.folderDir = corpusDir.resolve(folder);
        this.mainXsd = corpusDir.resolve(args[2]).toAbsolutePath().normalize();
        this.xsd11 = "1.1".equals(args[3]);
        this.outDir = Path.of(args[5]);
        result.folder = folder;
        result.main = args[2];
        result.xsdVersion = args[3];
        result.targetNamespace = "-".equals(args[4]) ? null : args[4];
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            new SampleXmlAuditWorker(args).run();
        } catch (Throwable t) {
            t.printStackTrace();
            exitCode = 2;
        }
        System.exit(exitCode); // also ends generator threads that ignored cancellation
    }

    private void run() throws Exception {
        Files.createDirectories(outDir);
        Files.deleteIfExists(samplesFile());
        Path work = outDir.resolve("work");
        phase("start");

        // Isolated, offline Schema Library backed by the folder's OASIS catalog. The generator resolves
        // remote imports through SchemaLibraryServiceImpl.shared() / SchemaResourceCache.shared(), which
        // both prefer these registered instances.
        ServiceRegistry.reset();
        SchemaResourceCache cache = new SchemaResourceCache(work.resolve("cache"));
        ServiceRegistry.register(SchemaResourceCache.class, cache);
        SchemaLibraryServiceImpl library = new SchemaLibraryServiceImpl(work.resolve("schema-library.json"), cache,
                () -> new ByteArrayInputStream(EMPTY_LIBRARY_BUNDLE.getBytes(UTF_8)));
        Path catalog = folderDir.resolve("catalog.xml");
        if (Files.isRegularFile(catalog)) {
            library.addCatalog(catalog);
        }
        if (library.catalogErrors() != null) {
            result.catalogErrors.putAll(library.catalogErrors());
        }
        registerLocalWellKnownSchemas(library);
        ServiceRegistry.register(SchemaLibraryService.class, library);

        compileReferenceSchema(library);
        phase("compiled");
        runFirstElement();
        phase("first");
        runBreadthPlain();
        phase("all-plain");
        runBreadthRealistic();
        phase("all-realistic");

        result.cacheEntriesAfterRun = cache.listEntries().size();
        result.stuckGeneratorThreads = stuckThreads;
        phase("done");
        tally.forEach((combo, counts) -> {
            StringBuilder line = new StringBuilder(folder).append(' ').append(combo);
            for (SampleStatus status : SampleStatus.values()) {
                if (counts[status.ordinal()] > 0) {
                    line.append(' ').append(status).append('=').append(counts[status.ordinal()]);
                }
            }
            System.out.println("[sample-audit] " + line);
        });
    }

    /**
     * Registers the folder's {@code local/*.xsd} copies of well-known W3C schemas (xml.xsd, xlink.xsd, …)
     * as namespace entries: the offline stand-in for the app's bundled Schema Library, whose entries for
     * these namespaces point at w3.org and would be downloaded.
     */
    private void registerLocalWellKnownSchemas(SchemaLibraryServiceImpl library) throws IOException {
        Path localDir = folderDir.resolve("local");
        if (!Files.isDirectory(localDir)) {
            return;
        }
        Pattern targetNamespace = Pattern.compile("targetNamespace\\s*=\\s*[\"']([^\"']+)[\"']");
        try (Stream<Path> files = Files.list(localDir)) {
            for (Path xsd : files.filter(p -> p.toString().endsWith(".xsd")).sorted().toList()) {
                Matcher matcher = targetNamespace.matcher(Files.readString(xsd, java.nio.charset.StandardCharsets.ISO_8859_1));
                if (matcher.find()) {
                    library.addEntry(SchemaLibraryEntry.user(matcher.group(1), xsd.toAbsolutePath().toString(),
                            SchemaKind.XSD, "", null));
                    result.localNamespaceEntries.add(matcher.group(1) + " -> " + folderDir.relativize(xsd));
                }
            }
        }
    }

    private void compileReferenceSchema(SchemaLibraryService library) {
        AuditResourceResolver resolver = new AuditResourceResolver(library);
        AuditSchemaCompiler.Compiled compiled = AuditSchemaCompiler.compile(mainXsd, xsd11, resolver);
        result.compileMillis = compiled.millis();
        result.compileStatus = compiled.ok() ? "OK" : "FAILED";
        result.compileErrors = new ArrayList<>(compiled.errors());
        result.compileWarnings = compiled.warningCount();
        result.globalElements = new ArrayList<>(compiled.globals());
        result.blockedRemote = resolver.blockedRemote();
        result.missingLocal = resolver.missingLocal();
        result.skippedDtds = resolver.skippedDtds();
        schema = compiled.ok() ? compiled.schema() : null;

        Map<String, Boolean> abstracts = new HashMap<>();
        String targetNamespace = Objects.requireNonNullElse(result.targetNamespace, "");
        for (GlobalElement global : compiled.globals()) {
            if (targetNamespace.equals(Objects.requireNonNullElse(global.namespace(), ""))) {
                abstracts.put(global.name(), global.isAbstract());
            }
        }
        abstractByName = abstracts;
    }

    /** The four samples the app itself produces today (first global element). */
    private void runFirstElement() throws IOException {
        for (String generator : GENERATORS) {
            for (boolean mandatoryOnly : MANDATORY_ONLY) {
                boolean realistic = "realistic".equals(generator);
                Timed<String> call = timed(() -> SampleXmlRunner.generate(mainXsd.toFile(), mandatoryOnly,
                        MAX_OCCURRENCES, realistic), processTimeoutSeconds + sampleTimeoutSeconds);
                record("first", generator, mandatoryOnly, null, call);
            }
        }
    }

    private void runBreadthPlain() throws IOException {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(mainXsd.toString());
        Timed<List<String>> processed = timed(() -> {
            service.loadSchema(XsdDocumentationService.MarkdownMode.OFF);
            return service.getRootElementNames();
        }, processTimeoutSeconds);
        result.plainProcessMillis = processed.millis();
        if (processed.value() == null) {
            result.plainProcessFailure = describe(processed);
            return;
        }
        result.appRoots = new ArrayList<>(processed.value());
        for (String root : capRoots(processed.value())) {
            for (boolean mandatoryOnly : MANDATORY_ONLY) {
                if (stuckThreads >= MAX_STUCK_THREADS) {
                    recordSkipped("plain", mandatoryOnly, root);
                    continue;
                }
                record("all", "plain", mandatoryOnly, root,
                        timed(() -> service.generateSampleXml(root, mandatoryOnly, MAX_OCCURRENCES), sampleTimeoutSeconds));
            }
        }
    }

    private void runBreadthRealistic() throws IOException {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(mainXsd.toString());
        Timed<List<String>> processed = timed(() -> {
            service.loadSchema(XsdDocumentationService.MarkdownMode.ALL);
            return service.getRootElementNames();
        }, processTimeoutSeconds);
        result.realisticProcessMillis = processed.millis();
        if (processed.value() == null) {
            result.realisticProcessFailure = describe(processed);
            return;
        }
        ProfiledXmlGeneratorService generator = new ProfiledXmlGeneratorService();
        for (String root : capRoots(processed.value())) {
            for (boolean mandatoryOnly : MANDATORY_ONLY) {
                if (stuckThreads >= MAX_STUCK_THREADS) {
                    recordSkipped("realistic", mandatoryOnly, root);
                    continue;
                }
                GenerationProfile profile = new GenerationProfile("Realistic");
                profile.setMandatoryOnly(mandatoryOnly);
                profile.setMaxOccurrences(MAX_OCCURRENCES);
                record("all", "realistic", mandatoryOnly, root, timed(() -> {
                    try {
                        service.expandForSample(root, mandatoryOnly, XsdDocumentationService.MarkdownMode.ALL);
                    } catch (SampleXmlLimits.LimitExceededException e) {
                        return e.toXmlComment();
                    }
                    return generator.generateRealistic(profile, service.xsdDocumentationData, mainXsd.toString(), root);
                }, sampleTimeoutSeconds));
            }
        }
    }

    private List<String> capRoots(List<String> roots) {
        if (roots.size() <= maxRoots) {
            return roots;
        }
        result.rootsSkipped = roots.size() - maxRoots;
        return roots.subList(0, maxRoots);
    }

    private void record(String rootSet, String generator, boolean mandatoryOnly, String root, Timed<String> call)
            throws IOException {
        String mode = mandatoryOnly ? "required" : "optional";
        SampleStatus status;
        String failure = null;
        String file = null;
        long chars = 0;
        AuditSchemaCompiler.Validation validation = null;

        if (call.timedOut()) {
            status = SampleStatus.TIMEOUT;
            failure = "no result after " + call.millis() + " ms";
        } else if (call.error() != null) {
            status = SampleStatus.GENERATOR_EXCEPTION;
            failure = describe(call.error());
        } else {
            String xml = Objects.requireNonNullElse(call.value(), "");
            chars = xml.length();
            String head = xml.stripLeading();
            if (head.isEmpty() || head.startsWith("ERROR:") || head.startsWith("<!--")) {
                status = SampleStatus.GENERATOR_ERROR;
                failure = head.length() > 500 ? head.substring(0, 500) : head;
            } else {
                // Validation runs under a time budget: Xerces pattern matching can backtrack for a very long time on a
                // generated value (KSeF FA(3) blocked a worker for over 30 minutes)
                String sampleXml = xml;
                Timed<AuditSchemaCompiler.Validation> validated =
                        timed(() -> AuditSchemaCompiler.validate(schema, sampleXml), validationTimeoutSeconds);
                validation = validated.value();
                if (validation == null) {
                    status = SampleStatus.VALIDATION_TIMEOUT;
                    failure = validated.timedOut()
                            ? "validation did not finish within " + validationTimeoutSeconds + " s"
                            : describe(validated.error());
                    file = writeSample(rootSet, generator, mode, root, xml);
                } else {
                    if (root == null) {
                        root = localName(validation.rootElement());
                    }
                    file = writeSample(rootSet, generator, mode, root, xml);
                    if (!validation.wellFormed()) {
                        status = SampleStatus.NOT_WELL_FORMED;
                    } else if (schema == null) {
                        status = SampleStatus.NOT_VALIDATED;
                    } else {
                        status = validation.errorCount() == 0 ? SampleStatus.VALID : SampleStatus.INVALID;
                    }
                }
            }
        }
        boolean expectedInvalid = root != null && abstractByName.getOrDefault(root, false);
        append(new SampleResult(folder, rootSet, generator, mode, root, status, call.millis(), chars, expectedInvalid,
                validation == null ? 0 : validation.errorCount(),
                validation == null ? 0 : validation.warningCount(),
                validation == null ? Map.of() : validation.errorKeys(),
                validation == null ? List.of() : validation.issues(),
                failure, file));
    }

    private void recordSkipped(String generator, boolean mandatoryOnly, String root) throws IOException {
        append(new SampleResult(folder, "all", generator, mandatoryOnly ? "required" : "optional", root,
                SampleStatus.SKIPPED, 0, 0, abstractByName.getOrDefault(root, false), 0, 0, Map.of(), List.of(),
                "aborted after " + stuckThreads + " stuck generator threads", null));
    }

    private void append(SampleResult sample) throws IOException {
        // getBytes: issue messages and snippets may carry unpaired surrogates from generated values
        Files.write(samplesFile(), (COMPACT.toJson(sample) + "\n").getBytes(UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        String combo = sample.rootSet() + "/" + sample.generator() + "/" + sample.mode();
        tally.computeIfAbsent(combo, k -> new int[SampleStatus.values().length])[sample.status().ordinal()]++;
    }

    private String writeSample(String rootSet, String generator, String mode, String root, String xml)
            throws IOException {
        Path dir = outDir.resolve(rootSet).resolve(generator).resolve(mode);
        Files.createDirectories(dir);
        String baseName = "first".equals(rootSet) ? "first" : sanitize(root);
        if (xml.length() <= LARGE_SAMPLE_CHARS) {
            Path file = dir.resolve(baseName + ".xml");
            // getBytes replaces unpaired surrogates (Generex can produce them) instead of throwing like writeString
            Files.write(file, xml.getBytes(UTF_8));
            return outDir.relativize(file).toString();
        }
        Path file = dir.resolve(baseName + ".xml.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file));
             Writer writer = new java.io.OutputStreamWriter(out, UTF_8)) {
            writer.write(xml);
        }
        return outDir.relativize(file).toString();
    }

    private void phase(String phase) throws IOException {
        result.phase = phase;
        Path tmp = outDir.resolve("schema.json.tmp");
        Files.write(tmp, PRETTY.toJson(result).getBytes(UTF_8));
        Files.move(tmp, outDir.resolve("schema.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path samplesFile() {
        return outDir.resolve("samples.jsonl");
    }

    private <T> Timed<T> timed(Callable<T> task, long timeoutSeconds) {
        FutureTask<T> future = new FutureTask<>(task);
        Thread thread = new Thread(null, future, "sample-audit-generator", 256L * 1024 * 1024);
        thread.setDaemon(true);
        long start = System.nanoTime();
        thread.start();
        try {
            return new Timed<>(future.get(timeoutSeconds, TimeUnit.SECONDS), null, false, millisSince(start));
        } catch (TimeoutException e) {
            future.cancel(true);
            try {
                thread.join(5_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                stuckThreads++;
            }
            return new Timed<>(null, null, true, millisSince(start));
        } catch (ExecutionException e) {
            return new Timed<>(null, e.getCause(), false, millisSince(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Timed<>(null, e, false, millisSince(start));
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String describe(Timed<?> call) {
        return call.timedOut() ? "timeout after " + call.millis() + " ms" : describe(call.error());
    }

    /** Exception class, message and the first application stack frame (root-cause hint). */
    private static String describe(Throwable error) {
        if (error == null) {
            return "no result";
        }
        StringBuilder sb = new StringBuilder(error.getClass().getName()).append(": ").append(error.getMessage());
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("org.fxt.")) {
                sb.append(" at ").append(frame);
                break;
            }
        }
        return sb.toString();
    }

    private static String localName(String qName) {
        if (qName == null) {
            return null;
        }
        int colon = qName.indexOf(':');
        return colon < 0 ? qName : qName.substring(colon + 1);
    }

    private static String sanitize(String name) {
        return name == null ? "unnamed" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    record Timed<T>(T value, Throwable error, boolean timedOut, long millis) {
    }
}
