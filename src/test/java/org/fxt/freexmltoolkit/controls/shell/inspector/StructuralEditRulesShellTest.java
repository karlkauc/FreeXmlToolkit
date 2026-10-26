package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerField;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The reported bug end to end: an element with an inline sequence must not get {@code type="xs:int"}
 * next to it. The Inspector asks first; Cancel keeps the schema, Replace swaps the inline content
 * for the type in one undo step. Content under a simple-typed element is refused.
 */
@ExtendWith(ApplicationExtension.class)
class StructuralEditRulesShellTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="first" type="xs:string"/>
                    <xs:element name="second" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final Pattern ROOT_WITH_INT = Pattern.compile("<xs:element name=\"root\" type=\"xs:int\"\\s*/>");

    private EditorHost host;
    private InspectorPanel inspector;
    private final AtomicBoolean answer = new AtomicBoolean();
    private final List<EditCheck> asked = new CopyOnWriteArrayList<>();
    private final List<EditCheck> blocked = new CopyOnWriteArrayList<>();

    @Start
    void start(Stage stage) {
        EditRuleDialogs.setConfirmer((title, check) -> {
            asked.add(check);
            return answer.get();
        });
        EditRuleDialogs.setBlockedHandler((title, check) -> blocked.add(check));
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    @AfterEach
    void resetDialogs() {
        EditRuleDialogs.setConfirmer(null);
        EditRuleDialogs.setBlockedHandler(null);
    }

    private void open(Path xsd) throws Exception {
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("complexType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
    }

    private void select(String name) throws Exception {
        XsdNode node = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> InspectorEditTest.find(host.getActiveSchemaRoot().orElseThrow(), name));
        assertNotNull(node, "fixture node: " + name);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(node);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> name.equals(inspector.getNodeNameText()));
    }

    private void typeInPicker(FxRobot robot, String type) throws Exception {
        TypePickerField picker = (TypePickerField) inspector.lookup("#inspector-type");
        robot.clickOn(picker.getEditor());
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write(type);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, picker::isPopupShowing);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> type.equals(picker.popup().detailTitleText()));
        robot.push(KeyCode.ENTER);
    }

    private String text() {
        return host.getActiveText().orElse("");
    }

    @Test
    void typeOnElementWithSequenceAsksAndCancelKeepsTheSchema(FxRobot robot, @TempDir Path tmp) throws Exception {
        open(tmp.resolve("schema.xsd"));
        select("root");
        String before = text();
        answer.set(false);

        typeInPicker(robot, "xs:int");

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> asked.size() == 1);
        assertEquals(EditCheck.Status.REPLACEABLE, asked.getFirst().status());
        assertTrue(asked.getFirst().reason().contains("sequence of 2 elements"), asked.getFirst().reason());
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
        assertEquals(before, text(), "cancelled: the schema is unchanged");
        TypePickerField picker = (TypePickerField) inspector.lookup("#inspector-type");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> picker.getEditor().getText().isEmpty());
    }

    @Test
    void replacingSwapsTheSequenceForTheTypeInOneUndoStep(FxRobot robot, @TempDir Path tmp) throws Exception {
        open(tmp.resolve("schema.xsd"));
        select("root");
        answer.set(true);

        typeInPicker(robot, "xs:int");

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> ROOT_WITH_INT.matcher(text()).find());
        assertFalse(text().contains("<xs:sequence"), text());
        assertFalse(text().contains("name=\"first\""), text());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> text().contains("name=\"first\""));
        assertFalse(text().contains("type=\"xs:int\""), text());
        assertTrue(text().contains("<xs:sequence"), text());
    }

    @Test
    void contentUnderASimpleTypedElementIsRefused(@TempDir Path tmp) throws Exception {
        open(tmp.resolve("schema.xsd"));
        select("first");
        String before = text();

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addElementToActive("nested")));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addAttributeToActive("attr")));
        assertEquals(2, blocked.size(), "both refusals are explained");
        assertTrue(blocked.getFirst().reason().contains("simple type xs:string"), blocked.getFirst().reason());

        answer.set(false);
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addSequenceToActive()));
        assertEquals(1, asked.size(), "a sequence would replace the type: asked first");
        assertEquals(before, text());

        answer.set(true);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addSequenceToActive()));
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> !text().contains("name=\"first\" type="));
        XsdElement first = (XsdElement) WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> InspectorEditTest.find(host.getActiveSchemaRoot().orElseThrow(), "first"));
        assertNull(first.getType());
        assertTrue(first.hasCompositor());
    }
}
