package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerField;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerPopup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The inspector edits XSD node properties in place (Figma sections stay), committing
 * through the EditorHost command stack and round-tripping to the text.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorEditTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="ISINType">
                <xs:annotation><xs:documentation>Securities identifier</xs:documentation></xs:annotation>
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="child" type="xs:string" minOccurs="1" maxOccurs="1"/>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:string" use="required"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    private void selectInTree(String name, Path xsd) throws Exception {
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("complexType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        XsdNode node = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(host.getActiveSchemaRoot().orElseThrow(), name));
        assertNotNull(node, "fixture node: " + name);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(node);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> name.equals(inspector.getNodeNameText()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void editingMaxOccursRoundTripsToText(@TempDir Path tmp) throws Exception {
        selectInTree("child", tmp.resolve("schema.xsd"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Spinner<Integer> max = (Spinner<Integer>) inspector.lookup("#inspector-max");
            max.getValueFactory().setValue(5);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("maxOccurs=\"5\""));
        assertTrue(host.getActiveText().orElse("").contains("maxOccurs=\"5\""),
                "editing maxOccurs in the inspector must round-trip to the text");
    }

    @Test
    void useFieldIsAttributeOnly(@TempDir Path tmp) throws Exception {
        selectInTree("child", tmp.resolve("schema.xsd")); // element
        // Wait for the element selection to settle into "Use hidden" (the inspector refresh is
        // debounced, so assert on the combined condition rather than a transient snapshot).
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "child".equals(inspector.getNodeNameText())
                        && !((ComboBox<?>) inspector.lookup("#inspector-use")).isVisible());
        assertFalse(((ComboBox<?>) inspector.lookup("#inspector-use")).isVisible(),
                "Use must be hidden for elements");

        XsdNode attr = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(host.getActiveSchemaRoot().orElseThrow(), "id"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(attr);
            return null;
        });
        // Wait for the attribute selection to settle into "Use visible" (name + visibility together).
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "id".equals(inspector.getNodeNameText())
                        && ((ComboBox<?>) inspector.lookup("#inspector-use")).isVisible());
        assertTrue(((ComboBox<?>) inspector.lookup("#inspector-use")).isVisible(),
                "Use must be visible for attributes");
    }

    @Test
    void changingTypeViaPickerRoundTripsToText(FxRobot robot, @TempDir Path tmp) throws Exception {
        selectInTree("child", tmp.resolve("schema.xsd"));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "xs:string".equals(inspector.getTypeText()));
        TypePickerField picker = (TypePickerField) inspector.lookup("#inspector-type");
        assertNotNull(picker, "the inspector's Type row is a TypePickerField");
        robot.clickOn(picker.getEditor());
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("xs:int");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, picker::isPopupShowing);
        robot.push(KeyCode.ENTER);
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("type=\"xs:int\""));
        assertTrue(host.getActiveText().orElse("").contains("type=\"xs:int\""),
                "committing a type in the inspector must round-trip to the text; picker value="
                        + picker.getValue() + ", editor=" + picker.getEditor().getText());
    }

    @Test
    void pickerOffersSchemaTypesWithExplanation(@TempDir Path tmp) throws Exception {
        selectInTree("child", tmp.resolve("schema.xsd"));
        TypePickerField picker = (TypePickerField) inspector.lookup("#inspector-type");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            picker.showPopup();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, picker::isPopupShowing);
        List<TypePickerPopup.Row> rows = WaitForAsyncUtils.waitForAsyncFx(2000, () -> picker.popup().rows());
        assertEquals(new TypePickerPopup.Header("Simple types"), rows.getFirst());
        assertTrue(rows.stream().anyMatch(r -> r instanceof TypePickerPopup.Item i
                && i.choice().qualifiedName().equals("ISINType")
                && "Securities identifier".equals(i.choice().details().get().documentation())),
                "the schema's own simple type is offered with its documentation");
        assertTrue(rows.stream().anyMatch(r -> r instanceof TypePickerPopup.Item i
                && i.choice().qualifiedName().equals("xs:dateTimeStamp")), "built-ins are offered");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            picker.hidePopup();
            return null;
        });
    }

    static XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
