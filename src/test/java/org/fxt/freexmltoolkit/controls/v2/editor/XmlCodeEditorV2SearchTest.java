package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Case-insensitive search runs on the text itself: no lower-cased copy, and offsets stay exact. */
class XmlCodeEditorV2SearchTest {

    @Test
    void findsIgnoringCase() {
        String text = "<Root><item>Value</item><ITEM/></Root>";
        assertEquals(7, XmlCodeEditorV2.indexOfIgnoreCase(text, "item", 0));
        assertEquals(19, XmlCodeEditorV2.indexOfIgnoreCase(text, "item", 8)); // in </item>
        assertEquals(25, XmlCodeEditorV2.indexOfIgnoreCase(text, "item", 20)); // <ITEM/>
        assertEquals(-1, XmlCodeEditorV2.indexOfIgnoreCase(text, "item", 26));
        assertEquals(25, XmlCodeEditorV2.lastIndexOfIgnoreCase(text, "ITEM", text.length()));
        assertEquals(19, XmlCodeEditorV2.lastIndexOfIgnoreCase(text, "Item", 24));
        assertEquals(7, XmlCodeEditorV2.lastIndexOfIgnoreCase(text, "Item", 18));
    }

    @Test
    void offsetsDoNotDriftBeforeCharactersWhoseLowerCaseIsLonger() {
        // "İ" (U+0130) lower-cases to two chars: searching a lower-cased copy reported 4 instead of 2
        String text = "İİabc";
        assertEquals(2, XmlCodeEditorV2.indexOfIgnoreCase(text, "ABC", 0));
        assertEquals(2, XmlCodeEditorV2.lastIndexOfIgnoreCase(text, "abc", text.length()));
    }

    @Test
    void edgeCases() {
        assertEquals(-1, XmlCodeEditorV2.indexOfIgnoreCase("ab", "abc", 0));
        assertEquals(0, XmlCodeEditorV2.indexOfIgnoreCase("abc", "ABC", -5));
        assertEquals(-1, XmlCodeEditorV2.lastIndexOfIgnoreCase("ab", "abc", 5));
    }
}
