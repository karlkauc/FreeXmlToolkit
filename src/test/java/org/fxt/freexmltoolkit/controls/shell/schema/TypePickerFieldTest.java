package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerPopup.Header;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerPopup.Item;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerPopup.Row;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The type picker offers schema and built-in types with explanations, filters while typing,
 * commits exactly once per user action and never on programmatic population.
 */
@ExtendWith(ApplicationExtension.class)
class TypePickerFieldTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="ISINType">
                <xs:annotation><xs:documentation>International Securities Identification Number</xs:documentation></xs:annotation>
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{2}[A-Z0-9]{9}[0-9]"/></xs:restriction>
              </xs:simpleType>
              <xs:complexType name="AmountType"><xs:sequence/></xs:complexType>
            </xs:schema>
            """;

    private TypePickerField field;
    private TextField other;
    private final List<String> commits = new CopyOnWriteArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        field = new TypePickerField();
        field.setId("picker");
        field.setChoices(() -> TypeChoices.of(schema));
        field.setOnCommit(commits::add);
        field.setValue("xs:string");
        other = new TextField();
        other.setId("other");
        stage.setScene(new Scene(new VBox(8, field, other), 500, 120));
        stage.show();
    }

    private List<Row> rows() throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> field.popup().rows());
    }

    private void waitForPopup(boolean showing) throws Exception {
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> field.isPopupShowing() == showing);
    }

    @Test
    void buttonOpensGroupedListWithCurrentValueHighlighted(FxRobot robot) throws Exception {
        robot.clickOn("#picker .fxt-type-picker-button");
        waitForPopup(true);
        List<Row> rows = rows();
        assertEquals(new Header("Simple types"), rows.getFirst());
        assertTrue(rows.contains(new Header("Complex types")));
        assertTrue(rows.contains(new Header("Numeric")));
        assertTrue(rows.stream().anyMatch(r -> r instanceof Item i && i.choice().qualifiedName().equals("ISINType")));
        assertTrue(rows.stream().anyMatch(r -> r instanceof Item i && i.choice().qualifiedName().equals("xs:dateTime")));
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS,
                () -> "xs:string".equals(field.popup().detailTitleText()));
        assertEquals("xs:string", WaitForAsyncUtils.waitForAsyncFx(2000, () -> field.popup().selectedChoice().qualifiedName()),
                "the current value is pre-highlighted");
        assertTrue(commits.isEmpty(), "opening the popup commits nothing");
    }

    @Test
    void typingFiltersAndShowsTheExplanation(FxRobot robot) throws Exception {
        robot.clickOn("#picker .text-field");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("dateT");
        waitForPopup(true);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> rows().size() == 3);
        List<Row> rows = rows();
        assertEquals(new Header("Date & Time"), rows.get(0));
        assertEquals("xs:dateTime", ((Item) rows.get(1)).choice().qualifiedName());
        assertEquals("xs:dateTimeStamp", ((Item) rows.get(2)).choice().qualifiedName());
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "xs:dateTime".equals(field.popup().detailTitleText()));
        assertTrue(field.popup().detailDocText().contains("date with a time of day"), field.popup().detailDocText());

        robot.push(KeyCode.DOWN);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "xs:dateTimeStamp".equals(field.popup().detailTitleText()));
        robot.push(KeyCode.ENTER);
        waitForPopup(false);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> commits.size() == 1);
        assertEquals(List.of("xs:dateTimeStamp"), commits);
        assertEquals("xs:dateTimeStamp", field.getValue());
        assertEquals("xs:dateTimeStamp", field.getEditor().getText());
    }

    @Test
    void exactMatchRanksAheadOfLongerNames(FxRobot robot) throws Exception {
        robot.clickOn("#picker .text-field");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("xs:int");
        waitForPopup(true);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "xs:int".equals(field.popup().detailTitleText()));
        List<Row> rows = rows();
        assertEquals("xs:int", ((Item) rows.get(1)).choice().qualifiedName(), "exact match first");
        assertEquals("xs:integer", ((Item) rows.get(2)).choice().qualifiedName());
        robot.push(KeyCode.ENTER);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> commits.size() == 1);
        assertEquals(List.of("xs:int"), commits);
    }

    @Test
    void schemaTypesExplainThemselvesFromDocumentation(FxRobot robot) throws Exception {
        robot.clickOn("#picker .text-field");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("isin");
        waitForPopup(true);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "ISINType".equals(field.popup().detailTitleText()));
        assertEquals("International Securities Identification Number", field.popup().detailDocText());
        robot.push(KeyCode.ENTER);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> commits.size() == 1);
        assertEquals(List.of("ISINType"), commits);
    }

    @Test
    void setValueIsSilentAndEscapeRestores(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            field.setValue("xs:int");
            return null;
        });
        assertEquals("xs:int", field.getEditor().getText());
        assertTrue(commits.isEmpty(), "setValue must not commit");

        robot.clickOn("#picker .text-field");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("foo");
        waitForPopup(true);
        robot.push(KeyCode.ESCAPE);          // closes the popup
        waitForPopup(false);
        assertEquals("foo", field.getEditor().getText());
        robot.push(KeyCode.ESCAPE);          // restores the committed value
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "xs:int".equals(field.getEditor().getText()));
        assertTrue(commits.isEmpty());
    }

    @Test
    void freeTextCommitsOnEnterAndOnBlurOnce(FxRobot robot) throws Exception {
        robot.clickOn("#picker .text-field");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("foo:Bar");
        waitForPopup(true);
        assertTrue(rows().isEmpty(), "no type matches foo:Bar");
        robot.push(KeyCode.ENTER);
        waitForPopup(false);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> commits.size() == 1);
        assertEquals(List.of("foo:Bar"), commits);

        robot.push(KeyCode.END);
        robot.write("2");
        waitForPopup(true);
        robot.push(KeyCode.ESCAPE);
        waitForPopup(false);
        robot.clickOn("#other");
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> commits.size() == 2);
        assertEquals("foo:Bar2", commits.get(1), "leaving the field commits the edited text once");
        WaitForAsyncUtils.sleep(200, TimeUnit.MILLISECONDS);
        assertEquals(2, commits.size());
    }
}
