package org.fxt.freexmltoolkit.screenshots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys;

/**
 * The files and settings the FundsXML tutorial videos (storyboards in {@code videos/fundsxml/})
 * share: the example documents of {@code src/test/resources/fundsxml/video/} next to the FundsXML
 * schema in a work folder, and the FundsXML extension switched on. Nothing is downloaded while a film
 * is recorded.
 *
 * <p>The documents are copies of the realistic samples of the official examples
 * (github.com/fundsxml/examples, {@code FundsXML_Files/4.2.9/realistic}, Apache-2.0): fictional funds with
 * synthetic identifiers, with the schema location changed to the local {@code FundsXML4.xsd}. The
 * {@code _xsd-error} and {@code _weights} variants differ from their original in one value. The schema is the current
 * release from the FundsXML schema repository next to this one ({@code ../fundsxml_schema}, or the
 * folder in {@code -Dfxt.video.fundsxml.schema}); without it, the older test copy
 * {@code src/test/resources/FundsXML4.xsd} (4.2.9) is used.
 */
final class FundsXmlVideoFiles {

    private static final Path EXAMPLES = Path.of("src/test/resources/fundsxml/video");
    private static final Path FALLBACK_SCHEMA = Path.of("src/test/resources");

    private FundsXmlVideoFiles() {
    }

    /** A new work folder with all example documents and stylesheets, {@code FundsXML4.xsd} and the signature schema it imports. */
    static Path folder(String name) throws IOException {
        Path folder = DocFiles.work(name);
        try (Stream<Path> files = Files.list(EXAMPLES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".xml") || f.toString().endsWith(".xslt")).toList()) {
                Files.copy(file, folder.resolve(file.getFileName()));
            }
        }
        Path schemaDir = schemaDir();
        Files.copy(schemaDir.resolve("FundsXML4.xsd"), folder.resolve("FundsXML4.xsd"));
        Files.copy(schemaDir.resolve("xmldsig-core-schema.xsd"), folder.resolve("xmldsig-core-schema.xsd"));
        return folder;
    }

    /**
     * Switches the FundsXML extension on (its data-quality checks run with every validation), with the
     * content cache filled, so the FundsXML panel shows the active schema version.
     */
    static void enableExtension() throws IOException {
        seedCache();
        ServiceRegistry.get(PropertiesService.class).set(FundsXmlPropertyKeys.ENABLED, "true");
    }

    /** Switches the FundsXML extension off and lets its one-time hint show again. */
    static void disableExtension() {
        PropertiesService properties = ServiceRegistry.get(PropertiesService.class);
        properties.set(FundsXmlPropertyKeys.ENABLED, "false");
        properties.set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "false");
    }

    /**
     * Fills the FundsXML content cache as a download would (the schema as its version, the example
     * documents), and turns the update check off - so switching the extension on fetches nothing.
     */
    static void seedCache() throws IOException {
        ServiceRegistry.get(PropertiesService.class).set(FundsXmlPropertyKeys.UPDATE_CHECK_ENABLED, "false");
        var cache = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache.getInstance();
        Path schemaDir = schemaDir();
        // the flattened schema names no version; the modular one in include_files/ does
        Path modular = schemaDir.resolve("include_files/FundsXML4.xsd");
        String schema = Files.readString(Files.isRegularFile(modular) ? modular : schemaDir.resolve("FundsXML4.xsd"));
        var m = java.util.regex.Pattern.compile("<xs:schema[^>]*\\sversion=\"([^\"]+)\"").matcher(schema);
        String version = m.find() ? m.group(1) : "4.2.11";
        Path target = cache.getSchemaVersionDir(version);
        Files.copy(schemaDir.resolve("FundsXML4.xsd"), target.resolve("FundsXML4.xsd"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(schemaDir.resolve("xmldsig-core-schema.xsd"), target.resolve("xmldsig-core-schema.xsd"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path examples = cache.getExamplesDir();
        Files.createDirectories(examples);
        try (Stream<Path> files = Files.list(EXAMPLES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".xml")).toList()) {
                // the copies name the cached schema (the downloaded examples name a URL; the cache keeps
                // nothing but samples in its examples folder)
                Files.writeString(examples.resolve(file.getFileName()), Files.readString(file).replace(
                        "xsi:noNamespaceSchemaLocation=\"FundsXML4.xsd\"",
                        "xsi:noNamespaceSchemaLocation=\"../schema/" + version + "/FundsXML4.xsd\""));
            }
        }
        var meta = cache.loadMetadata();
        if (!meta.getInstalledSchemaVersions().contains(version)) {
            meta.getInstalledSchemaVersions().add(version);
        }
        meta.setActiveSchemaVersion(version);
        cache.saveMetadata(meta);
    }

    private static Path schemaDir() {
        String configured = System.getProperty("fxt.video.fundsxml.schema");
        Path dir = configured != null ? Path.of(configured) : Path.of("../fundsxml_schema");
        if (Files.isRegularFile(dir.resolve("FundsXML4.xsd")) && Files.isRegularFile(dir.resolve("xmldsig-core-schema.xsd"))) {
            return dir;
        }
        System.err.println("[fundsxml-video] no FundsXML schema in " + dir.toAbsolutePath()
                + " - using the test copy (4.2.9)");
        return FALLBACK_SCHEMA;
    }
}
