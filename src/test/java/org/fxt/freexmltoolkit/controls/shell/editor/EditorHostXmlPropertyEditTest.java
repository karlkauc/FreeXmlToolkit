package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Phase 1: a selected XML element's namespace and full attribute CRUD (add / rename key / delete)
 * commit via the XML command stack and round-trip to the editor text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostXmlPropertyEditTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void namespaceAndAttributeCrudRoundTrip(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root>\n  <item id=\"1\">v</item>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRID);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        XmlGridView grid = (XmlGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlGridView).findFirst().orElseThrow();
        XmlElement item = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            XmlElement it = grid.getContext().getDocument().getRootElement().getChildElements("item").get(0);
            grid.getContext().getSelectionModel().setSelectedNode(it);
            return it;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.activeXmlNodeProperty().get() == item);

        // Namespace prefix + URI -> prefixed tag + xmlns declaration.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveElementNamespace("ns", "http://example.com/ns")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("xmlns:ns=\"http://example.com/ns\""));
        assertTrue(host.getActiveText().orElse("").contains("<ns:item"), "tag must use the prefix");

        // Add a new attribute.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveXmlAttribute("foo", "bar")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("foo=\"bar\""));

        // Rename the original attribute key (value preserved).
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.renameActiveXmlAttribute("id", "code")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("code=\"1\""));
        assertFalse(host.getActiveText().orElse("").contains("id=\"1\""), "old attribute key must be gone");

        // Delete an attribute.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.removeActiveXmlAttribute("foo")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> !host.getActiveText().orElse("").contains("foo=\"bar\""));
    }
}
