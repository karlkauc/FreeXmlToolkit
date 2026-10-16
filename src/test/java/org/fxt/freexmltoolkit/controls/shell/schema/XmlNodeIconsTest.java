package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.icons.IconifyIconService;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.Test;

/** Tests for {@link XmlNodeIcons}: one distinct, resolvable icon per XML node kind. */
class XmlNodeIconsTest {

    @Test
    void everyNodeKindHasAResolvableIcon() {
        List<XmlNode> nodes = List.of(new XmlElement("a"), new XmlText("t"), new XmlComment("c"),
                new XmlCData("d"), new XmlProcessingInstruction("pi", "x"), new XmlDocument());
        IconifyIconService icons = IconifyIconService.getInstance();
        for (XmlNode node : nodes) {
            String icon = XmlNodeIcons.icon(node);
            assertTrue(icons.exists(icon), () -> node.getClass().getSimpleName() + " -> unknown icon " + icon);
        }
        assertEquals("bi-code-slash", XmlNodeIcons.icon(new XmlElement("a")));
        assertEquals("bi-chat-left-text", XmlNodeIcons.icon(new XmlComment("c")));
        assertEquals(nodes.size(), nodes.stream().map(XmlNodeIcons::icon).distinct().count());
    }
}
