package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link TemplateRunner} (no UI): renders an {@link XmlTemplate} with its
 * parameters substituted, reusing the existing template engine/model.
 */
class TemplateRunnerTest {

    private XmlTemplate greetingTemplate() {
        XmlTemplate t = new XmlTemplate("Greeting", "<g>${greeting} world</g>", "test");
        t.addParameter(new TemplateParameter("greeting", TemplateParameter.ParameterType.STRING, "Hello"));
        return t;
    }

    @Test
    void rendersWithDefaultParameterValues() {
        String content = TemplateRunner.render(greetingTemplate(), Map.of());
        assertFalse(content.startsWith("ERROR:"), content);
        assertEquals("<g>Hello world</g>", content.strip());
    }

    @Test
    void rendersWithProvidedParameterValues() {
        String content = TemplateRunner.render(greetingTemplate(), Map.of("greeting", "Hi"));
        assertEquals("<g>Hi world</g>", content.strip());
    }
}
