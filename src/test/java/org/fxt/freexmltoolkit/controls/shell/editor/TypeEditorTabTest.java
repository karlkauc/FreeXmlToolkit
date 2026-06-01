package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.views.ComplexTypeEditorView;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypeEditorView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Opening a named XSD type in a dedicated editor tab: a form view for a simple type, a graphical
 * view for a complex type; re-opening a type re-uses its tab; unknown names open nothing.
 */
@ExtendWith(ApplicationExtension.class)
class TypeEditorTabTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="3"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void opensSimpleAndComplexTypeTabsWithDedup(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PersonType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        // Simple type -> form view.
        Tab simple = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("Code"));
        assertNotNull(simple, "a simple type must open a tab");
        assertInstanceOf(SimpleTypeEditorView.class, simple.getContent(),
                "simple type opens the form-based editor view");

        // Re-opening the same type re-uses the tab.
        Tab simpleAgain = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("Code"));
        assertSame(simple, simpleAgain, "re-opening a type must re-use its existing tab");

        // Complex type -> graphical editor wrapped with a Save toolbar.
        Tab complex = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("PersonType"));
        assertNotNull(complex, "a complex type must open a tab");
        assertInstanceOf(BorderPane.class, complex.getContent());
        assertInstanceOf(ComplexTypeEditorView.class, ((BorderPane) complex.getContent()).getCenter(),
                "complex type opens the graphical editor view");
        assertNotSame(simple, complex, "different types open in different tabs");

        // Unknown / non-type name opens nothing.
        Tab none = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("DoesNotExist"));
        assertNull(none, "an unknown type name must not open a tab");

        // Cosmetic polish: the Type Library stays populated while a type tool tab is focused
        // (a complex-type tab is the active tab at this point, not an XSD document tab).
        var names = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveNamedTypes().stream()
                        .map(org.fxt.freexmltoolkit.controls.v2.model.XsdNode::getName).toList());
        assertTrue(names.contains("Code") && names.contains("PersonType"),
                "named types must stay listed while a type tab is focused, was: " + names);
    }
}
