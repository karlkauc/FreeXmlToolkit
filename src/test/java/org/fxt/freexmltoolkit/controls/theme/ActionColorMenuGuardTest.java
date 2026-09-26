package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Ratchet for the unified action-colour rule (spec §3): menu classes paint icons through
 * {@link ActionColor} only, never through a raw semantic ColorToken, so Cut/Copy/Paste/… cannot
 * drift apart between editors again.
 */
class ActionColorMenuGuardTest {

    private static final List<String> MENU_FILES = List.of(
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/menu/XsdContextMenuFactory.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shared/utilities/XmlContextMenuManager.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/ContextMenuManagerV2.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/jsoneditor/editor/JsonContextMenuManager.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlGridContextMenu.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/jsoneditor/grid/JsonGridContextMenu.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/schema/NodeContextMenu.java");

    private static final Pattern RAW_TOKEN =
            Pattern.compile("ColorToken\\.(SUCCESS|DANGER|WARNING|INFO|PRIMARY|NEUTRAL|PURPLE|TEAL|INDIGO|ACCENT)\\b");

    @Test
    void menuIconsUseActionColorOnly() throws IOException {
        for (String file : MENU_FILES) {
            String src = Files.readString(Path.of(file));
            assertFalse(RAW_TOKEN.matcher(src).find(),
                    () -> file + " paints a menu icon with a raw ColorToken; use ActionColor");
            assertTrue(src.contains("ActionColor."), () -> file + " does not use ActionColor at all");
        }
        assertFalse(Files.exists(Path.of("src/main/java/org/fxt/freexmltoolkit/util/ContextMenuFactory.java")),
                "dead util/ContextMenuFactory must be gone");
    }

    @Test
    void changeTypeSubItemsAreStructureNotCreate() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/fxt/freexmltoolkit/controls/jsoneditor/grid/JsonGridContextMenu.java"));
        assertTrue(src.contains("createColoredIcon(typeIcon(type), ActionColor.STRUCTURE)"),
                "JSON grid 'Change Type' entries change structure (spec §3), they do not create content");
        assertFalse(src.contains("createColoredIcon(typeIcon(type), ActionColor.CREATE)"));
    }
}
