package org.fxt.freexmltoolkit.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding, registering and isolating plugins; validators in the validation run. */
@DisplayName("PluginManager")
class PluginManagerTest {

    @TempDir
    Path dir;

    private final PluginManager manager = PluginManager.getInstance();

    @BeforeEach
    void setUp() {
        ServiceRegistry.initialize();
        manager.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.get(PropertiesService.class).set(PluginManager.enabledKey("test.off"), "true");
        manager.resetForTesting();
    }

    /** A plugin contributing a little of everything. */
    static final class TestPlugin implements FxtPlugin {
        private final String id;
        private final boolean fail;

        TestPlugin(String id, boolean fail) {
            this.id = id;
            this.fail = fail;
        }

        @Override public String id() { return id; }
        @Override public String name() { return "Test " + id; }
        @Override public String version() { return "1.0"; }

        @Override
        public void register(PluginContext context) {
            context.addActivity(new PluginActivity(id + "-activity", "Test", "bi-puzzle", ui -> null));
            context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.TOOLS, id + "-tool", "Test Tool", null, ui -> { }, null));
            context.addValidator(new DocumentValidator() {
                @Override public String name() { return "No foo"; }
                @Override public boolean accepts(String content, String documentName) { return content.contains("<root"); }
                @Override public List<Problem> validate(String content, String documentName) {
                    return content.contains("<foo") ? List.of(new Problem("warning", 2, "foo is not allowed")) : List.of();
                }
            });
            context.addElementDescriber(element -> element.name().equals("foo")
                    ? "Foo in " + element.rootName() : null);
            if (fail) {
                throw new IllegalStateException("broken on purpose");
            }
        }
    }

    @Test
    @DisplayName("the built-in FundsXML plugin is found on the class path")
    void builtIn() {
        manager.loadAll();
        var fundsXml = manager.plugins().stream().filter(p -> p.id().equals("fundsxml")).findFirst().orElseThrow();
        assertEquals(PluginManager.Origin.BUILT_IN, fundsXml.origin());
        assertEquals(PluginManager.State.ACTIVE, fundsXml.state());
        assertTrue(manager.activities().stream().anyMatch(a -> a.id().equals("fundsxml")));
        assertTrue(manager.menuItems().stream().anyMatch(m -> m.id().equals("fundsxml-site")));
        assertEquals(2, manager.startupTasks().size(), "content sync and snippets");
    }

    @Test
    @DisplayName("contributions are kept, a failing register keeps none, ids are unique, disabled plugins are skipped")
    void registration() {
        manager.loadAll();
        int activities = manager.activities().size();
        manager.registerForTesting(new TestPlugin("test.ok", false));
        manager.registerForTesting(new TestPlugin("test.broken", true));
        manager.registerForTesting(new TestPlugin("test.ok", false));
        ServiceRegistry.get(PropertiesService.class).set(PluginManager.enabledKey("test.off"), "false");
        manager.registerForTesting(new TestPlugin("test.off", false));

        assertEquals(activities + 1, manager.activities().size(), "only the working plugin contributes");
        assertEquals(PluginManager.State.ACTIVE, state("test.ok"));
        assertEquals(PluginManager.State.FAILED, state("test.broken"));
        assertTrue(manager.plugins().stream().anyMatch(p -> p.state() == PluginManager.State.FAILED
                && p.error().contains("Another extension has the id test.ok")));
        assertEquals(PluginManager.State.DISABLED, state("test.off"));
    }

    @Test
    @DisplayName("element describers answer for the elements they know; a failing one is skipped")
    void describers() {
        manager.registerForTesting(new TestPlugin("test.ok", false));
        manager.registerForTesting(new FxtPlugin() {
            @Override public String id() { return "test.describe-throws"; }
            @Override public String name() { return "Throws"; }
            @Override public void register(PluginContext context) {
                context.addElementDescriber(element -> {
                    throw new IllegalStateException("boom");
                });
            }
        });
        var root = new org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement("root");
        var foo = new org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement("foo");
        root.addChild(foo);
        assertTrue(manager.hasElementDescribers());
        assertEquals("Foo in root", org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ElementDescriptions.of(foo));
        assertEquals(null, org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ElementDescriptions.of(root));
        assertEquals(List.of(), manager.describe(null));
    }

    @Test
    @DisplayName("validators run with the XML validation; a failing one becomes an error")
    void validators() {
        manager.registerForTesting(new TestPlugin("test.ok", false));
        var result = ValidationRunner.runWithReport("<root>\n<foo/></root>", null, null, "a.xml");
        assertTrue(result.problems().stream().anyMatch(p -> p.source().equals("No foo") && p.line() == 2
                && p.severity().equals("warning") && p.message().equals("foo is not allowed")), result.problems().toString());
        assertTrue(ValidationRunner.runWithReport("<other><foo/></other>", null, null, "a.xml").problems().isEmpty(),
                "not accepted, not checked");

        manager.registerForTesting(new FxtPlugin() {
            @Override public String id() { return "test.throws"; }
            @Override public String name() { return "Throws"; }
            @Override public void register(PluginContext context) {
                context.addValidator(new DocumentValidator() {
                    @Override public String name() { return "Throws"; }
                    @Override public boolean accepts(String content, String documentName) { return true; }
                    @Override public List<Problem> validate(String content, String documentName) {
                        throw new IllegalStateException("boom");
                    }
                });
            }
        });
        var problems = manager.validate("<x/>", null);
        assertTrue(problems.stream().anyMatch(p -> p.source().equals("Throws") && p.message().contains("boom")), problems.toString());
    }

    @Test
    @DisplayName("a jar in the plugins folder is loaded in its own class loader")
    void externalJar() throws Exception {
        Path src = Files.createDirectories(dir.resolve("src/ext"));
        Files.writeString(src.resolve("ExtPlugin.java"), """
                package ext;
                import org.fxt.freexmltoolkit.plugin.*;
                public class ExtPlugin implements FxtPlugin {
                    public String id() { return "ext.sample"; }
                    public String name() { return "Sample"; }
                    public String version() { return "2.0"; }
                    public void register(PluginContext context) {
                        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "ext-help", "Sample Help",
                                "bi-info-circle", ui -> { }, null));
                    }
                }
                """);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, "-d", classes.toString(), "-cp",
                System.getProperty("java.class.path"), src.resolve("ExtPlugin.java").toString()));
        Path plugins = Files.createDirectories(dir.resolve("plugins"));
        try (OutputStream out = Files.newOutputStream(plugins.resolve("sample.jar"));
             JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("ext/ExtPlugin.class"));
            jar.write(Files.readAllBytes(classes.resolve("ext/ExtPlugin.class")));
            jar.putNextEntry(new JarEntry("META-INF/services/" + FxtPlugin.class.getName()));
            jar.write("ext.ExtPlugin\n".getBytes(StandardCharsets.UTF_8));
        }
        Files.writeString(plugins.resolve("empty.jar"), "not a jar");

        manager.loadAll();
        manager.loadFolderForTesting(plugins);
        var sample = manager.plugins().stream().filter(p -> p.id().equals("ext.sample")).findFirst().orElseThrow();
        assertEquals(PluginManager.Origin.EXTERNAL, sample.origin());
        assertEquals("2.0", sample.version());
        assertEquals(plugins.resolve("sample.jar"), sample.jar());
        assertTrue(manager.menuItems().stream().anyMatch(m -> m.id().equals("ext-help")));
        assertTrue(manager.plugins().stream().anyMatch(p -> p.id().equals("empty.jar")
                && p.state() == PluginManager.State.FAILED), manager.plugins().toString());
    }

    private PluginManager.State state(String id) {
        return manager.plugins().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().state();
    }
}
