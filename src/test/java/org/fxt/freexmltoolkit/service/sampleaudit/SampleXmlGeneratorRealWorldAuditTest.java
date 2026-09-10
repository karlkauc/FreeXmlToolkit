package org.fxt.freexmltoolkit.service.sampleaudit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.SampleResult;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.SampleStatus;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.SchemaResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Non-gating audit of the sample XML generator against the real-world XSD corpus in
 * {@code src/test/resources/xsd/real-world}. For every schema a {@link SampleXmlAuditWorker} JVM
 * generates samples (first global element as the app does, plus every app-visible root; plain and
 * realistic generator; mandatory-only and with optional elements), validates them offline against the
 * schema and records the outcome. Results: {@code build/sample-xml-audit/results.json},
 * {@code summary.csv} and the samples themselves.
 *
 * <p>Runs only via {@code ./gradlew sampleXmlAudit}. It fails only on harness problems, never because a
 * generated sample is invalid.</p>
 */
@EnabledIfSystemProperty(named = "sample.audit", matches = "true")
class SampleXmlGeneratorRealWorldAuditTest {

    private static final Path CORPUS = Path.of("src/test/resources/xsd/real-world");
    private static final Path OUT = Path.of("build/sample-xml-audit");
    private static final String QUIET_LOG4J = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
              <Appenders>
                <Console name="Console" target="SYSTEM_OUT">
                  <PatternLayout pattern="%d{HH:mm:ss} %-5level %c{1} - %msg%n"/>
                </Console>
              </Appenders>
              <Loggers>
                <Root level="WARN"><AppenderRef ref="Console"/></Root>
              </Loggers>
            </Configuration>
            """;

    @Test
    void auditRealWorldCorpus() throws Exception {
        Path manifest = CORPUS.resolve("manifest.json");
        Assumptions.assumeTrue(Files.isRegularFile(manifest), "real-world XSD corpus not present");
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("log4j2-audit.xml"), QUIET_LOG4J, UTF_8);

        List<JsonObject> entries = selectEntries(manifest);
        int parallel = Integer.getInteger("sample.audit.parallel", 2);
        System.out.printf("[sample-audit] %d schemas, %d worker(s) in parallel%n", entries.size(), parallel);

        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        List<Future<String>> runs = new ArrayList<>();
        for (JsonObject entry : entries) {
            runs.add(pool.submit(() -> runWorker(entry)));
        }
        List<String> harnessErrors = new ArrayList<>();
        for (Future<String> run : runs) {
            String error = run.get();
            if (error != null) {
                harnessErrors.add(error);
            }
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);

        aggregate();
        assertTrue(harnessErrors.isEmpty(), "Harness problems:\n" + String.join("\n", harnessErrors));
    }

    private static List<JsonObject> selectEntries(Path manifest) throws IOException {
        Set<String> only = Arrays.stream(System.getProperty("sample.audit.only", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<JsonObject> entries = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(Files.readString(manifest, UTF_8)).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("folder") || !entry.has("main")) {
                continue; // URLs that were not exported as a schema
            }
            if (only.isEmpty() || only.contains(entry.get("folder").getAsString())) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong(e -> folderSize(e.get("folder").getAsString())));
        return entries;
    }

    private static long folderSize(String folder) {
        try (Stream<Path> files = Files.walk(CORPUS.resolve(folder))) {
            return files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    /** Runs one schema's worker JVM; returns a harness error description or {@code null}. */
    private static String runWorker(JsonObject entry) throws Exception {
        String folder = entry.get("folder").getAsString();
        Path dir = OUT.resolve(folder).toAbsolutePath();
        deleteRecursively(dir);
        Files.createDirectories(dir);

        String targetNamespace = entry.has("target_namespace") && !entry.get("target_namespace").isJsonNull()
                ? entry.get("target_namespace").getAsString() : "-";
        String xsdVersion = entry.has("xsd_version") ? entry.get("xsd_version").getAsString() : "1.0";

        List<String> command = new ArrayList<>(List.of(javaBinary(), "--enable-preview",
                "-Xmx" + System.getProperty("sample.audit.workerHeap", "6g"), "-Xss16m",
                "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true",
                "-Dlog4j2.configurationFile=" + OUT.resolve("log4j2-audit.xml").toAbsolutePath()));
        for (String key : List.of("user.home", "fxt.properties.file", "fxt.schema.namespaceFallback",
                "fxt.suppressErrorDialogs", "sample.audit.sampleTimeoutSeconds", "sample.audit.processTimeoutMinutes",
                "sample.audit.maxRootsPerSchema")) {
            String value = System.getProperty(key);
            if (value != null) {
                command.add("-D" + key + "=" + value);
            }
        }
        command.add("-cp");
        command.add(System.getProperty("sample.audit.classpath", System.getProperty("java.class.path")));
        command.add(SampleXmlAuditWorker.class.getName());
        command.addAll(List.of(CORPUS.toAbsolutePath().toString(), folder, entry.get("main").getAsString(),
                xsdVersion, targetNamespace, dir.toString()));

        long timeoutMinutes = Long.getLong("sample.audit.schemaTimeoutMinutes", 45);
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(dir.resolve("worker.log").toFile())
                .start();
        String workerStatus;
        int exitCode = -1;
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            workerStatus = "TIMEOUT";
        } else {
            exitCode = process.exitValue();
            if (exitCode == 0) {
                workerStatus = "OK";
            } else if (Files.readString(dir.resolve("worker.log"), UTF_8).contains("java.lang.OutOfMemoryError")) {
                workerStatus = "OOM";
            } else {
                workerStatus = "CRASH";
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;

        Path schemaJson = dir.resolve("schema.json");
        SchemaResult result;
        if (Files.isRegularFile(schemaJson)) {
            result = SampleXmlAuditWorker.PRETTY.fromJson(Files.readString(schemaJson, UTF_8), SchemaResult.class);
        } else {
            result = new SchemaResult();
            result.folder = folder;
            result.main = entry.get("main").getAsString();
            result.xsdVersion = xsdVersion;
            result.phase = "not-started";
        }
        result.workerStatus = workerStatus;
        result.workerExitCode = exitCode;
        result.workerMillis = millis;
        Files.writeString(schemaJson, SampleXmlAuditWorker.PRETTY.toJson(result), UTF_8);
        System.out.printf("[sample-audit] %-60s worker=%s phase=%s compile=%s %d s%n",
                folder, workerStatus, result.phase, result.compileStatus, millis / 1000);

        if ("CRASH".equals(workerStatus)) {
            return folder + ": worker crashed (exit " + exitCode + "), see " + dir.resolve("worker.log");
        }
        if ("OK".equals(workerStatus) && !"done".equals(result.phase)) {
            return folder + ": worker exited normally in phase " + result.phase;
        }
        return null;
    }

    /** Merges every schema.json / samples.jsonl under the output directory into results.json and summary.csv. */
    private static void aggregate() throws IOException {
        List<Path> dirs;
        try (Stream<Path> list = Files.list(OUT)) {
            dirs = list.filter(d -> Files.isRegularFile(d.resolve("schema.json"))).sorted().toList();
        }
        JsonArray results = new JsonArray();
        List<String> csv = new ArrayList<>();
        csv.add("folder,rootSet,generator,mode,total," + Arrays.stream(SampleStatus.values())
                .map(s -> s.name().toLowerCase()).collect(Collectors.joining(",")) + ",expectedInvalid,compileStatus,workerStatus");
        StringBuilder table = new StringBuilder();

        for (Path dir : dirs) {
            SchemaResult schema = SampleXmlAuditWorker.PRETTY.fromJson(
                    Files.readString(dir.resolve("schema.json"), UTF_8), SchemaResult.class);
            List<SampleResult> samples = readSamples(dir.resolve("samples.jsonl"));

            Map<String, Map<SampleStatus, Integer>> counts = new TreeMap<>();
            Map<String, Integer> expectedInvalid = new TreeMap<>();
            Map<String, Map<String, Integer>> errorKeySamples = new TreeMap<>();
            for (SampleResult sample : samples) {
                String combo = sample.rootSet() + "/" + sample.generator() + "/" + sample.mode();
                counts.computeIfAbsent(combo, k -> new EnumMap<>(SampleStatus.class)).merge(sample.status(), 1, Integer::sum);
                if (sample.expectedInvalid()) {
                    expectedInvalid.merge(combo, 1, Integer::sum);
                }
                for (String key : sample.errorKeys().keySet()) {
                    errorKeySamples.computeIfAbsent(combo, k -> new TreeMap<>()).merge(key, 1, Integer::sum);
                }
            }

            JsonObject entry = new JsonObject();
            entry.add("schema", SampleXmlAuditWorker.PRETTY.toJsonTree(schema));
            entry.add("counts", SampleXmlAuditWorker.PRETTY.toJsonTree(counts));
            entry.add("expectedInvalid", SampleXmlAuditWorker.PRETTY.toJsonTree(expectedInvalid));
            entry.add("errorKeySamples", SampleXmlAuditWorker.PRETTY.toJsonTree(errorKeySamples));
            results.add(entry);

            StringBuilder row = new StringBuilder(String.format("%-52s %-6s %-7s",
                    shorten(schema.folder, 52), schema.compileStatus, schema.workerStatus));
            for (var combo : counts.entrySet()) {
                Map<SampleStatus, Integer> c = combo.getValue();
                int total = c.values().stream().mapToInt(Integer::intValue).sum();
                csv.add(String.join(",", schema.folder, combo.getKey().replace('/', ','), String.valueOf(total),
                        Arrays.stream(SampleStatus.values()).map(s -> String.valueOf(c.getOrDefault(s, 0)))
                                .collect(Collectors.joining(",")),
                        String.valueOf(expectedInvalid.getOrDefault(combo.getKey(), 0)),
                        String.valueOf(schema.compileStatus), String.valueOf(schema.workerStatus)));
                row.append(String.format(" %s=%d/%d", combo.getKey().replace("/required", "/req").replace("/optional", "/opt"),
                        c.getOrDefault(SampleStatus.VALID, 0), total));
            }
            table.append(row).append('\n');
        }
        Files.writeString(OUT.resolve("results.json"), SampleXmlAuditWorker.PRETTY.toJson(results), UTF_8);
        Files.write(OUT.resolve("summary.csv"), csv, UTF_8);
        System.out.println("[sample-audit] valid/total per rootSet/generator/mode:\n" + table);
    }

    private static List<SampleResult> readSamples(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        List<SampleResult> samples = new ArrayList<>();
        for (String line : Files.readAllLines(file, UTF_8)) {
            if (!line.isBlank()) {
                samples.add(SampleXmlAuditWorker.COMPACT.fromJson(line, SampleResult.class));
            }
        }
        return samples;
    }

    private static String javaBinary() {
        return ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    private static String shorten(String text, int max) {
        return text == null || text.length() <= max ? String.valueOf(text) : text.substring(0, max - 1) + "…";
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
