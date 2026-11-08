package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.DataFormatConverter.Format;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Mapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.google.gson.JsonParser;

/** Tests Tools → Convert to JSON / XML on the active document, and the options dialog. */
@ExtendWith(ApplicationExtension.class)
class DataConversionActionTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @AfterEach
    void resetHook() {
        DataConversionAction.dialogAnswer = null;
    }

    @Test
    void whichTypesConvert() {
        assertTrue(DataConversionAction.canConvert(EditorFileType.XML, Format.JSON));
        assertTrue(DataConversionAction.canConvert(EditorFileType.XSD, Format.JSON));
        assertFalse(DataConversionAction.canConvert(EditorFileType.XML, Format.XML));
        assertTrue(DataConversionAction.canConvert(EditorFileType.JSON, Format.XML));
        assertFalse(DataConversionAction.canConvert(EditorFileType.JSON, Format.JSON));
        assertTrue(DataConversionAction.canConvert(EditorFileType.YAML, Format.JSON));
        assertTrue(DataConversionAction.canConvert(EditorFileType.YAML, Format.XML));
        assertFalse(DataConversionAction.canConvert(EditorFileType.XQUERY, Format.JSON));
        assertFalse(DataConversionAction.canConvert(null, Format.JSON));
        assertEquals(EditorFileType.YAML, EditorFileType.fromFileName("ci.yml"));
        assertEquals("order", DataConversionAction.baseName("order.xml"));
        assertEquals("Untitled-2", DataConversionAction.baseName("*Untitled-2"));
    }

    @Test
    void convertsXmlToJsonInANewTab(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order id=\"7\"><item>A</item><item>B</item></order>");
        open(xml, "<order");
        DataConversionAction.dialogAnswer = dialog -> Optional.of(dialog.options(
                DataConversionAction.savedOptions(Format.JSON).withMapping(Mapping.STRUCTURED).withTypedValues(true)));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> DataConversionAction.convertActive(host, null, Format.JSON));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false)
                && host.getActiveText().map(t -> t.contains("order")).orElse(false));
        assertEquals("order.json", host.getActiveDocument().orElseThrow().getDisplayName().replace("*", "").strip());
        assertEquals(JsonParser.parseString("{\"order\": {\"@id\": \"7\", \"item\": [\"A\", \"B\"]}}"),
                JsonParser.parseString(host.getActiveText().orElse("")),
                "the dialog's typed-values checkbox (off by default) decides, not the base options");
    }

    @Test
    void convertsYamlToXml(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "server:\n  port: 8080\n  hosts: [a, b]\n");
        open(yaml, "server");
        assertEquals(EditorFileType.YAML, host.getActiveDocument().orElseThrow().getFileType());
        DataConversionAction.dialogAnswer = dialog -> Optional.of(dialog.options(DataConversionAction.savedOptions(Format.XML)));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> DataConversionAction.convertActive(host, null, Format.XML));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.XML).orElse(false)
                && host.getActiveText().map(t -> t.contains("<server>")).orElse(false));
        String text = host.getActiveText().orElse("");
        assertTrue(text.contains("<server>") && text.contains("<port>8080</port>") && text.contains("<hosts>b</hosts>"),
                text);
    }

    @Test
    void convertsJsonToYamlWithoutAsking(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\"a\": [1, 2], \"b\": \"x\"}");
        open(json, "\"a\"");
        DataConversionAction.dialogAnswer = dialog -> {
            throw new AssertionError("JSON → YAML has no options to ask for");
        };
        assertTrue(DataConversionAction.canConvert(EditorFileType.JSON, Format.YAML));
        assertFalse(DataConversionAction.canConvert(EditorFileType.YAML, Format.YAML));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> DataConversionAction.convertActive(host, null, Format.YAML));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.YAML).orElse(false)
                && host.getActiveText().map(t -> t.contains("b: x")).orElse(false));
        assertEquals("a:\n- 1\n- 2\nb: x\n", host.getActiveText().orElse(""));
        assertEquals("data.yaml", host.getActiveDocument().orElseThrow().getDisplayName().replace("*", "").strip());
    }

    @Test
    void dialogShowsTheOptionsOfTheMapping() throws Exception {
        DataConversionDialog toXml = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new DataConversionDialog(
                Format.JSON, Format.XML, DataConversionAction.savedOptions(Format.XML).withMapping(Mapping.STRUCTURED)));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            assertFalse(toXml.typedValuesBox().isVisible(), "typed values only for XML → JSON");
            assertTrue(toXml.rootNameField().getParent().isVisible());
            toXml.rootNameField().setText("1bad");
            assertTrue(toXml.isRootNameErrorShown());
            toXml.rootNameField().setText("data");
            assertFalse(toXml.isRootNameErrorShown());
            toXml.w3cMappingButton().setSelected(true);
            assertFalse(toXml.rootNameField().getParent().isVisible(), "the W3C mapping has no options");
            assertEquals(Mapping.W3C, toXml.options(DataConversionAction.savedOptions(Format.XML)).mapping());
        });
    }

    private void open(Path file, String marker) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
    }
}
