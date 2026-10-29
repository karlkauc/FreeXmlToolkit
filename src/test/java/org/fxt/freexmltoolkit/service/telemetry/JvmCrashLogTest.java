package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The JVM's crash file identifies a native crash of a dead session: signal, faulting library or
 * method and thread — and nothing else (no paths, no addresses).
 */
class JvmCrashLogTest {

    private static final String WINDOWS = """
            #
            # A fatal error has been detected by the Java Runtime Environment:
            #
            #  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb12345678, pid=4242, tid=4321
            #
            # JRE version: OpenJDK Runtime Environment (25.0+36) (build 25+36)
            # Java VM: OpenJDK 64-Bit Server VM (25+36, mixed mode, sharing, tiered, compressed oops, g1 gc, windows-amd64)
            # Problematic frame:
            # C  [fontmanager.dll+0x1a2b3]
            #
            # No core dump will be written. Minidumps are not enabled by default on client versions of Windows
            #

            ---------------  S U M M A R Y ------------

            Command Line: C:\\Users\\someone\\AppData\\Local\\FreeXmlToolkit\\app\\FreeXmlToolkit.cfg

            ---------------  T H R E A D  ---------------

            Current thread (0x000001d2c3b4a000):  JavaThread "JavaFX Application Thread" [_thread_in_native, id=4321, stack(0x0000,0x0001)]
            """;

    private static final String LINUX_JAVA_FRAME = """
            #
            # A fatal error has been detected by the Java Runtime Environment:
            #
            #  SIGSEGV (0xb) at pc=0x00007f1234567890, pid=77, tid=78
            #
            # JRE version: OpenJDK Runtime Environment (25.0+36) (build 25+36)
            # Problematic frame:
            # J 12345 c2 org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2.lambda$ignoreFlowlessHitRace$3(Ljavafx/event/Event;)V (120 bytes) @ 0x00007f12 [0x00007f10+0x0000002]
            #

            Current thread (0x00007f0000001000):  JavaThread "pool-3-thread-1" [_thread_in_Java, id=78]
            """;

    @Test
    void readsSignalLibraryAndThreadFromAWindowsCrashFile() {
        Optional<JvmCrashLog.Crash> crash = JvmCrashLog.parse(WINDOWS.lines().toList());

        assertTrue(crash.isPresent());
        assertEquals("EXCEPTION_ACCESS_VIOLATION", crash.get().signal());
        assertEquals("C fontmanager.dll", crash.get().frame());
        assertEquals("JavaFX Application Thread", crash.get().thread());
    }

    @Test
    void reducesAJavaFrameToClassAndMethod() {
        Optional<JvmCrashLog.Crash> crash = JvmCrashLog.parse(LINUX_JAVA_FRAME.lines().toList());

        assertTrue(crash.isPresent());
        assertEquals("SIGSEGV", crash.get().signal());
        assertEquals("J org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2#lambda$ignoreFlowlessHitRace$3",
                crash.get().frame());
        assertEquals("pool-3-thread-1", crash.get().thread());
    }

    @Test
    void internalErrorAndLibraryPathsAreReducedToNames() {
        assertEquals("V libjvm.so", JvmCrashLog.frameOf("# V  [/usr/lib/jvm/lib/server/libjvm.so+0x12ab]  Method::foo()+0x1"));
        assertEquals("C jfxwebkit.dll", JvmCrashLog.frameOf("# C  [C:\\Program Files\\FreeXmlToolkit\\runtime\\bin\\jfxwebkit.dll+0x1]"));
        assertEquals("j org.foo.Bar#baz", JvmCrashLog.frameOf("# j  org.foo.Bar.baz()V+12"));
        Optional<JvmCrashLog.Crash> internal = JvmCrashLog.parse(List.of(
                "#", "# A fatal error has been detected by the Java Runtime Environment:", "#",
                "#  Internal Error (sharedRuntime.cpp:1234), pid=5, tid=6",
                "#  fatal error: OutOfMemory encountered: Java heap space", "#"));
        assertTrue(internal.isPresent());
        assertEquals("Internal Error", internal.get().signal());
        assertNull(internal.get().frame());
    }

    @Test
    void somethingThatIsNotACrashFileIsIgnored() {
        assertTrue(JvmCrashLog.parse(List.of("hello", "# Problematic frame:", "# C [x.dll+0x1]")).isEmpty());
        assertTrue(JvmCrashLog.parse(List.of()).isEmpty());
    }

    @Test
    void findsTheFileOfThePidWrittenAfterTheProcessStarted(@TempDir Path dir) throws Exception {
        Path other = dir.resolve("other");
        Files.createDirectories(other);
        Path file = other.resolve("hs_err_pid4242.log");
        Files.writeString(file, WINDOWS);
        JvmCrashLog log = new JvmCrashLog(List.of(dir.resolve("missing"), dir, other));

        assertTrue(log.find(4242, 0).isPresent(), "any file counts when the start time is unknown");
        assertTrue(log.find(4242, System.currentTimeMillis() - 60_000).isPresent());
        assertTrue(log.find(4243, 0).isEmpty(), "another pid");
        assertTrue(log.find(0, 0).isEmpty());

        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 3_600_000));
        assertTrue(log.find(4242, System.currentTimeMillis() - 60_000).isEmpty(),
                "a file older than the dead process belongs to an earlier process with the same pid");
        assertTrue(Files.exists(file), "the file is left in place");
    }
}
