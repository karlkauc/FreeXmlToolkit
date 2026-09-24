package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Locale;

import org.fxt.freexmltoolkit.util.VersionUtil;

/**
 * Coarse, non-identifying description of the runtime environment sent in the batch
 * envelope: OS family, CPU architecture family, Java feature release, UI language
 * and app version.
 *
 * @param appVersion app version ({@link VersionUtil#getVersion()}), at most 32 chars
 * @param osName     {@code Windows | macOS | Linux | Other}
 * @param osArch     {@code x64 | arm64 | other}
 * @param javaMajor  Java feature release, e.g. {@code 25}
 * @param locale     language code only, e.g. {@code de}
 */
public record TelemetryEnvironment(String appVersion, String osName, String osArch, int javaMajor, String locale) {

    /** @return the environment of the running JVM. */
    public static TelemetryEnvironment current() {
        return new TelemetryEnvironment(
                TelemetrySanitizer.truncate(VersionUtil.getVersion(), 32),
                mapOsName(System.getProperty("os.name")),
                mapOsArch(System.getProperty("os.arch")),
                Runtime.version().feature(),
                mapLocale(Locale.getDefault()));
    }

    /** Maps {@code os.name} to {@code Windows | macOS | Linux | Other}. */
    public static String mapOsName(String osName) {
        if (osName == null) {
            return "Other";
        }
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return "Windows";
        }
        if (os.startsWith("mac") || os.contains("darwin") || os.contains("os x")) {
            return "macOS";
        }
        if (os.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }

    /** Maps {@code os.arch} to {@code x64 | arm64 | other} (amd64/x86_64 → x64, aarch64 → arm64). */
    public static String mapOsArch(String osArch) {
        if (osArch == null) {
            return "other";
        }
        return switch (osArch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64", "x64", "x86-64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> "other";
        };
    }

    /** Language part only ({@code [a-z]{2,3}}); falls back to {@code "en"}. */
    public static String mapLocale(Locale locale) {
        if (locale == null) {
            return "en";
        }
        String lang = locale.getLanguage() == null ? "" : locale.getLanguage().toLowerCase(Locale.ROOT);
        return lang.matches("[a-z]{2,3}") ? lang : "en";
    }
}
