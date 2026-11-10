package org.fxt.freexmltoolkit.service.ai;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.context.DataLevel;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;

/**
 * Typed access to the user's {@code ai.*} settings (Settings → AI). Everything is off by default; what the
 * administrator policy allows is {@link AiPolicy}'s business.
 */
public final class AiSettings {

    public static final String KEY_ENABLED = "ai.enabled";
    public static final String KEY_DATA_LEVEL = "ai.dataLevel";
    public static final String KEY_DATA_LEVEL_REMOTE = "ai.dataLevel.remote";
    public static final String KEY_PREVIEW_ALWAYS = "ai.preview.always";
    public static final String KEY_LOCAL_HOSTS = "ai.localHosts";
    public static final String KEY_TOOL_TIMEOUT = "ai.tool.timeoutSeconds";
    public static final String KEY_TOOL_MAX_OUTPUT_KB = "ai.tool.maxOutputKb";
    public static final String KEY_TOOL_MAX_INPUT_MB = "ai.tool.maxInputMb";

    private final Function<String, String> getter;

    /** @param getter reads one setting ({@code null} = not set) */
    public AiSettings(Function<String, String> getter) {
        this.getter = getter;
    }

    /** The settings of this installation. */
    public static AiSettings current() {
        return new AiSettings(PropertiesServiceImpl.getInstance()::get);
    }

    /** {@code ai.enabled} (default off) — and the policy allows AI at all. */
    public boolean enabled() {
        return AiPolicy.allowed() && Boolean.parseBoolean(get(KEY_ENABLED));
    }

    /** {@code ai.dataLevel}, default {@link DataLevel#EXCERPTS}. */
    public DataLevel dataLevel() {
        return DataLevel.parse(get(KEY_DATA_LEVEL), DataLevel.EXCERPTS);
    }

    /** {@code ai.dataLevel.remote}: the cap for providers that are not local, default {@link DataLevel#SCHEMA}. */
    public DataLevel dataLevelRemote() {
        return DataLevel.parse(get(KEY_DATA_LEVEL_REMOTE), DataLevel.SCHEMA);
    }

    /** The level that applies to a request: remote providers get at most {@link #dataLevelRemote()}. */
    public DataLevel effectiveLevel(boolean localProvider) {
        return localProvider ? dataLevel() : dataLevel().min(dataLevelRemote());
    }

    /** {@code ai.preview.always}: show every request before it is sent (default on). */
    public boolean previewAlways() {
        String value = get(KEY_PREVIEW_ALWAYS);
        return value == null || value.isBlank() || Boolean.parseBoolean(value);
    }

    /** {@code ai.localHosts}: further hosts that count as local (a company gateway). */
    public List<String> localHosts() {
        String value = get(KEY_LOCAL_HOSTS);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;\\s]+")).map(String::strip).filter(h -> !h.isEmpty()).toList();
    }

    /** The limits of tool calls made by the in-app assistant ({@code ai.tool.*}), clamped to sane ranges. */
    public ToolLimits toolLimits() {
        ToolLimits d = ToolLimits.DEFAULTS;
        int timeout = number(KEY_TOOL_TIMEOUT, (int) d.timeout().toSeconds(), 1, 600);
        int outputKb = number(KEY_TOOL_MAX_OUTPUT_KB, d.maxOutputChars() / 1024, 1, 4096);
        int inputMb = number(KEY_TOOL_MAX_INPUT_MB, (int) (d.maxInputBytes() / (1024 * 1024)), 1, 1024);
        return new ToolLimits(Duration.ofSeconds(timeout), outputKb * 1024, inputMb * 1024L * 1024, d.maxItems());
    }

    private int number(String key, int fallback, int min, int max) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.strip())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String get(String key) {
        return getter.apply(key);
    }
}
