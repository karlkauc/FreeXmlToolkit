package org.fxt.freexmltoolkit.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The example project of the plugin guide (examples/plugin-sample) compiles and loads as a plugin jar. */
class PluginSampleTest {

    private static final Path SAMPLE = Path.of("examples/plugin-sample/src/main");

    @AfterEach
    void reset() {
        PluginManager.getInstance().resetForTesting();
    }

    @Test
    void sampleCompilesAndLoads(@TempDir Path dir) throws Exception {
        List<String> sources;
        try (Stream<Path> files = Files.walk(SAMPLE.resolve("java"))) {
            sources = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
        }
        Path classes = Files.createDirectories(dir.resolve("classes"));
        List<String> args = new java.util.ArrayList<>(List.of("--release", "25", "--enable-preview", "-d",
                classes.toString(), "-cp", System.getProperty("java.class.path")));
        args.addAll(sources);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));

        Path jar = Files.createDirectories(dir.resolve("plugins")).resolve("orders-plugin.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out)) {
            for (Path root : List.of(classes, SAMPLE.resolve("resources"))) {
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        zip.putNextEntry(new JarEntry(root.relativize(file).toString().replace('\\', '/')));
                        zip.write(Files.readAllBytes(file));
                    }
                }
            }
        }
        PluginManager manager = PluginManager.getInstance();
        manager.loadAll();
        manager.loadFolderForTesting(jar.getParent());
        var info = manager.plugins().stream().filter(p -> p.id().equals("com.example.orders")).findFirst().orElseThrow();
        assertEquals(PluginManager.State.ACTIVE, info.state(), String.valueOf(info.error()));
        assertTrue(manager.activities().stream().anyMatch(a -> a.id().equals("orders")));
        assertTrue(manager.menuItems().stream().anyMatch(m -> m.id().equals("orders-new")));
        assertTrue(manager.stylesheets().stream().anyMatch(s -> s.endsWith("orders.css")));
        var problems = manager.validate("<order><line><product>P</product><quantity>0</quantity><price>1</price></line>"
                + "<total>5</total></order>", null);
        assertTrue(problems.stream().anyMatch(p -> p.message().equals("The quantity must be positive.")), problems.toString());
    }
}
