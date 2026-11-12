package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EditedWithCommentTest {

    private static final String C = EditedWithComment.comment("3.1.0", "Ada Lovelace", "Analytical Engines");

    @Test
    void commentNamesVersionUrlAndUser() {
        assertEquals("<!-- Edited with FreeXmlToolkit 3.1.0 (https://github.com/karlkauc/FreeXmlToolkit) by Ada "
                + "Lovelace (Analytical Engines) -->", C);
        assertEquals("<!-- Edited with FreeXmlToolkit 3.1.0 (https://github.com/karlkauc/FreeXmlToolkit) -->",
                EditedWithComment.comment("3.1.0", "", "Corp"));
        assertFalse(EditedWithComment.comment("1", "a--b", "x\ny").contains("a--b"));
    }

    @Test
    void insertedAfterTheDeclaration() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root/>\n";
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + C + "\n<root/>\n",
                EditedWithComment.apply(xml, C));
        assertEquals("<?xml version=\"1.0\"?>\r\n" + C + "\r\n<root/>",
                EditedWithComment.apply("<?xml version=\"1.0\"?>\r\n<root/>", C));
        assertEquals("<?xml version=\"1.0\"?>\n" + C + "\n<root/>",
                EditedWithComment.apply("<?xml version=\"1.0\"?><root/>", C));
    }

    @Test
    void insertedAtTheStartWithoutDeclarationAndKeepsTheBom() {
        assertEquals(C + "\n<root/>", EditedWithComment.apply("<root/>", C));
        assertEquals("﻿" + C + "\n<root/>", EditedWithComment.apply("﻿<root/>", C));
    }

    @Test
    void anEarlierCommentIsReplacedNotRepeated() {
        String once = EditedWithComment.apply("<?xml version=\"1.0\"?>\n<!DOCTYPE r [<!ELEMENT r ANY>]>\n<r/>",
                EditedWithComment.comment("3.0.0", "", ""));
        String twice = EditedWithComment.apply(once, C);
        assertEquals(1, twice.split("Edited with FreeXmlToolkit", -1).length - 1, twice);
        assertTrue(twice.contains("3.1.0") && !twice.contains("3.0.0"), twice);
        assertEquals(twice, EditedWithComment.apply(twice, C));
    }

    @Test
    void otherCommentsAndTheBodyStayUntouched() {
        String xml = "<?xml version=\"1.0\"?>\n<!-- edited with XMLSpy v2024 -->\n<r><!-- Edited with FreeXmlToolkit inside --></r>";
        String result = EditedWithComment.apply(xml, C);
        assertTrue(result.contains("<!-- edited with XMLSpy v2024 -->"));
        assertTrue(result.contains("<r><!-- Edited with FreeXmlToolkit inside --></r>"));
        assertTrue(result.startsWith("<?xml version=\"1.0\"?>\n" + C + "\n"), result);
    }

    @Test
    void notXmlIsUnchanged() {
        assertEquals("", EditedWithComment.apply("", C));
        assertEquals("plain text", EditedWithComment.apply("plain text", C));
        assertEquals("<?xml version=\"1.0\"?>", EditedWithComment.apply("<?xml version=\"1.0\"?>", C));
    }

    @Test
    void settingDefaultsToOn() {
        assertTrue(EditedWithComment.enabled(key -> null));
        assertFalse(EditedWithComment.enabled(Map.of(EditedWithComment.KEY, "false")::get));
        String xml = "<r/>";
        assertEquals(xml, EditedWithComment.applyIfEnabled(xml, Map.of(EditedWithComment.KEY, "false")::get));
        assertTrue(EditedWithComment.applyIfEnabled(xml, k -> null).startsWith("<!-- Edited with FreeXmlToolkit"));
    }
}
