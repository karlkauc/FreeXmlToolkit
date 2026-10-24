package org.fxt.freexmltoolkit.debugger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * How a stylesheet is debugged, remembered per stylesheet: the input (a file, or empty for the
 * Run target — the XML document the Run button uses), the stylesheet parameters, the output
 * format (empty = detected from {@code xsl:output}) and whether the run pauses on entry.
 *
 * @param inputPath    absolute input file path, {@code ""} = the Run target
 * @param parameters   stylesheet parameters (name → value), in order
 * @param outputFormat an {@code OutputFormat} name, {@code ""} = automatic
 * @param pauseOnEntry pause at the first instruction even when breakpoints are set
 */
public record DebugLaunchConfig(String inputPath, Map<String, String> parameters, String outputFormat,
                                boolean pauseOnEntry) {

    static final String KEY_PREFIX = "debug.launch.";

    public DebugLaunchConfig {
        inputPath = inputPath == null ? "" : inputPath.strip();
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        outputFormat = outputFormat == null ? "" : outputFormat.strip();
    }

    /** The defaults: Run target, no parameters, automatic format, no forced pause. */
    public static DebugLaunchConfig defaults() {
        return new DebugLaunchConfig("", Map.of(), "", false);
    }

    /** @return the configuration stored for {@code stylesheetPath}, or empty. */
    public static java.util.Optional<DebugLaunchConfig> load(String stylesheetPath) {
        PropertiesService service = BreakpointStore.properties();
        if (service == null) {
            return java.util.Optional.empty();
        }
        return parse(service.get(key(stylesheetPath)));
    }

    /** Stores this configuration for {@code stylesheetPath}. */
    public void save(String stylesheetPath) {
        PropertiesService service = BreakpointStore.properties();
        if (service == null) {
            return;
        }
        try {
            service.set(key(stylesheetPath), format());
        } catch (RuntimeException ignored) {
            // not persisting the configuration is not worth an error
        }
    }

    static String key(String stylesheetPath) {
        return KEY_PREFIX + BreakpointStore.pathHash(stylesheetPath);
    }

    String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("input\t").append(BreakpointStore.escape(inputPath)).append('\n');
        sb.append("format\t").append(outputFormat).append('\n');
        sb.append("pauseOnEntry\t").append(pauseOnEntry);
        parameters.forEach((name, value) -> sb.append('\n').append("param\t")
                .append(BreakpointStore.escape(name)).append('\t').append(BreakpointStore.escape(value)));
        return sb.toString();
    }

    static java.util.Optional<DebugLaunchConfig> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return java.util.Optional.empty();
        }
        String input = "";
        String format = "";
        boolean pause = false;
        Map<String, String> params = new LinkedHashMap<>();
        for (String line : stored.split("\n")) {
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "input" -> input = f.length > 1 ? BreakpointStore.unescape(f[1]) : "";
                case "format" -> format = f.length > 1 ? f[1] : "";
                case "pauseOnEntry" -> pause = f.length > 1 && Boolean.parseBoolean(f[1].trim());
                case "param" -> {
                    if (f.length > 2 && !f[1].isBlank()) {
                        params.put(BreakpointStore.unescape(f[1]), BreakpointStore.unescape(f[2]));
                    }
                }
                default -> { /* unknown keys from newer versions are ignored */ }
            }
        }
        return java.util.Optional.of(new DebugLaunchConfig(input, params, format, pause));
    }

    /** Parses "name=value" lines (blank lines and lines without '=' are skipped). */
    public static Map<String, String> parseParameters(String text) {
        Map<String, String> params = new LinkedHashMap<>();
        if (text != null) {
            for (String line : text.split("\\R")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.substring(0, eq).isBlank()) {
                    params.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            }
        }
        return params;
    }

    /** @return the parameters as "name=value" lines. */
    public String parametersText() {
        StringBuilder sb = new StringBuilder();
        parameters.forEach((n, v) -> sb.append(sb.length() > 0 ? "\n" : "").append(n).append('=').append(v));
        return sb.toString();
    }
}
