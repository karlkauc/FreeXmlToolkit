package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Exercises the REAL "Go to Definition" production path (not just the wired handler):
 * bind a schema to an open XML document, place the caret inside an element, invoke the
 * actual {@code ContextMenuManagerV2.goToDefinition()} (the right-click action), and verify
 * the bound XSD is opened. This covers the schema-dependent steps the handler-level
 * {@link EditorHostGoToDefinitionTest} bypasses (hasSchema → XPath → findBestMatchingElement →
 * navigateToDefinition).
 */
@ExtendWith(ApplicationExtension.class)
class GoToDefinitionRealPathTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void rightClickGoToDefinitionOpensTheBoundXsd(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="child" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>""");
        String xmlText = "<root><child>x</child></root>";
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, xmlText);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitForFxEvents();

        // The file content is loaded asynchronously; wait until the editor actually holds the
        // XML text before touching the caret (moveTo on an empty CodeArea throws IOOBE).
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> Boolean.TRUE.equals(
                WaitForAsyncUtils.waitForAsyncFx(1000, () -> {
                    EditorView v = host.activeEditorView();
                    return v != null && v.getText() != null && v.getText().contains("<child>");
                })));

        // Bind the schema (synchronous loadSchema → hasSchema() becomes true).
        Boolean bound = WaitForAsyncUtils.waitForAsyncFx(4000,
                () -> host.setSchemaForActiveDocument(xsd.toFile()));
        assertTrue(Boolean.TRUE.equals(bound), "schema should bind to the active XML document");

        XmlEditorView view = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            EditorView v = host.activeEditorView();
            return v instanceof XmlEditorView xev ? xev : null;
        });
        assertNotNull(view, "active view should be the XML editor");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.getEditorContext().hasSchema()),
                "the editor context must report a bound schema");

        // Place the caret inside the <child> element (computed from the live editor text),
        // then invoke the real right-click action.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            String live = view.getCodeArea().getText();
            int caret = live.indexOf("<child>") + "<child>".length(); // inside <child> content
            view.getCodeArea().moveTo(caret);
            view.getContextMenuManager().goToDefinition();
            return null;
        });

        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getPath() != null
                        && "schema.xsd".equals(d.getPath().getFileName().toString())));
        assertTrue(host.getOpenDocuments().stream()
                        .anyMatch(d -> d.getPath() != null
                                && "schema.xsd".equals(d.getPath().getFileName().toString())),
                "Go to Definition (real path) should open the bound XSD");
    }
}
