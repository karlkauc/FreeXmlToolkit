package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The OS command behind {@link DesktopActions#reveal}. */
class DesktopActionsRevealTest {

    private final File file = new File("/data/docs/a b.xml");

    @Test
    void windowsSelectsTheFileInExplorer() {
        assertEquals(List.of("explorer.exe", "/select," + file.getAbsolutePath()),
                DesktopActions.revealCommand("Windows 11", file));
    }

    @Test
    void macOsRevealsTheFileInFinder() {
        assertEquals(List.of("open", "-R", file.getAbsolutePath()),
                DesktopActions.revealCommand("Mac OS X", file));
    }

    @Test
    void linuxAsksTheFreedesktopFileManagerWithAFileUri() {
        List<String> command = DesktopActions.revealCommand("Linux", file);
        assertEquals("dbus-send", command.getFirst());
        assertTrue(command.contains("org.freedesktop.FileManager1.ShowItems"));
        assertTrue(command.contains("array:string:" + file.getAbsoluteFile().toURI()));
    }

    @Test
    void unknownSystemsHaveNoCommand() {
        assertNull(DesktopActions.revealCommand("Plan 9", file));
    }
}
