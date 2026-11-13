package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.service.update.InstallSource;
import org.junit.jupiter.api.Test;

class LaunchContextTest {

    private static final Map<String, String> WIN_ENV = Map.of(
            "ProgramFiles", "C:\\Program Files",
            "LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local");

    @Test
    void noLauncherMeansNone() {
        assertEquals(LaunchContext.PACKAGE_NONE,
                LaunchContext.packageKind(false, "Linux", Path.of("/opt/freexmltoolkit"), Map.of()));
    }

    @Test
    void windowsInstallerLocations() {
        assertEquals(LaunchContext.PACKAGE_INSTALLER, LaunchContext.packageKind(true, "Windows 11",
                Path.of("C:\\Program Files\\FreeXmlToolkit"), WIN_ENV));
        assertEquals(LaunchContext.PACKAGE_INSTALLER, LaunchContext.packageKind(true, "Windows 11",
                Path.of("C:\\Users\\u\\AppData\\Local\\Programs\\FreeXmlToolkit"), WIN_ENV));
        assertEquals(LaunchContext.PACKAGE_PORTABLE, LaunchContext.packageKind(true, "Windows 11",
                Path.of("D:\\tools\\FreeXmlToolkit"), WIN_ENV));
    }

    @Test
    void unixInstallerLocations() {
        assertEquals(LaunchContext.PACKAGE_INSTALLER,
                LaunchContext.packageKind(true, "Linux", Path.of("/opt/freexmltoolkit"), Map.of()));
        assertEquals(LaunchContext.PACKAGE_PORTABLE,
                LaunchContext.packageKind(true, "Linux", Path.of("/home/u/FreeXmlToolkit"), Map.of()));
        assertEquals(LaunchContext.PACKAGE_INSTALLER, LaunchContext.packageKind(true, "Mac OS X",
                Path.of("/Applications/FreeXmlToolkit.app"), Map.of()));
        assertEquals(LaunchContext.PACKAGE_PORTABLE, LaunchContext.packageKind(true, "Mac OS X",
                Path.of("/Users/u/Downloads/FreeXmlToolkit.app"), Map.of()));
    }

    @Test
    void settingsSnapshotWithoutPropertiesIsEmpty() {
        assertTrue(LaunchContext.settingsSnapshot(null).isEmpty());
    }

    /** Settings in memory, the provider list written as Settings → AI writes it. */
    private static java.util.Map<String, String> aiSettings(boolean enabled,
                                                            org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig... providers) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put(org.fxt.freexmltoolkit.service.ai.AiSettings.KEY_ENABLED, String.valueOf(enabled));
        new org.fxt.freexmltoolkit.service.ai.provider.ProviderStore(values::get, values::put, () -> true, key -> false,
                java.util.List::of).save(java.util.List.of(providers));
        return values;
    }

    private static org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig provider(String name,
            org.fxt.freexmltoolkit.service.ai.provider.ProviderKind kind, String url, String model) {
        return new org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig("p1", name, kind,
                org.fxt.freexmltoolkit.service.ai.provider.ProviderPreset.CUSTOM, url, model, null, null, null, 0, 60);
    }

    private static java.util.Map<String, Object> aiSnapshot(java.util.Map<String, String> settings) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        LaunchContext.putAi(m, settings::get);
        return m;
    }

    @Test
    void aiOffReportsOnlyThatItIsOff() {
        assertEquals(java.util.Map.of("set_ai", false), aiSnapshot(aiSettings(false,
                provider("Company gateway", org.fxt.freexmltoolkit.service.ai.provider.ProviderKind.ANTHROPIC,
                        "https://ai.example.com", "secret-model"))));
        assertEquals(java.util.Map.of("set_ai", false), aiSnapshot(new java.util.HashMap<>()));
    }

    @Test
    void aiOnReportsTheKindOfTheDefaultProviderOnly() {
        assertEquals(java.util.Map.of("set_ai", true, "set_ai_provider", "none"), aiSnapshot(aiSettings(true)));
        assertEquals("local", aiSnapshot(aiSettings(true, provider("My Ollama",
                org.fxt.freexmltoolkit.service.ai.provider.ProviderKind.OPENAI_COMPATIBLE,
                "http://localhost:11434/v1", "qwen2.5-coder:14b"))).get("set_ai_provider"));
        assertEquals("openai_compatible", aiSnapshot(aiSettings(true, provider("Gateway",
                org.fxt.freexmltoolkit.service.ai.provider.ProviderKind.OPENAI_COMPATIBLE,
                "https://llm.example.com/v1", "gpt-x"))).get("set_ai_provider"));
        java.util.Map<String, Object> anthropic = aiSnapshot(aiSettings(true, provider("Company gateway",
                org.fxt.freexmltoolkit.service.ai.provider.ProviderKind.ANTHROPIC, "https://ai.example.com",
                "secret-model")));
        assertEquals("anthropic", anthropic.get("set_ai_provider"));
        String values = anthropic.values().toString();
        for (String leak : new String[]{"Company", "example.com", "secret-model", "p1"}) {
            assertFalse(values.contains(leak), "no name, host, model or id in telemetry: " + values);
        }
    }

    @Test
    void startupMillisIsPositive() {
        assertTrue(LaunchContext.startupMillis() > 0);
    }

    @Test
    void installSourceUsesQuickDetection() {
        assertEquals("flatpak", LaunchContext.installSource(InstallSource.FLATPAK, null));
        assertEquals("scoop", LaunchContext.installSource(InstallSource.SCOOP, "winget"));
        assertEquals("standalone", LaunchContext.installSource(InstallSource.STANDALONE, null));
        assertEquals("standalone", LaunchContext.installSource(InstallSource.STANDALONE, "homebrew"));
    }

    @Test
    void wingetComesFromThePreviousFullDetection() {
        assertEquals("winget", LaunchContext.installSource(InstallSource.STANDALONE, "winget"));
    }
}
