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
 * A project file ({@code .fxtproj}, JSON): the folders that belong to it, which schema validates
 * which XML files, the XML catalogs to use, and named transformation scenarios. All paths in the
 * file are relative to the folder of the project file, so a project can be committed with the
 * files it describes and opened on another computer.
 *
 * <pre>{@code
 * {
 *   "name": "Orders",
 *   "folders": ["."],
 *   "schemaBindings": [ { "files": "data/**\/*.xml", "schema": "schemas/order.xsd" } ],
 *   "catalogs": ["schemas/catalog.xml"],
 *   "scenarios": [ { "name": "Report", "stylesheet": "xsl/report.xsl", "input": "data/order.xml",
 *                    "parameters": { "year": "2026" }, "outputFormat": "HTML", "output": "out/report.html" } ]
 * }
 * }</pre>
 */
public final class FxtProject {

    /** The file extension of project files. */
    public static final String EXTENSION = "fxtproj";

    /** A schema for the XML files whose project-relative path matches a glob ({@code data/**\/*.xml}). */
    public static final class SchemaBinding {
        public String files;
        public String schema;

        public SchemaBinding() {
        }

        public SchemaBinding(String files, String schema) {
            this.files = files;
            this.schema = schema;
        }
    }

    /**
     * A transformation scenario.
     *
     * <p>{@code stylesheet} is an XSLT ({@code .xsl}, {@code .xslt}), XQuery ({@code .xq}, …) or XProc
     * ({@code .xpl}) file; {@code input} the document (optional for XQuery and XProc); {@code output}
     * where the result is written (optional: the result is shown instead); {@code outputFormat} one
     * of XML, HTML, XHTML, TEXT, JSON (optional: from the stylesheet's {@code xsl:output}).
     */
    public static final class Scenario {
        public String name;
        public String stylesheet;
        public String input;
        public Map<String, String> parameters = new LinkedHashMap<>();
        public String outputFormat;
        public String output;

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
     * ({@code data/**\/*.xml}) or one file's relative path. A binding for the same pattern is replaced.
     */
    public void bindSchema(String files, Path schema) {
        data.schemaBindings.removeIf(b -> files.equals(b.files));
        data.schemaBindings.add(new SchemaBinding(files, relativize(schema)));
    }

    /** @return the schema the project binds to an XML file (the first matching binding), if any */
    public Optional<Path> schemaFor(Path xml) {
        Path absolute = xml.toAbsolutePath().normalize();
        if (!absolute.startsWith(root())) {
            return Optional.empty();
        }
        Path relative = root().relativize(absolute);
        for (SchemaBinding binding : data.schemaBindings) {
            if (binding.files == null || binding.schema == null) {
                continue;
            }
            if (matches(binding.files, relative) || binding.files.equals(relative.toString().replace('\\', '/'))) {
                return Optional.of(resolve(binding.schema));
            }
        }
        return Optional.empty();
    }

    /**
     * Glob matching where {@code **}{@code /} also stands for no folder at all ({@code data/**}{@code /*.xml}
     * matches {@code data/a.xml}), as in most tools; Java's globs need at least one folder there.
     */
    static boolean matches(String glob, Path relative) {
        java.util.Set<String> variants = new java.util.LinkedHashSet<>();
        variants.add(glob);
        variants.add(glob.replace("/**/", "/"));
        if (glob.startsWith("**/")) {
            variants.add(glob.substring(3));
            variants.add(glob.substring(3).replace("/**/", "/"));
        }
        for (String variant : variants) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + variant);
            if (matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }

    /** @return whether a path is a project file */
    public static boolean isProjectFile(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + EXTENSION);
    }

    private static String baseName(Path path) {
        String name = path.getFileName() == null ? "project" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
