package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdTreeView;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies {@link EditorHost#getActiveSearchTarget()}: the shell's search bar
 * must bind the XSD Tree/Graphic views (and the XML grid) in structured modes
 * and fall back to the code area ({@code null} target) in Text mode.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostSearchTargetTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="item" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private UnifiedShellView shell;
    private EditorHost host;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1100, 700));
        stage.show();
        host = shell.getEditorHost();
    }

    private void openTemp(String suffix, String content) throws Exception {
        Path file = Files.createTempFile("search-target", suffix);
        Files.writeString(file, content);
        file.toFile().deleteOnExit();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(file));
        WaitForAsyncUtils.waitForFxEvents();
        // The document content loads asynchronously; the structured views parse the
        // editor text, so wait until it is actually present before switching modes.
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> {
            var codeArea = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea());
            return codeArea != null && !codeArea.getText().isBlank();
        });
    }

    @Test
    void xsdViewModesYieldTheMatchingSearchTarget() throws Exception {
        openTemp(".xsd", XSD);

        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSearchTarget()),
                "Text mode binds the code area, not a structured target");

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertInstanceOf(XsdTreeView.class,
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSearchTarget()),
                "Tree mode exposes the XSD tree as search target");

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertInstanceOf(XsdGraphView.class,
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSearchTarget()),
                "Graphic mode exposes the XSD diagram as search target");

        // And the target actually finds schema nodes.
        var target = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSearchTarget());
        assertEquals(1, (int) WaitForAsyncUtils.waitForAsyncFx(2000, () -> target.findAll("item")));
    }

    @Test
    void xmlGraphicModeExposesTheGridSearchTarget() throws Exception {
        openTemp(".xml", "<order><title>book</title><quantity>2</quantity></order>");

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        var target = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSearchTarget());
        assertNotNull(target, "XML Graphic (grid) mode must expose the canvas search target");
        // The grid groups repeating elements, so match on a unique value instead of a tag count.
        assertEquals(1, (int) WaitForAsyncUtils.waitForAsyncFx(2000, () -> target.findAll("book")));
    }
}
