package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Errors of the JavaFX render thread, which JavaFX only prints, are noticed on stderr. */
class RenderErrorMonitorTest {

    /** The trace a user saw while scrolling a large HTML report in the preview (Windows, Direct3D). */
    static final String D3D_TRACE = """
            java.lang.NullPointerException: Cannot invoke "com.sun.prism.d3d.D3DTextureData.getContext()" because the return value of "com.sun.prism.d3d.D3DTextureResource.getResource()" is null
                    at javafx.graphics/com.sun.prism.d3d.D3DTexture.getContext(D3DTexture.java:85)
                    at javafx.graphics/com.sun.prism.d3d.D3DTexture.update(D3DTexture.java:210)
                    at javafx.graphics/com.sun.prism.d3d.D3DTexture.update(D3DTexture.java:154)
                    at javafx.graphics/com.sun.prism.impl.BaseContext.flushMask(BaseContext.java:115)
                    at javafx.graphics/com.sun.javafx.tk.quantum.PresentingPainter.run(PresentingPainter.java:88)
                    at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:545)
                    at java.base/java.lang.Thread.run(Thread.java:1474)
            """;

    @Test
    void aPrismTraceIsReportedOnceWithItsFramesAndPassesThrough() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<RenderErrorMonitor.RenderPipelineError> seen = new ArrayList<>();
        RenderErrorMonitor monitor = new RenderErrorMonitor(out, seen::add);
        for (int i = 0; i < 3; i++) { // JavaFX prints it again at every frame
            D3D_TRACE.lines().forEach(monitor::println);
        }
        monitor.println("next line");

        assertEquals(1, seen.size(), "once per session");
        RenderErrorMonitor.RenderPipelineError error = seen.getFirst();
        assertTrue(error.getMessage().startsWith("java.lang.NullPointerException: Cannot invoke"), error.getMessage());
        StackTraceElement top = error.getStackTrace()[0];
        assertEquals("com.sun.prism.d3d.D3DTexture", top.getClassName());
        assertEquals("getContext", top.getMethodName());
        assertEquals(85, top.getLineNumber());
        assertEquals(7, error.getStackTrace().length);
        assertTrue(monitor.hasReported());
        String written = out.toString(StandardCharsets.UTF_8);
        assertTrue(written.contains("D3DTexture.getContext(D3DTexture.java:85)"), "stderr gets every line unchanged");
    }

    @Test
    void otherTracesAreLeftAlone() {
        List<RenderErrorMonitor.RenderPipelineError> seen = new ArrayList<>();
        RenderErrorMonitor monitor = new RenderErrorMonitor(new ByteArrayOutputStream(), seen::add);
        """
                Exception in thread "main" java.lang.IllegalStateException: something else
                \tat org.fxt.freexmltoolkit.Foo.bar(Foo.java:12)
                \tat java.base/java.lang.Thread.run(Thread.java:1474)
                done
                """.lines().forEach(monitor::println);
        assertTrue(seen.isEmpty());
        assertFalse(monitor.hasReported());
    }
}
