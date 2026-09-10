package org.fxt.freexmltoolkit.service.sampleaudit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result data of the real-world sample XML audit. Serialized with Gson into
 * {@code build/sample-xml-audit/<folder>/schema.json} and {@code samples.jsonl}.
 */
final class AuditModels {

    private AuditModels() {
    }

    /** Outcome of one generated sample. */
    enum SampleStatus {
        /** Well-formed and valid against the reference schema. */
        VALID,
        /** Well-formed but with at least one schema validation error. */
        INVALID,
        /** Not well-formed XML. */
        NOT_WELL_FORMED,
        /** Well-formed, but the reference schema does not compile, so it could not be validated. */
        NOT_VALIDATED,
        /** The generator returned an error text instead of XML. */
        GENERATOR_ERROR,
        /** The generator threw. */
        GENERATOR_EXCEPTION,
        /** The generator did not finish within the sample time budget. */
        TIMEOUT,
        /** The sample was generated, but validating it did not finish within the validation time budget. */
        VALIDATION_TIMEOUT,
        /** Not run (root cap or aborted after stuck generator threads). */
        SKIPPED
    }

    /** A validation or schema compilation message. */
    record Issue(String severity, String key, String message, int line, int column, String elementPath,
                 String snippet) {
    }

    /** A global element declaration of the compiled reference schema (all namespaces). */
    record GlobalElement(String namespace, String name, boolean isAbstract) {
    }

    /**
     * One generated sample.
     *
     * @param rootSet   {@code first} (as the app does) or {@code all} (breadth test)
     * @param generator {@code plain} or {@code realistic}
     * @param mode      {@code required} (mandatory only) or {@code optional}
     */
    record SampleResult(String folder, String rootSet, String generator, String mode, String root,
                        SampleStatus status, long millis, long chars, boolean expectedInvalid,
                        int errorCount, int warningCount, Map<String, Integer> errorKeys,
                        List<Issue> issues, String failure, String file) {
    }

    /** Per-schema result, filled incrementally by the worker and completed by the parent. */
    static final class SchemaResult {
        String folder;
        String main;
        String xsdVersion;
        String targetNamespace;
        /** Last completed worker phase; {@code done} when the worker finished normally. */
        String phase;

        String compileStatus;
        long compileMillis;
        List<Issue> compileErrors = new ArrayList<>();
        int compileWarnings;
        List<String> blockedRemote = new ArrayList<>();
        List<String> missingLocal = new ArrayList<>();
        List<String> skippedDtds = new ArrayList<>();
        Map<String, String> catalogErrors = new LinkedHashMap<>();
        /** Well-known schemas from the folder's {@code local/} directory, registered by namespace. */
        List<String> localNamespaceEntries = new ArrayList<>();
        List<GlobalElement> globalElements = new ArrayList<>();

        List<String> appRoots = new ArrayList<>();
        long plainProcessMillis = -1;
        String plainProcessFailure;
        long realisticProcessMillis = -1;
        String realisticProcessFailure;
        int rootsSkipped;
        int stuckGeneratorThreads;
        int cacheEntriesAfterRun;

        String workerStatus;
        int workerExitCode;
        long workerMillis;
    }
}
