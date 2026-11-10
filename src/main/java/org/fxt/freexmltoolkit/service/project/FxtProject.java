package org.fxt.freexmltoolkit.service.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * A project file ({@code .fxtproj}, JSON): the folders that belong to it, which schema, Schematron
 * and transformation scenario belong to which files, the XML catalogs to use, and named
 * transformation scenarios. All paths in the file are relative to the folder of the project file,
 * so a project can be committed with the files it describes and opened on another computer.
 *
 * <pre>{@code
 * {
 *   "name": "Orders",
 *   "folders": ["."],
 *   "schemaBindings": [ { "files": "data/**\/*.xml", "schema": "schemas/order.xsd",
 *                         "schematron": "rules/order.sch", "scenario": "Report" } ],
 *   "catalogs": ["schemas/catalog.xml"],
 *   "scenarios": [ { "name": "Report", "stylesheet": "xsl/report.xsl", "input": "data/order.xml",
 *                    "parameters": { "year": "2026" }, "outputFormat": "HTML", "output": "out/report.html" } ]
 * }
 * }</pre>
 */
public final class FxtProject {

    /** The file extension of project files. */
    public static final String EXTENSION = "fxtproj";

    /**
     * What belongs to the files whose project-relative path matches a glob ({@code data/**\/*.xml}):
     * a grammar ({@code schema}: XSD, DTD, RELAX NG, or a JSON Schema for JSON files), a Schematron
     * and the name of a transformation scenario. Each of the three is optional.
     */
    public static final class SchemaBinding {
        public String files;
        public String schema;
        public String schematron;
        public String scenario;

        public SchemaBinding() {
        }

        public SchemaBinding(String files, String schema) {
            this.files = files;
            this.schema = schema;
        }

        public SchemaBinding(String files, String schema, String schematron, String scenario) {
            this.files = files;
            this.schema = blankToNull(schema);
            this.schematron = blankToNull(schematron);
            this.scenario = blankToNull(scenario);
        }

        /** @return a copy (the dialogs edit copies) */
        public SchemaBinding copy() {
            return new SchemaBinding(files, schema, schematron, scenario);
        }

        /** @return whether the binding assigns anything */
        public boolean isEmpty() {
            return blankToNull(schema) == null && blankToNull(schematron) == null && blankToNull(scenario) == null;
        }
    }

    /**
     * What the project assigns to one file: each part from the first binding that matches the file
     * and names it.
     *
     * @param schema     the grammar (XSD, DTD, RELAX NG, JSON Schema), or {@code null}
     * @param schematron the Schematron, or {@code null}
     * @param scenario   the name of the transformation scenario, or {@code null}
     */
    public record Assignment(Path schema, Path schematron, String scenario) {

        /** Nothing assigned. */
        public static final Assignment NONE = new Assignment(null, null, null);

        public boolean isEmpty() {
            return schema == null && schematron == null && scenario == null;
        }
    }

    /** In a scenario's input, parameters and output: the file the scenario runs for (project-relative). */
    public static final String VAR_FILE = "${file}";
    /** In a scenario's output: the name of that file without its extension. */
    public static final String VAR_NAME = "${name}";
    /** In a scenario's output: the project-relative folder of that file ({@code .} at the top). */
    public static final String VAR_DIR = "${dir}";

    /**
     * A transformation scenario.
     *
     * <p>{@code stylesheet} is an XSLT ({@code .xsl}, {@code .xslt}), XQuery ({@code .xq}, …) or XProc
     * ({@code .xpl}) file; {@code input} the document (optional for XQuery and XProc); {@code output}
     * where the result is written (optional: the result is shown instead); {@code outputFormat} one
     * of XML, HTML, XHTML, TEXT, JSON (optional: from the stylesheet's {@code xsl:output});
     * {@code expected} the result it must produce (optional; a regression check compares against it,
     * {@code ${name}} / {@code ${dir}} / {@code ${file}} as in the output), {@code compare} how
     * (optional, see {@link CompareSettings}) and, for PDF, {@code processor} the XSL-FO processor
     * (optional: the default of the settings; {@code builtin} or the name of an external processor
     * configured in the settings — a project file names a processor, it never carries a command line).
     */
    public static final class Scenario {
        public String name;
        public String stylesheet;
        public String input;
        public Map<String, String> parameters = new LinkedHashMap<>();
        public String outputFormat;
        public String output;
        public String expected;
        public CompareSettings compare;
        public String processor;

        public Scenario() {
        }

        public Scenario(String name, String stylesheet, String input, Map<String, String> parameters,
                        String outputFormat, String output) {
            this.name = name;
            this.stylesheet = stylesheet;
            this.input = input;
            this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
            this.outputFormat = outputFormat;
            this.output = output;
        }

        /** @return a copy (the dialogs edit copies) */
        public Scenario copy() {
            Scenario copy = new Scenario(name, stylesheet, input, parameters, outputFormat, output);
            copy.expected = expected;
            copy.compare = compare == null ? null : compare.copy();
            copy.processor = processor;
            return copy;
        }

        /** @return whether a regression check can run: an expected result is named */
        public boolean hasExpected() {
            return expected != null && !expected.isBlank();
        }

        /**
         * @return whether the scenario takes the file it runs for: its input is empty or contains
         *         {@value FxtProject#VAR_FILE}
         */
        public boolean takesFile() {
            return input == null || input.isBlank() || input.contains(VAR_FILE);
        }

        /** @return whether the result is a PDF (the stylesheet produces XSL-FO, FOP renders it) */
        public boolean isPdf() {
            return outputFormat != null && outputFormat.strip().equalsIgnoreCase("PDF");
        }
    }

    /**
     * How a scenario's result is compared with its expected result (all optional). XML results are
     * compared by structure ({@code XmlStructuralDiff}): {@code ignore} XPath 1.0 expressions,
     * a numeric {@code tolerance}, {@code ignoreOrder}, {@code keys} that pair elements,
     * {@code placeholders} ({@code ${xmlunit.…}} in the expected file, on unless false),
     * {@code ignoreComments}, {@code exactWhitespace}.
     */
    public static final class CompareSettings {
        public List<String> ignore = new ArrayList<>();
        public Double tolerance;
        public Boolean ignoreOrder;
        public List<String> keys = new ArrayList<>();
        public Boolean placeholders;
        public Boolean ignoreComments;
        public Boolean exactWhitespace;

        public CompareSettings copy() {
            CompareSettings copy = new CompareSettings();
            copy.ignore = ignore == null ? new ArrayList<>() : new ArrayList<>(ignore);
            copy.tolerance = tolerance;
            copy.ignoreOrder = ignoreOrder;
            copy.keys = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
            copy.placeholders = placeholders;
            copy.ignoreComments = ignoreComments;
            copy.exactWhitespace = exactWhitespace;
            return copy;
        }

        /** @return whether nothing differs from the defaults (then it is not written) */
        public boolean isDefault() {
            return (ignore == null || ignore.isEmpty()) && (tolerance == null || tolerance == 0)
                    && !Boolean.TRUE.equals(ignoreOrder) && (keys == null || keys.isEmpty())
                    && !Boolean.FALSE.equals(placeholders) && !Boolean.TRUE.equals(ignoreComments)
                    && !Boolean.TRUE.equals(exactWhitespace);
        }

        /** @return the structural comparison options these settings describe ({@code null} = defaults) */
        public static org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options toOptions(CompareSettings settings) {
            if (settings == null) {
                return org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options.DEFAULTS;
            }
            return new org.fxt.freexmltoolkit.service.XmlStructuralDiff.Options(
                    !Boolean.TRUE.equals(settings.exactWhitespace), Boolean.TRUE.equals(settings.ignoreComments),
                    false, Boolean.TRUE.equals(settings.ignoreOrder), settings.keys, settings.ignore,
                    !Boolean.FALSE.equals(settings.placeholders),
                    settings.tolerance == null ? 0 : settings.tolerance);
        }
    }

    /** The serialized form. */
    private static final class Data {
        String name;
        List<String> folders = new ArrayList<>();
        List<SchemaBinding> schemaBindings = new ArrayList<>();
        List<String> catalogs = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Data data;

    private FxtProject(Path file, Data data) {
        this.file = file.toAbsolutePath().normalize();
        this.data = data;
        if (data.folders == null || data.folders.isEmpty()) {
            data.folders = new ArrayList<>(List.of("."));
        }
        data.schemaBindings = data.schemaBindings == null ? new ArrayList<>() : new ArrayList<>(data.schemaBindings);
        data.catalogs = data.catalogs == null ? new ArrayList<>() : new ArrayList<>(data.catalogs);
        data.scenarios = data.scenarios == null ? new ArrayList<>() : new ArrayList<>(data.scenarios);
        for (Scenario scenario : data.scenarios) {
            if (scenario.parameters == null) {
                scenario.parameters = new LinkedHashMap<>();
            }
        }
    }

    /** A new, empty project (not yet saved). */
    public static FxtProject create(Path file, String name) {
        Data data = new Data();
        data.name = name;
        return new FxtProject(file, data);
    }

    /** @throws IOException if the file cannot be read or is not a project file */
    public static FxtProject load(Path file) throws IOException {
        try {
            Data data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (data == null) {
                throw new IOException(file.getFileName() + " is empty.");
            }
            return new FxtProject(file, data);
        } catch (JsonParseException e) {
            throw new IOException(file.getFileName() + " is not a valid project file: " + e.getMessage(), e);
        }
    }

    /** Writes the project file. */
    public void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        for (Scenario scenario : data.scenarios) {
            if (scenario.compare != null && scenario.compare.isDefault()) {
                scenario.compare = null;   // only settings that differ from the defaults are written
            }
            if (scenario.expected != null && scenario.expected.isBlank()) {
                scenario.expected = null;
            }
        }
        Files.writeString(file, GSON.toJson(data) + "\n", StandardCharsets.UTF_8);
    }

    // ---- paths -------------------------------------------------------------------------------

    public Path file() {
        return file;
    }

    /** @return the folder of the project file; relative paths are resolved against it */
    public Path root() {
        Path parent = file.getParent();
        return parent == null ? Path.of("").toAbsolutePath() : parent;
    }

    /** @return a path from the project file resolved to an absolute path */
    public Path resolve(String relative) {
        return root().resolve(relative).normalize();
    }

    /**
     * A path a project may write to: relative and inside the project folder. A project file comes
     * from anywhere (a repository, a download), so it must not name {@code ../../.bashrc} or a
     * start-up folder as the output of a scenario.
     *
     * @throws IOException when {@code relative} is absolute or leads out of the project folder
     */
    public Path resolveWritable(String relative) throws IOException {
        Path root = root().toAbsolutePath().normalize();
        Path target = root.resolve(relative).toAbsolutePath().normalize();
        boolean absolute = Path.of(relative).isAbsolute() || relative.startsWith("/") || relative.startsWith("\\")
                || relative.matches("^[A-Za-z]:.*");
        if (absolute || !target.startsWith(root) || target.equals(root)) {
            throw new IOException("Refused to write " + relative + ": a project writes only to files inside its folder ("
                    + root + ").");
        }
        return target;
    }

    /** @return {@code path} relative to the project folder (with {@code /}), or absolute if outside */
    public String relativize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(root())) {
            String relative = root().relativize(absolute).toString().replace('\\', '/');
            return relative.isEmpty() ? "." : relative;
        }
        return absolute.toString();
    }

    // ---- content -----------------------------------------------------------------------------

    public String name() {
        return data.name == null || data.name.isBlank() ? baseName(file) : data.name;
    }

    public void setName(String name) {
        data.name = name;
    }

    /** @return the project's folders, absolute (the first one is shown in the Explorer) */
    public List<Path> folders() {
        return data.folders.stream().map(this::resolve).toList();
    }

    public List<SchemaBinding> schemaBindings() {
        return List.copyOf(data.schemaBindings);
    }

    /** Replaces all bindings (Project Settings); empty ones are dropped. */
    public void setBindings(List<SchemaBinding> bindings) {
        data.schemaBindings = new ArrayList<>(bindings.stream()
                .filter(b -> b.files != null && !b.files.isBlank() && !b.isEmpty()).toList());
    }

    /** @return the folders as written in the project file */
    public List<String> folderEntries() {
        return List.copyOf(data.folders);
    }

    /** Replaces the folders (Project Settings); none = the project folder. */
    public void setFolders(List<String> folders) {
        data.folders = new ArrayList<>(folders.stream().filter(f -> f != null && !f.isBlank()).toList());
        if (data.folders.isEmpty()) {
            data.folders.add(".");
        }
    }

    /** @return the catalogs as written in the project file */
    public List<String> catalogEntries() {
        return List.copyOf(data.catalogs);
    }

    /** Replaces the catalogs (Project Settings). */
    public void setCatalogs(List<String> catalogs) {
        data.catalogs = new ArrayList<>(catalogs.stream().filter(c -> c != null && !c.isBlank()).distinct().toList());
    }

    /** Replaces the scenarios (Project Settings); scenarios without a name are dropped. */
    public void setScenarios(List<Scenario> scenarios) {
        data.scenarios = new ArrayList<>(scenarios.stream().filter(sc -> sc.name != null && !sc.name.isBlank()).toList());
        for (Scenario scenario : data.scenarios) {
            if (scenario.parameters == null) {
                scenario.parameters = new LinkedHashMap<>();
            }
        }
    }

    /** @return the catalogs, absolute */
    public List<Path> catalogs() {
        return data.catalogs.stream().map(this::resolve).toList();
    }

    public void addCatalog(Path catalog) {
        String relative = relativize(catalog);
        if (!data.catalogs.contains(relative)) {
            data.catalogs.add(relative);
        }
    }

    public List<Scenario> scenarios() {
        return List.copyOf(data.scenarios);
    }

    public Optional<Scenario> scenario(String name) {
        return data.scenarios.stream().filter(s -> s.name != null && s.name.equals(name)).findFirst();
    }

    /** Adds a scenario, replacing one of the same name. */
    public void putScenario(Scenario scenario) {
        data.scenarios.removeIf(s -> s.name != null && s.name.equals(scenario.name));
        data.scenarios.add(scenario);
    }

    public boolean removeScenario(String name) {
        return data.scenarios.removeIf(s -> s.name != null && s.name.equals(name));
    }

    /**
     * Binds a schema to XML files: {@code files} is a glob relative to the project folder
     * ({@code data/**\/*.xml}) or one file's relative path. A binding for the same pattern gets the
     * new schema and keeps its Schematron and scenario.
     */
    public void bindSchema(String files, Path schema) {
        SchemaBinding existing = bindingWithPattern(files);
        if (existing != null) {
            existing.schema = relativize(schema);
        } else {
            data.schemaBindings.add(new SchemaBinding(files, relativize(schema)));
        }
    }

    /**
     * Adds a binding, replacing the one with the same pattern in its place.
     */
    public void bind(SchemaBinding binding) {
        for (int i = 0; i < data.schemaBindings.size(); i++) {
            if (binding.files.equals(data.schemaBindings.get(i).files)) {
                data.schemaBindings.set(i, binding);
                return;
            }
        }
        data.schemaBindings.add(binding);
    }

    /** @return the binding written for exactly this pattern, or {@code null} */
    public SchemaBinding bindingWithPattern(String files) {
        return data.schemaBindings.stream().filter(b -> files.equals(b.files)).findFirst().orElse(null);
    }

    /** @return the schema the project binds to an XML file (the first matching binding), if any */
    public Optional<Path> schemaFor(Path xml) {
        return Optional.ofNullable(assignmentFor(xml).schema());
    }

    /** @return the Schematron the project binds to a file, if any */
    public Optional<Path> schematronFor(Path file) {
        return Optional.ofNullable(assignmentFor(file).schematron());
    }

    /** @return the scenario the project binds to a file, if it exists */
    public Optional<Scenario> scenarioFor(Path file) {
        String name = assignmentFor(file).scenario();
        return name == null ? Optional.empty() : scenario(name);
    }

    /**
     * What the project assigns to a file: schema, Schematron and scenario, each from the first
     * matching binding that names one (so {@code **\/*.xml → rules.sch} can add a Schematron to every
     * file while narrower patterns before it choose the schemas).
     */
    public Assignment assignmentFor(Path file) {
        if (file == null) {
            return Assignment.NONE;
        }
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(root())) {
            return Assignment.NONE;
        }
        Path relative = root().relativize(absolute);
        Path schema = null;
        Path schematron = null;
        String scenario = null;
        for (SchemaBinding binding : data.schemaBindings) {
            if (!matchesFile(binding.files, relative)) {
                continue;
            }
            if (schema == null && blankToNull(binding.schema) != null) {
                schema = resolve(binding.schema);
            }
            if (schematron == null && blankToNull(binding.schematron) != null) {
                schematron = resolve(binding.schematron);
            }
            if (scenario == null && blankToNull(binding.scenario) != null) {
                scenario = binding.scenario.strip();
            }
        }
        return new Assignment(schema, schematron, scenario);
    }

    /**
     * The files of the project's folders whose path matches a binding pattern (hidden folders such
     * as {@code .git} are skipped), in path order.
     *
     * @param files a pattern as in a binding
     * @param limit stop after this many files
     */
    /** @return the project's files whose binding names {@code scenario} (at most {@code limit}) */
    public List<Path> filesForScenario(String scenario, int limit) {
        return walkFiles(path -> scenario != null && scenario.equals(assignmentFor(path).scenario()), limit);
    }

    public List<Path> filesMatching(String files, int limit) {
        return walkFiles(path -> matchesFile(files, root().relativize(path)), limit);
    }

    /**
     * The files of the project's folders that a binding gives a schema or Schematron (the files
     * "Validate Project" checks), in path order.
     */
    public List<Path> validatedFiles(int limit) {
        return walkFiles(path -> {
            Assignment assignment = assignmentFor(path);
            return assignment.schema() != null || assignment.schematron() != null;
        }, limit);
    }

    private List<Path> walkFiles(java.util.function.Predicate<Path> accept, int limit) {
        java.util.Set<Path> found = new java.util.TreeSet<>();
        for (Path folder : folders()) {
            if (!Files.isDirectory(folder) || found.size() >= limit) {
                continue;
            }
            try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
                java.util.Iterator<Path> it = walk.filter(Files::isRegularFile)
                        .filter(path -> !insideHiddenFolder(folder, path))
                        .filter(path -> path.toAbsolutePath().normalize().startsWith(root()))
                        .iterator();
                while (it.hasNext() && found.size() < limit) {
                    Path path = it.next().toAbsolutePath().normalize();
                    if (!isProjectFile(path) && accept.test(path)) {
                        found.add(path);
                    }
                }
            } catch (IOException | java.io.UncheckedIOException e) {
                // an unreadable folder contributes nothing
            }
        }
        return List.copyOf(found);
    }

    private static boolean insideHiddenFolder(Path folder, Path file) {
        Path relative = folder.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String part = relative.getName(i).toString();
            if (part.startsWith(".") || part.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    /** @return whether a binding's pattern matches a project-relative path */
    public static boolean matchesFile(String files, Path relative) {
        if (files == null || files.isBlank()) {
            return false;
        }
        return matches(files, relative) || files.equals(relative.toString().replace('\\', '/'));
    }

    /**
     * A scenario prepared for one file: {@value #VAR_FILE} in the input and the parameters becomes the
     * file's project-relative path (an empty input becomes the file), and {@value #VAR_NAME},
     * {@value #VAR_DIR} and {@value #VAR_FILE} in the output are filled in.
     */
    public Scenario scenarioForFile(Scenario scenario, Path file) {
        Scenario copy = scenario.copy();
        String relative = relativize(file);
        String fileName = file.getFileName() == null ? relative : file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        int slash = relative.lastIndexOf('/');
        String dir = slash > 0 ? relative.substring(0, slash) : ".";
        copy.input = copy.input == null || copy.input.isBlank() ? relative : copy.input.replace(VAR_FILE, relative);
        copy.parameters.replaceAll((k, v) -> v == null ? null : v.replace(VAR_FILE, relative));
        if (copy.output != null) {
            copy.output = copy.output.replace(VAR_NAME, name).replace(VAR_DIR, dir).replace(VAR_FILE, relative);
        }
        if (copy.expected != null) {
            copy.expected = copy.expected.replace(VAR_NAME, name).replace(VAR_DIR, dir).replace(VAR_FILE, relative);
        }
        return copy;
    }

    /**
     * Glob matching where {@code **}{@code /} also stands for no folder at all ({@code data/**}{@code /*.xml}
     * matches {@code data/a.xml}), as in most tools; Java's globs need at least one folder there.
     */
    static boolean matches(String glob, Path relative) {
        if (MATCHERS.size() > 500) {
            MATCHERS.clear();
        }
        for (PathMatcher matcher : MATCHERS.computeIfAbsent(glob, FxtProject::compile)) {
            if (matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }

    /** Compiled patterns (the Explorer asks for every file row it draws). */
    private static final Map<String, List<PathMatcher>> MATCHERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<PathMatcher> compile(String glob) {
        java.util.Set<String> variants = new java.util.LinkedHashSet<>();
        variants.add(glob);
        variants.add(glob.replace("/**/", "/"));
        if (glob.startsWith("**/")) {
            variants.add(glob.substring(3));
            variants.add(glob.substring(3).replace("/**/", "/"));
        }
        try {
            return variants.stream().map(v -> FileSystems.getDefault().getPathMatcher("glob:" + v)).toList();
        } catch (RuntimeException e) {
            return List.of(); // an invalid pattern matches nothing (see isValidPattern)
        }
    }

    /** @return whether a binding pattern is a valid glob */
    public static boolean isValidPattern(String files) {
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + files);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** @return whether a path is a project file */
    public static boolean isProjectFile(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + EXTENSION);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String baseName(Path path) {
        String name = path.getFileName() == null ? "project" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
