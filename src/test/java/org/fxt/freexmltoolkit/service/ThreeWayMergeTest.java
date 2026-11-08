package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.service.ThreeWayMerge.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link ThreeWayMerge}. */
@DisplayName("ThreeWayMerge")
class ThreeWayMergeTest {

    private static final String BASE = """
            <order>
              <id>1</id>
              <customer>Ada</customer>
              <item>A</item>
              <item>B</item>
              <note>none</note>
            </order>
            """;

    @Test
    @DisplayName("changes on different lines from both sides are combined")
    void clean() {
        String left = BASE.replace("<customer>Ada</customer>", "<customer>Ada Lovelace</customer>");
        String right = BASE.replace("<note>none</note>", "<note>urgent</note>")
                .replace("<item>B</item>\n", "<item>B</item>\n  <item>C</item>\n");
        Result result = ThreeWayMerge.merge(BASE, left, right, false);
        assertEquals(0, result.conflicts());
        assertEquals("""
                <order>
                  <id>1</id>
                  <customer>Ada Lovelace</customer>
                  <item>A</item>
                  <item>B</item>
                  <item>C</item>
                  <note>urgent</note>
                </order>
                """, result.text("L", "B", "R", false));
    }

    @Test
    @DisplayName("same change on both sides is no conflict; different changes are, with markers")
    void conflicts() {
        String left = BASE.replace("<id>1</id>", "<id>2</id>").replace("none", "left note");
        String right = BASE.replace("<id>1</id>", "<id>2</id>").replace("none", "right note");
        Result result = ThreeWayMerge.merge(BASE, left, right, false);
        assertEquals(1, result.conflicts());
        String text = result.text("mine.xml", "base.xml", "theirs.xml", true);
        assertTrue(text.contains("  <id>2</id>\n"), text);
        assertTrue(text.contains("""
                <<<<<<< mine.xml
                  <note>left note</note>
                ||||||| base.xml
                  <note>none</note>
                =======
                  <note>right note</note>
                >>>>>>> theirs.xml
                """), text);

        List<ThreeWayMerge.Block> blocks = ThreeWayMerge.blocks(text);
        assertEquals(1, blocks.size());
        assertEquals("  <note>left note</note>\n", blocks.get(0).left());
        assertEquals("  <note>none</note>\n", blocks.get(0).base());
        assertEquals("  <note>right note</note>\n", blocks.get(0).right());
        assertTrue(text.substring(blocks.get(0).start()).startsWith("<<<<<<<"));

        List<ThreeWayMerge.Block> noBase = ThreeWayMerge.blocks(result.text("a", "b", "c", false));
        assertNull(noBase.get(0).base());
    }

    @Test
    @DisplayName("whitespace-only differences between both changes are not a conflict when ignored")
    void whitespace() {
        String left = BASE.replace("<note>none</note>", "<note>new</note>");
        String right = BASE.replace("<note>none</note>", "<note>new</note>  ");
        assertEquals(1, ThreeWayMerge.merge(BASE, left, right, false).conflicts());
        Result relaxed = ThreeWayMerge.merge(BASE, left, right, true);
        assertEquals(0, relaxed.conflicts());
        assertEquals(left, relaxed.text("L", "B", "R", false), "the left version wins");
    }

    @Test
    @DisplayName("adjacent changes conflict (like Git); a missing final newline survives")
    void edges() {
        String base = "a\nb\nc";
        assertEquals("A\nb\nC", ThreeWayMerge.merge(base, "A\nb\nc", "a\nb\nC", false).text("", "", "", false));
        assertEquals(1, ThreeWayMerge.merge(base, "A\nb\nc", "a\nB\nc", false).conflicts());
        assertEquals("x", ThreeWayMerge.merge("", "x", "", false).text("", "", "", false));
        assertEquals(List.of("a\n", "b"), ThreeWayMerge.lines("a\nb"));
    }
}
