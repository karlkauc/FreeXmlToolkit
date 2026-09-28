package org.fxt.freexmltoolkit.service.telemetry;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.fxt.freexmltoolkit.service.ApplicationLauncherLocator;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Builds the launch context of the {@code app_start} event: startup time, how the app was
 * installed and a snapshot of a few UI settings (to see whether the defaults fit).
 *
 * <p>Privacy: only fixed enum values and booleans — never the install path, proxy
 * settings, user info or any other free text.
 */
public final class LaunchContext {

    /** {@code meta.package}: installed by an installer (Program Files, /Applications, /opt). */
    public static final String PACKAGE_INSTALLER = "installer";
    /** {@code meta.package}: native app image unpacked somewhere else (zip / tar.gz / copied .app). */
    public static final String PACKAGE_PORTABLE = "portable";
    /** {@code meta.package}: no native launcher (IDE, {@code gradlew run}, plain jar). */
    public static final String PACKAGE_NONE = "none";

    private LaunchContext() {
    }

    /**
     * Customizer for the {@code app_start} event of this process.
     *
     * @param props the settings (nullable: no snapshot then)
     */
    public static Consumer<TelemetryEvent.Builder> appStart(PropertiesService props) {
        long startupMs = startupMillis();
        String pkg = currentPackageKind();
        return b -> {
            if (startupMs > 0) {
                b.durationMs(startupMs);
            }
            b.meta("package", pkg);
            settingsSnapshot(props).forEach(b::meta);
        };
    }

    /** @return milliseconds since the JVM started, or -1 when unknown */
    static long startupMillis() {
        try {
            long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
            return jvmStart > 0 ? Math.max(0, System.currentTimeMillis() - jvmStart) : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String currentPackageKind() {
        try {
            return packageKind(ApplicationLauncherLocator.isInstalled(), System.getProperty("os.name"),
                    ApplicationLauncherLocator.getApplicationDirectory(), System.getenv());
        } catch (Throwable t) {
            return PACKAGE_NONE;
        }
    }

    /**
     * Classifies the installation from the application folder.
     *
     * @param installed whether a native launcher exists
     * @param osName    {@code os.name}
     * @param appDir    the application folder
     * @param env       environment variables ({@code ProgramFiles}, {@code LOCALAPPDATA}, …)
     * @return one of the {@code PACKAGE_*} constants
     */
    static String packageKind(boolean installed, String osName, Path appDir, Map<String, String> env) {
        if (!installed || appDir == null) {
            return PACKAGE_NONE;
        }
        String dir = normalize(appDir.toString());
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            for (String var : new String[]{"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                if (startsWith(dir, env.get(var))) {
                    return PACKAGE_INSTALLER;
                }
            }
            String local = env.get("LOCALAPPDATA");
            // Per-user installs of jpackage installers land in %LOCALAPPDATA%\Programs
            if (local != null && startsWith(dir, local + "/Programs")) {
                return PACKAGE_INSTALLER;
            }
            return PACKAGE_PORTABLE;
        }
        if (os.contains("mac")) {
            return dir.startsWith("/applications/") ? PACKAGE_INSTALLER : PACKAGE_PORTABLE;
        }
        return dir.startsWith("/opt/") || dir.startsWith("/usr/") ? PACKAGE_INSTALLER : PACKAGE_PORTABLE;
    }

    /**
     * A few UI settings as flat enum/boolean meta values ({@code set_*} keys).
     *
     * @param props the settings (nullable)
     */
    static Map<String, Object> settingsSnapshot(PropertiesService props) {
        if (props == null) {
            return Map.of();
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        put(m, "set_theme", () -> "dark".equals(props.get("ui.theme")) ? "dark" : "light");
        put(m, "set_toolbar_labels", props::isToolbarShowLabels);
        put(m, "set_toolbar_icons", () -> enumValue(props.getToolbarIconSize()));
        put(m, "set_activity_labels", props::isActivityBarShowLabels);
        put(m, "set_rendering", () -> enumValue(props.getRenderingMode()));
        put(m, "set_update_check", props::isUpdateCheckEnabled);
        put(m, "set_xml_autoformat", props::isXmlAutoFormatAfterLoading);
        put(m, "set_xsd_autosave", props::isXsdAutoSaveEnabled);
        put(m, "set_schema_autobind", props::isSchemaLibraryAutoBindEnabled);
        return m;
    }

    private static void put(Map<String, Object> m, String key, java.util.function.Supplier<Object> value) {
        try {
            Object v = value.get();
            if (v != null) {
                m.put(key, v);
            }
        } catch (Throwable ignored) {
            // a broken setting must not drop the whole snapshot
        }
    }

    /** Keeps only short identifier-like values (enum names), never free text. */
    private static String enumValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.strip().toLowerCase(Locale.ROOT);
        return v.length() <= 20 && v.matches("[a-z0-9_-]+") ? v : "other";
    }

    private static boolean startsWith(String normalizedDir, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        String p = normalize(prefix);
        return normalizedDir.equals(p) || normalizedDir.startsWith(p.endsWith("/") ? p : p + "/");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
