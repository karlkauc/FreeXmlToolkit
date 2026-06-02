package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The template parameter dialog builds one input per parameter and reads back values/defaults. */
@ExtendWith(ApplicationExtension.class)
class TemplateParameterDialogTest {

    @Start
    void start(Stage stage) {
    }

    private XmlTemplate parameterizedTemplate() {
        XmlTemplate t = new XmlTemplate("Greeting", "<hi to=\"${who}\" loud=\"${loud}\" lang=\"${lang}\"/>", "test");
        t.addParameter(TemplateParameter.stringParam("who", "world"));
        t.addParameter(TemplateParameter.boolParam("loud", true));
        t.addParameter(TemplateParameter.enumParam("lang", "en", "de"));
        return t;
    }

    @Test
    void readsDefaultValuesForEachParameter() {
        XmlTemplate template = parameterizedTemplate();
        Map<String, String> values = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TemplateParameterDialog dialog = new TemplateParameterDialog(template);
            return dialog.readValues();
        });
        assertEquals(3, values.size(), "one value per parameter");
        assertEquals("world", values.get("who"), "string default");
        assertEquals("true", values.get("loud"), "boolean default");
        assertEquals("en", values.get("lang"), "enum picks the first/declared default");
    }

    @Test
    void renderingWithReadValuesSubstitutesParameters() {
        XmlTemplate template = parameterizedTemplate();
        Map<String, String> values = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> new TemplateParameterDialog(template).readValues());
        String rendered = TemplateRunner.render(template, values);
        assertFalse(rendered.startsWith("ERROR"), rendered);
        assertTrue(rendered.contains("to=\"world\""), rendered);
        assertFalse(rendered.contains("${who}"), "parameters must be substituted: " + rendered);
    }
}
