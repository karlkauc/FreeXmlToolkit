package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Switching a small XSD with {@code xs:include} to the Tree view reads the included file off the FX
 * thread (telemetry: 194 s frozen for a 1.8 KB XSD whose includes lay on a slow file system). The
 * included file is a named pipe: reading it blocks until the test writes to it, as a cloud folder or
 * network share that does not answer.
 */
@ExtendWith(ApplicationExtension.class)
class EditorTabSlowIncludeTest {

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private static final String MAIN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="common.xsd"/>
              <xs:element name="ref" type="Code"/>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void anIncludeThatDoesNotAnswerLeavesTheUiResponsive(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"), "named pipes: Linux only");
        Path common = tmp.resolve("common.xsd");
        Process mkfifo = new ProcessBuilder("mkfifo", common.toString()).start();
        assumeTrue(mkfifo.waitFor(10, TimeUnit.SECONDS) && mkfifo.exitValue() == 0, "mkfifo not available");
        Path main = tmp.resolve("main.xsd");
        Files.writeString(main, MAIN);

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openFile(main));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:include")).orElse(false));

        // Nobody writes to the pipe yet: a parse on the FX thread would hang here.
        long elapsedMs = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            long t0 = System.nanoTime();
            host.setActiveViewMode(ViewMode.TREE);
            return (System.nanoTime() - t0) / 1_000_000;
        });
        assertTrue(elapsedMs < 1000, "the view switch must not wait for the include, took " + elapsedMs + " ms");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> true), "the FX thread must stay responsive");

        // The file system answers: the switch completes and the included type resolves.
        PipeWriter writer = new PipeWriter(common);
        writer.start();
        try {
            WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                    () -> host.activeViewModeProperty().get() == ViewMode.TREE);
            var types = WaitForAsyncUtils.waitForAsyncFx(5000,
                    () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
            assertTrue(types.contains("Code"), "the included type must resolve, was: " + types);
        } finally {
            writer.finish();
        }
    }

    /** Serves the included schema to every reader that opens the pipe (parse, checksum, change monitor). */
    private static final class PipeWriter extends Thread {
        private final Path pipe;
        private volatile boolean stop;

        PipeWriter(Path pipe) {
            super("fifo-writer");
            this.pipe = pipe;
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!stop) {
                try (FileOutputStream out = new FileOutputStream(pipe.toFile())) { // blocks until a reader opens
                    if (!stop) {
                        out.write(COMMON.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    return;
                }
                try {
                    // A reader still holding the pipe open would let the next open succeed at once
                    // and read the schema twice: give it time to finish and close.
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        /** Stops the loop; a read-write open releases a writer waiting for a reader. */
        void finish() throws Exception {
            stop = true;
            try (RandomAccessFile release = new RandomAccessFile(pipe.toFile(), "rw")) {
                join(2000);
            }
        }
    }
}
