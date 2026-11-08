package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.di.ServiceRegistry;

import picocli.CommandLine;

/** Shared helpers of the subcommands: service setup, input files, output. */
final class CliSupport {

    private static volatile boolean initialized;

    private CliSupport() {
    }

    /**
     * Prepares the services the way the application does, minus everything interactive: the
     * proxy from the settings applies to schema downloads, telemetry stays off.
     */
    static synchronized void initServices() {
        if (initialized) {
            return;
        }
        if (System.getProperty("java.net.useSystemProxies") == null) {
            System.setProperty("java.net.useSystemProxies", "true");
        }
        ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.plugin.PluginManager.getInstance().loadAll(); // their validators
        try {
            org.fxt.freexmltoolkit.service.ConnectionServiceImpl.getInstance().applyGlobalSettings();
        } catch (RuntimeException e) {
            org.apache.logging.log4j.LogManager.getLogger(CliSupport.class)
                    .warn("Could not apply the proxy settings: {}", e.toString());
        }
        initialized = true;
    }

    /**
     * Expands the given files and folders: files are taken as they are, folders are searched
     * recursively for file names matching one of the globs (hidden folders are skipped).
     *
     * @throws CommandLine.ParameterException when an input does not exist (exit code 2)
     */
    static List<Path> expand(CommandLine commandLine, List<Path> inputs, List<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob.trim()));
        }
        Set<Path> files = new LinkedHashSet<>();
        for (Path input : inputs) {
            if (Files.isRegularFile(input)) {
                files.add(input);
            } else if (Files.isDirectory(input)) {
                try (Stream<Path> walk = Files.walk(input)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> !insideHiddenFolder(input, p))
                            .filter(p -> matchers.stream().anyMatch(m -> m.matches(p.getFileName())))
                            .sorted()
                            .forEach(files::add);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot read the folder " + input + ": " + e.getMessage(), e);
                }
            } else {
                throw new CommandLine.ParameterException(commandLine, "File not found: " + input);
            }
        }
        return new ArrayList<>(files);
    }

    private static boolean insideHiddenFolder(Path root, Path file) {
        Path relative = root.relativize(file).getParent();
        if (relative == null) {
            return false;
        }
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /** @throws CommandLine.ParameterException when {@code file} is given but is not a file */
    static void requireFile(CommandLine commandLine, Path file, String what) {
        if (file != null && !Files.isRegularFile(file)) {
            throw new CommandLine.ParameterException(commandLine, what + " not found: " + file);
        }
    }

    static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** Writes text as UTF-8, creating missing parent folders. */
    static void write(Path file, String text) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** The file name without its extension. */
    static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** The lower-case extension without the dot, or an empty string. */
    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }
}
