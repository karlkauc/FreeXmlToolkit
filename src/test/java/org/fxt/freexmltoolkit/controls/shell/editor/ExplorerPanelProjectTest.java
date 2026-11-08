package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * An open project makes its folder the Explorer's workspace, titled with the project's name, and its
 * schema bindings bind the schema of XML files opened in the editor.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelProjectTest {

    @TempDir
    Path dir;

    private ExplorerPanel panel;
    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @AfterEach
    void close() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> ProjectService.getInstance().close());
    }

    @Test
    void projectBecomesTheWorkspace() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("orders"));
        Path file = folder.resolve("orders.fxtproj");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> ProjectService.getInstance().create(file, "Orders"));
        assertEquals(folder.toAbsolutePath().normalize(), WaitForAsyncUtils.waitForAsyncFx(5000, panel::getWorkspaceFolder));
        assertEquals("PROJECT · ORDERS", WaitForAsyncUtils.waitForAsyncFx(5000, panel::getWorkspaceTitle));

        // A save keeps the workspace; renaming shows in the title
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            ProjectService.getInstance().current().orElseThrow().setName("Orders 2");
            ProjectService.getInstance().save();
            return null;
        });
        assertEquals("PROJECT · ORDERS 2", WaitForAsyncUtils.waitForAsyncFx(5000, panel::getWorkspaceTitle));

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> ProjectService.getInstance().close());
        assertEquals(folder.toAbsolutePath().normalize(), WaitForAsyncUtils.waitForAsyncFx(5000, panel::getWorkspaceFolder));
        assertEquals("ORDERS", WaitForAsyncUtils.waitForAsyncFx(5000, panel::getWorkspaceTitle));
    }

    @Test
    void projectBindingChoosesTheSchema() throws Exception {
        Files.writeString(dir.resolve("s.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"><xs:element name="r"/></xs:schema>
                """);
        Path xml = Files.createDirectories(dir.resolve("data")).resolve("a.xml");
        Files.writeString(xml, "<r/>");
        Path file = dir.resolve("p.fxtproj");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            ProjectService.getInstance().create(file, "P").bindSchema("data/**/*.xml", dir.resolve("s.xsd"));
            ProjectService.getInstance().save();
            host.openFile(xml);
            return null;
        });
        WaitForAsyncUtils.waitFor(15, java.util.concurrent.TimeUnit.SECONDS,
                () -> host.activeSchemaSourceProperty().get() == EditorHost.SchemaSource.PROJECT);
        assertEquals(dir.resolve("s.xsd").toAbsolutePath().normalize().toFile(),
                WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.activeSchemaProperty().get()));
    }
}
