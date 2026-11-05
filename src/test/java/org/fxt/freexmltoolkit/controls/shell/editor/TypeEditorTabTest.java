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

    private Tab openComplex(Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PersonType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("PersonType"));
    }

    private static <T extends javafx.scene.control.Labeled> T labeled(Tab tab, Class<T> type, String text) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> tab.getContent().lookupAll("*").stream()
                .filter(type::isInstance).map(type::cast)
                .filter(n -> text.equals(n.getText())).findFirst().orElseThrow());
    }

    private org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType personType() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveNamedTypes().stream()
                .filter(t -> "PersonType".equals(t.getName()))
                .map(org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType.class::cast)
                .findFirst().orElseThrow());
    }

    @Test
    void theCloseButtonClosesTheComplexTypeTabSoItOpensAgain(@TempDir Path tmp) throws Exception {
        Tab complex = openComplex(tmp);
        // The title chip names the root as the complex type it stands for, not as an element
        String meta = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                ((javafx.scene.control.Label) complex.getContent().lookup("#graph-title-meta")).getText());
        assertTrue(meta.startsWith("complexType"), meta);
        javafx.scene.control.Button close = labeled(complex, javafx.scene.control.Button.class, "Close");

        WaitForAsyncUtils.waitForAsyncFx(2000, close::fire);

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> complex.getTabPane() != null
                && complex.getTabPane().getTabs().contains(complex)), "Close removes the tab");
        Tab again = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("PersonType"));
        assertNotNull(again);
        assertNotSame(complex, again, "the closed tab was forgotten, a new one opens");
    }

    @Test
    void abstractIsACommandOfTheSchemaThatCanBeUndone(@TempDir Path tmp) throws Exception {
        Tab complex = openComplex(tmp);
        var type = personType();
        javafx.scene.control.CheckBox abstractBox = labeled(complex, javafx.scene.control.CheckBox.class, "Abstract");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> abstractBox.setSelected(true));
        assertTrue(type.isAbstract(), "the type in the schema is abstract");

        // Undo in the schema document reverts it, and the checkbox follows
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.openFile(tmp.resolve("schema.xsd"));
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(type.isAbstract(), "undone through the schema's command stack");
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, abstractBox::isSelected), "the checkbox follows the model");
    }

    // ---- Types used inside a type editor open in their own type editor ----

    private static String nestedXsd(String innerChild) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="OuterType">
                    <xs:sequence>
                      <xs:element name="inner" type="InnerType"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="InnerType">
                    <xs:sequence>
                      <xs:element name="%s" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """.formatted(innerChild);
    }

    private Path openSchema(Path file, String text, String marker) throws Exception {
        Files.writeString(file, text);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        return file;
    }

    private static ComplexTypeEditorView complexView(Tab tab) {
        return (ComplexTypeEditorView) ((BorderPane) tab.getContent()).getCenter();
    }

    /** The first type frame in the type editor's diagram (the frame of a type used inside it). */
    private static org.fxt.freexmltoolkit.controls.v2.view.TypeFrame firstFrame(Tab tab) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var root = complexView(tab).getGraphView().getRootNode();
            java.util.ArrayDeque<org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode> todo =
                    new java.util.ArrayDeque<>(java.util.List.of(root));
            while (!todo.isEmpty()) {
                var node = todo.poll();
                if (!node.getTypeFrames().isEmpty()) {
                    return node.getTypeFrames().getFirst();
                }
                todo.addAll(node.getChildren());
            }
            throw new AssertionError("no type frame in the type editor");
        });
    }

    /** Asks the type editor's diagram to open the type of a frame, as the frame menu / double-click do. */
    private Tab openFrameType(Tab typeTab) {
        var frame = firstFrame(typeTab);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> complexView(typeTab).getGraphView().openTypeOfFrame(frame)),
                "the type editor's diagram can open the framed type");
        WaitForAsyncUtils.waitForFxEvents();
        return WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> typeTab.getTabPane().getSelectionModel().getSelectedItem());
    }

    @Test
    void aTypeUsedInsideATypeEditorOpensInItsOwnTypeEditor(@TempDir Path tmp) throws Exception {
        openSchema(tmp.resolve("nested.xsd"), nestedXsd("value"), "OuterType");
        Tab outer = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("OuterType"));
        assertNotNull(outer);

        Tab inner = openFrameType(outer);
        assertNotSame(outer, inner, "a new tab opened");
        assertEquals("Type: InnerType", inner.getText());
        assertEquals("InnerType", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> complexView(inner).getGraphView().getRootNode().getLabel()));

        // Asking again re-selects the same tab
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> outer.getTabPane().getSelectionModel().select(outer));
        assertSame(inner, openFrameType(outer), "an open type tab is re-used");

        // Discarding the outer editor's changes rebuilds its diagram; opening still works
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            complexView(outer).reload();
            outer.getTabPane().getSelectionModel().select(outer);
        });
        assertSame(inner, openFrameType(outer), "still wired after reload");
    }

    @Test
    void aNestedTypeIsLookedUpInTheSchemaOfItsTypeEditor(@TempDir Path tmp) throws Exception {
        openSchema(tmp.resolve("a.xsd"), nestedXsd("fromA"), "fromA");
        openSchema(tmp.resolve("b.xsd"), nestedXsd("fromB"), "fromB");
        Tab outerB = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("OuterType"));

        // The first XSD tab (a.xsd) would be the fallback; the type must come from b.xsd
        Tab innerB = openFrameType(outerB);
        String child = WaitForAsyncUtils.waitForAsyncFx(2000, () -> complexView(innerB).getGraphView()
                .getRootNode().getChildren().stream()
                .flatMap(n -> java.util.stream.Stream.concat(java.util.stream.Stream.of(n), n.getChildren().stream()))
                .map(org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode::getLabel)
                .filter(l -> l.startsWith("from")).findFirst().orElse(""));
        assertEquals("fromB", child, "InnerType of b.xsd, not of a.xsd");

        // a.xsd's OuterType is a different type and gets its own tab
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(tmp.resolve("a.xsd")));
        Tab outerA = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openTypeEditorTab("OuterType"));
        assertNotSame(outerB, outerA, "equal type names of two schemas are different tabs");
    }
}
