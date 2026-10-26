package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeCardinalityCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.NewAttributeSpec;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.NewElementSpec;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Add Element / Add Attribute dialog turns its fields into a spec and checks them live. */
@ExtendWith(ApplicationExtension.class)
class NewNodeDialogTest {

    private XsdSchema schema;

    @Start
    void start(Stage stage) throws Exception {
        schema = new XsdNodeFactory().fromString("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="order">
                    <xs:complexType><xs:sequence><xs:element name="id"/></xs:sequence></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
    }

    private <T> T fx(java.util.concurrent.Callable<T> call) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, call);
    }

    private <T> T lookup(NewNodeDialog dialog, String id, Class<T> type) {
        // The collapsed "More" pane has no skin yet, so its content is not a scene-graph child.
        javafx.scene.Node node = dialog.getDialogPane().lookup("#" + id);
        if (node == null) {
            var more = (javafx.scene.control.TitledPane) dialog.getDialogPane().lookup("#new-node-more");
            node = more.getContent().lookup("#" + id);
        }
        return type.cast(node);
    }

    @Test
    void elementFieldsBecomeTheSpec() {
        XsdElement order = (XsdElement) schema.getChildren().get(0);
        NewNodeDialog dialog = fx(() -> new NewNodeDialog(order, false, () -> schema, "newElement"));

        NewElementSpec spec = fx(() -> {
            lookup(dialog, "new-node-name", javafx.scene.control.TextField.class).setText("line");
            lookup(dialog, "new-node-type", TypePickerField.class).getEditor().setText("xs:int");
            @SuppressWarnings("unchecked")
            var min = (javafx.scene.control.Spinner<Integer>) lookup(dialog, "new-node-min", javafx.scene.control.Spinner.class);
            min.getValueFactory().setValue(0);
            lookup(dialog, "new-node-unbounded", javafx.scene.control.CheckBox.class).setSelected(true);
            lookup(dialog, "new-node-documentation", javafx.scene.control.TextArea.class).setText("A line.");
            lookup(dialog, "new-node-nillable", javafx.scene.control.CheckBox.class).setSelected(true);
            return dialog.elementSpec();
        });

        assertEquals("line", spec.name());
        assertEquals("xs:int", spec.type());
        assertEquals(0, spec.minOccurs());
        assertEquals(ChangeCardinalityCommand.UNBOUNDED, spec.maxOccurs());
        assertEquals("A line.", spec.documentation());
        assertTrue(spec.nillable());
        assertNull(spec.form(), "schema default form is not written");
        assertNull(fx(dialog::validate));
        assertFalse(fx(() -> lookup(dialog, "new-node-use", javafx.scene.control.ComboBox.class).isVisible()),
                "use is an attribute property");
        assertFalse(fx(() -> lookup(dialog, "new-node-more", javafx.scene.control.TitledPane.class).isExpanded()),
                "the less common properties start collapsed");
    }

    @Test
    void invalidInputShowsTheReason() {
        XsdElement order = (XsdElement) schema.getChildren().get(0);
        NewNodeDialog dialog = fx(() -> new NewNodeDialog(order, false, () -> schema, "newElement"));

        String error = fx(() -> {
            lookup(dialog, "new-node-default", javafx.scene.control.TextField.class).setText("a");
            lookup(dialog, "new-node-fixed", javafx.scene.control.TextField.class).setText("b");
            return dialog.validate();
        });
        assertNotNull(error);
        assertTrue(fx(() -> lookup(dialog, "new-node-error", javafx.scene.control.Label.class).isVisible()));

        String nameError = fx(() -> {
            lookup(dialog, "new-node-fixed", javafx.scene.control.TextField.class).setText("");
            lookup(dialog, "new-node-name", javafx.scene.control.TextField.class).setText("my line");
            return dialog.validate();
        });
        assertTrue(nameError.contains("not a valid XML name"), nameError);
    }

    @Test
    void topLevelElementHidesCardinalityAndAttributeShowsUse() {
        NewNodeDialog root = fx(() -> new NewNodeDialog(schema, false, () -> schema, "newElement"));
        assertFalse(fx(() -> lookup(root, "new-node-min", javafx.scene.control.Spinner.class).isVisible()
                && lookup(root, "new-node-min", javafx.scene.control.Spinner.class).getParent().isVisible()));
        assertTrue(fx(() -> lookup(root, "new-node-abstract", javafx.scene.control.CheckBox.class).isVisible()));
        assertEquals(1, fx(() -> root.elementSpec().minOccurs()));

        XsdElement order = (XsdElement) schema.getChildren().get(0);
        NewNodeDialog attr = fx(() -> new NewNodeDialog(order, true, () -> schema, "newAttribute"));
        NewAttributeSpec spec = fx(() -> {
            @SuppressWarnings("unchecked")
            var use = (javafx.scene.control.ComboBox<String>) lookup(attr, "new-node-use", javafx.scene.control.ComboBox.class);
            use.getSelectionModel().select("required");
            return attr.attributeSpec();
        });
        assertEquals("required", spec.use());
        assertEquals("newAttribute", spec.name());
        assertNull(spec.type(), "no type unless one is entered");
    }
}
