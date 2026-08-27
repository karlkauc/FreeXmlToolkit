package org.fxt.freexmltoolkit.screenshots;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Dialog;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.CatalogImportDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryEntryDialog;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Visual verification of the Schema Library feature (not a documentation harness — output goes
 * to {@code build/visual-verify/}). Drives the real Unified Shell against a temp-dir library and
 * cache so nothing in the user's {@code ~/.freeXmlToolkit} is touched.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*SchemaLibraryVisualDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SchemaLibraryVisualDocScreenshotGenerator {

    private static final File OUT_DIR = new File("build/visual-verify");
    private static final String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";

    private Parent root;
    private UnifiedShellView shell;
    private Path work;
    private SchemaLibraryServiceImpl library;
    private SchemaResourceCache cache;
    private Path x3dXsd;
    private Path jsonSchema;

    @Start
    void start(Stage stage) throws Exception {
        work = Files.createTempDirectory("schema-library-visual");
        ServiceRegistry.initialize();
        cache = new SchemaResourceCache(work.resolve("cache"));
        ServiceRegistry.register(SchemaResourceCache.class, cache);
        library = new SchemaLibraryServiceImpl(work.resolve("schema-library.json"), cache,
                () -> SchemaLibraryServiceImpl.class.getResourceAsStream("/schema-library/bundled.json"));
        ServiceRegistry.register(SchemaLibraryService.class, library);
        seedLibrary();

        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    /** A no-namespace "X3D-like" schema, a JSON schema, a good and a broken catalog, one cached entry. */
    private void seedLibrary() throws Exception {
        x3dXsd = work.resolve("schemas").resolve("x3d-demo.xsd");
        Files.createDirectories(x3dXsd.getParent());
        Files.writeString(x3dXsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="X3D">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Scene"><xs:complexType><xs:sequence>
                        <xs:element name="Shape" minOccurs="0" maxOccurs="unbounded"/>
                      </xs:sequence></xs:complexType></xs:element>
                    </xs:sequence><xs:attribute name="version" type="xs:string"/></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        Path nsXsd = work.resolve("schemas").resolve("orders.xsd");
        Files.writeString(nsXsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:demo:orders"
                           xmlns="urn:demo:orders" elementFormDefault="qualified">
                  <xs:element name="order"><xs:complexType><xs:sequence>
                    <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>
                """);
        jsonSchema = work.resolve("schemas").resolve("person.schema.json");
        Files.writeString(jsonSchema, """
                {"$id":"https://demo.example.org/person.json","type":"object",
                 "properties":{"name":{"type":"string"},"age":{"type":"integer"}},"required":["name"]}
                """);
        library.addEntry(SchemaLibraryEntry.user("", x3dXsd.toString(), SchemaKind.XSD, "X3D demo (no namespace)", "X3D"));
        library.addEntry(SchemaLibraryEntry.user("urn:demo:orders", nsXsd.toString(), SchemaKind.XSD, "Orders demo", null));
        library.addEntry(SchemaLibraryEntry.user("https://demo.example.org/person.json", jsonSchema.toString(),
                SchemaKind.JSON_SCHEMA, "Person JSON Schema", null));
        library.addEntry(SchemaLibraryEntry.user("urn:demo:missing", work.resolve("schemas/gone.xsd").toString(),
                SchemaKind.XSD, "Entry whose file is missing", null));

        Path catalog = work.resolve("catalog.xml");
        Files.writeString(catalog, ("<catalog xmlns='%s'>\n"
                + "  <uri name='urn:demo:catalog' uri='schemas/orders.xsd'/>\n"
                + "  <system systemId='https://example.org/orders.xsd' uri='schemas/orders.xsd'/>\n"
                + "  <rewriteSystem systemIdStartString='http://example.org/schemas/' rewritePrefix='schemas/'/>\n"
                + "</catalog>\n").formatted(CATALOG_NS));
        library.addCatalog(catalog);
        Path broken = work.resolve("broken-catalog.xml");
        Files.writeString(broken, "<catalog xmlns='" + CATALOG_NS + "'><uri name='x'");
        library.addCatalog(broken);

        // One cache entry (as if downloaded earlier).
        Path cached = cache.getCacheDirectory().resolve("abc123.xsd");
        Files.createDirectories(cached.getParent());
        Files.copy(nsXsd, cached);
        cache.getCacheIndex().addOrUpdateEntry(org.fxt.freexmltoolkit.service.SchemaCacheEntry.builder()
                .localFilename("abc123.xsd").remoteUrl("https://example.org/orders.xsd")
                .downloadTimestamp(java.time.Instant.now()).fileSizeBytes(Files.size(cached)).build());
        cache.saveIndex();
    }

    @Test
    void verifySchemaLibraryVisually() throws Exception {
        OUT_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle(800);

        // 1. Mappings tab
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA_LIBRARY));
        settle(800);
        shot("01-mappings");

        // 2. Add-mapping dialog (separate window → screen grab)
        Dialog<?>[] dlg = new Dialog<?>[1];
        onFx(() -> {
            var d = new SchemaLibraryEntryDialog(library, null);
            dlg[0] = d;
            d.show();
        });
        settle(800);
        shotScreen("02-add-dialog");
        onFx(() -> dlg[0].close());
        settle(300);

        // 3. Catalogs tab
        TabPane tabs = (TabPane) shell.lookup("#schema-library-tabs");
        onFx(() -> tabs.getSelectionModel().select(1));
        settle(600);
        shot("03-catalogs");

        // 4. Import preview dialog
        List<SchemaLibraryEntry> preview = library.importCatalog(work.resolve("catalog.xml"));
        var existing = library.getEntries().stream().map(SchemaLibraryEntry::key).collect(java.util.stream.Collectors.toSet());
        onFx(() -> {
            var d = new CatalogImportDialog(preview, existing);
            dlg[0] = d;
            d.show();
        });
        settle(800);
        shotScreen("04-import-dialog");
        onFx(() -> dlg[0].close());
        settle(300);

        // 5. Cache tab
        onFx(() -> tabs.getSelectionModel().select(2));
        settle(800);
        shot("05-cache");

        // 6. Auto-bind: no-namespace document with root X3D → library entry by root element
        Path x3dDoc = work.resolve("scene.x3d.xml");
        Files.writeString(x3dDoc, "<X3D version=\"4.0\">\n  <Scene>\n    <Shape/>\n  </Scene>\n</X3D>\n");
        onFx(() -> host.openFile(x3dDoc));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
        settle(1000);
        shot("06-autobind-x3d-validation-panel");
        System.out.println("[visual] X3D bound schema = " + host.activeSchemaProperty().get());

        // 7. Auto-bind by namespace
        Path orderDoc = work.resolve("order.xml");
        Files.writeString(orderDoc, "<order xmlns=\"urn:demo:orders\">\n  <item>Widget</item>\n</order>\n");
        onFx(() -> host.openFile(orderDoc));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> {
            var f = host.activeSchemaProperty().get();
            return f != null && f.getName().equals("orders.xsd");
        });
        settle(800);
        shot("07-autobind-namespace");
        System.out.println("[visual] orders bound schema = " + host.activeSchemaProperty().get());

        // 8. JSON $schema mapped through the library
        Path jsonDoc = work.resolve("person.json");
        Files.writeString(jsonDoc, "{\n  \"$schema\": \"https://demo.example.org/person.json\",\n  \"name\": \"Ada\",\n  \"age\": \"not-a-number\"\n}\n");
        onFx(() -> host.openFile(jsonDoc));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> {
            var f = host.activeSchemaProperty().get();
            return f != null && f.getName().endsWith("person.schema.json");
        });
        settle(800);
        shot("08-autobind-json");
        System.out.println("[visual] json bound schema = " + host.activeSchemaProperty().get());

        // 8b. Shipped catalog example: declared but unreachable xsi:schemaLocation, resolved via catalog
        Path exampleCatalog = Path.of("release/examples/catalog/catalog.xml").toAbsolutePath();
        if (Files.isRegularFile(exampleCatalog)) {
            library.addCatalog(exampleCatalog);
            Path invoice = exampleCatalog.resolveSibling("invoice.xml");
            onFx(() -> host.openFile(invoice));
            WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> {
                var f = host.activeSchemaProperty().get();
                return f != null && f.getName().equals("invoice.xsd");
            });
            onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
            settle(1000);
            shot("08b-catalog-example-statusbar");
            System.out.println("[visual] catalog example bound = " + host.activeSchemaProperty().get()
                    + " source=" + host.activeSchemaSourceProperty().get());
        }

        // 9. Settings page: SCHEMA LIBRARY card
        onFx(() -> shell.getSelectionModel().select(Activity.SETTINGS));
        settle(1200);
        shot("09-settings");

        System.out.println("[visual] entries=" + library.getEntries().size() + " catalogs=" + library.getCatalogs().size()
                + " catalogErrors=" + library.catalogErrors());
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> { action.run(); return null; });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(OUT_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[visual] wrote " + out.getAbsolutePath());
    }

    private void shotScreen(String name) throws Exception {
        settle(300);
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        var img = new Robot().createScreenCapture(screen);
        File out = new File(OUT_DIR, name + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("[visual] wrote " + out.getAbsolutePath());
    }
}
