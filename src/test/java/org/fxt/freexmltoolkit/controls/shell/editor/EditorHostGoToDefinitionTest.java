package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.core.NavigationRequest;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the shell wires the XML editor's "Go to Definition" hook
 * ({@link EditorContext#getGoToDefinitionHandler()}) to {@code EditorHost.openXsdAndReveal},
 * so right-click → Go to Definition opens the XSD instead of falling back to an info dialog.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostGoToDefinitionTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void openingXmlWiresGoToDefinitionToOpenTheXsd(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xs:element name=\"foo\" type=\"xs:string\"/></xs:schema>");
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<foo>x</foo>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitForFxEvents();

        // The active XML editor's context now has a non-null go-to-definition handler.
        EditorContext ctx = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            EditorView v = host.activeEditorView();
            return v instanceof XmlEditorView xev ? xev.getEditorContext() : null;
        });
        assertNotNull(ctx, "active view should be an XML editor with an EditorContext");
        assertNotNull(ctx.getGoToDefinitionHandler(), "go-to-definition handler must be wired");

        // Invoking the handler opens the XSD as a document in the host.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ctx.getGoToDefinitionHandler().accept(new NavigationRequest(xsd.toFile(), "foo"));
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null
                        && "schema.xsd".equals(d.getPath().getFileName().toString())));
        assertTrue(host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null
                        && "schema.xsd".equals(d.getPath().getFileName().toString())),
                "Go to Definition should open the XSD in the editor host");
    }
}
