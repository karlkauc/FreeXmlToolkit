package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

class MacGlassOpenUrlsTest {

    @Test
    void splitsFileUrlsFromOtherUrls() {
        var split = MacGlassOpenUrls.split(List.of(
                new MacGlassOpenUrls.OpenedUrl("/tmp/a.xml"),
                new MacGlassOpenUrls.OpenedUrl(null),
                new MacGlassOpenUrls.OpenedUrl("/tmp/b b.xsd")));

        assertEquals(List.of(Path.of("/tmp/a.xml"), Path.of("/tmp/b b.xsd")), split.files());
        assertTrue(split.hasOtherUrls());
    }

    @Test
    void onlyFileUrlsLeaveNothingForGlass() {
        var split = MacGlassOpenUrls.split(List.of(new MacGlassOpenUrls.OpenedUrl("/tmp/a.json")));

        assertEquals(List.of(Path.of("/tmp/a.json")), split.files());
        assertFalse(split.hasOtherUrls());
    }

    @Test
    void blankPathsAreNoFiles() {
        var split = MacGlassOpenUrls.split(List.of(new MacGlassOpenUrls.OpenedUrl("  ")));

        assertTrue(split.files().isEmpty());
        assertTrue(split.hasOtherUrls());
    }

    /**
     * Sends {@code application:openURLs:} to a Glass delegate exactly as AppKit does for a Finder
     * double-click and checks that the files arrive. Fails when a JavaFX update changes the method
     * the bridge replaces.
     */
    @Test
    @EnabledOnOs(OS.MAC)
    void glassOpenUrlsReachesTheConsumer(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("open me.xml"), "<root/>");
        List<Path> opened = new CopyOnWriteArrayList<>();

        assertTrue(MacGlassOpenUrls.install(opened::addAll), "bridge not installed");

        NativeLibrary objc = NativeLibrary.getInstance("objc");
        Function getClass = objc.getFunction("objc_getClass");
        Function sel = objc.getFunction("sel_registerName");
        Function send = objc.getFunction("objc_msgSend");
        java.util.function.BiFunction<Object, Object[], Pointer> msg = (receiver, rest) -> {
            Object[] args = new Object[rest.length + 2];
            args[0] = receiver;
            args[1] = sel.invoke(Pointer.class, new Object[]{rest[0]});
            System.arraycopy(rest, 1, args, 2, rest.length - 1);
            return (Pointer) send.invoke(Pointer.class, args);
        };

        Pointer nsString = msg.apply(getClass.invoke(Pointer.class, new Object[]{"NSString"}),
                new Object[]{"stringWithUTF8String:", file.toString()});
        Pointer url = msg.apply(getClass.invoke(Pointer.class, new Object[]{"NSURL"}),
                new Object[]{"fileURLWithPath:", nsString});
        Pointer urls = msg.apply(getClass.invoke(Pointer.class, new Object[]{"NSArray"}),
                new Object[]{"arrayWithObject:", url});
        Pointer delegate = msg.apply(getClass.invoke(Pointer.class, new Object[]{"GlassApplication"}),
                new Object[]{"alloc"});

        msg.apply(delegate, new Object[]{"application:openURLs:", Pointer.NULL, urls});

        assertEquals(List.of(file.toRealPath()), opened.stream().map(MacGlassOpenUrlsTest::real).toList());
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (java.io.IOException e) {
            return path;
        }
    }
}
