package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class TelemetryEnvironmentTest {

    @Test
    void osNameMapping() {
        assertEquals("Windows", TelemetryEnvironment.mapOsName("Windows 11"));
        assertEquals("macOS", TelemetryEnvironment.mapOsName("Mac OS X"));
        assertEquals("Linux", TelemetryEnvironment.mapOsName("Linux"));
        assertEquals("Other", TelemetryEnvironment.mapOsName("FreeBSD"));
        assertEquals("Other", TelemetryEnvironment.mapOsName(null));
    }

    @Test
    void osArchMapping() {
        assertEquals("x64", TelemetryEnvironment.mapOsArch("amd64"));
        assertEquals("x64", TelemetryEnvironment.mapOsArch("x86_64"));
        assertEquals("arm64", TelemetryEnvironment.mapOsArch("aarch64"));
        assertEquals("arm64", TelemetryEnvironment.mapOsArch("arm64"));
        assertEquals("other", TelemetryEnvironment.mapOsArch("x86"));
        assertEquals("other", TelemetryEnvironment.mapOsArch(null));
    }

    @Test
    void localeIsLanguageOnly() {
        assertEquals("de", TelemetryEnvironment.mapLocale(Locale.GERMANY));
        assertEquals("en", TelemetryEnvironment.mapLocale(Locale.ROOT));
        assertEquals("en", TelemetryEnvironment.mapLocale(null));
    }

    @Test
    void currentEnvironmentIsWithinApiLimits() {
        TelemetryEnvironment env = TelemetryEnvironment.current();
        assertTrue(env.osName().matches("Windows|macOS|Linux|Other"));
        assertTrue(env.osArch().matches("x64|arm64|other"));
        assertEquals(Runtime.version().feature(), env.javaMajor());
        assertTrue(env.locale().matches("[a-z]{2,3}"));
        assertTrue(env.appVersion().length() <= 32);
    }

    @Test
    void docKindFromExtensionAndPath() {
        assertEquals(DocKind.XML, DocKind.fromExtension(".XML"));
        assertEquals(DocKind.XSD, DocKind.fromExtension("xsd"));
        assertEquals(DocKind.XSLT, DocKind.fromExtension("xsl"));
        assertEquals(DocKind.XSLT, DocKind.fromExtension("xslt"));
        assertEquals(DocKind.SCHEMATRON, DocKind.fromExtension("sch"));
        assertEquals(DocKind.JSON, DocKind.fromExtension("json"));
        assertEquals(DocKind.OTHER, DocKind.fromExtension("txt"));
        assertEquals(DocKind.XSD, DocKind.fromPath(Path.of("some", "dir.v1", "schema.xsd")));
        assertEquals(DocKind.OTHER, DocKind.fromFileName("dir.v1/README"));
        assertNull(DocKind.fromPath(null));
        assertEquals("schematron", DocKind.SCHEMATRON.wireName());
    }

    @Test
    void eventBuilderSanitizesAndNeverStoresPaths() {
        TelemetryEvent e = TelemetryEvent.builder("Validate File!", TelemetryEvent.Category.ACTION)
                .docKind(Path.of("/home/alice/secret.xml"))
                .inputBytes(-5)
                .meta("mode", "tree")
                .meta("nested", java.util.List.of(1, 2))
                .build();
        assertEquals("validate_file", e.eventType());
        assertEquals(DocKind.XML, e.docKind());
        assertEquals(0L, e.inputBytes());
        assertEquals("tree", e.meta().get("mode"));
        assertTrue(!e.meta().containsKey("nested"));
        String json = e.toJson().toString();
        assertTrue(!json.contains("alice") && !json.contains("secret"), json);
        assertEquals(e, TelemetryEvent.fromJson(e.toJson()));
    }
}
