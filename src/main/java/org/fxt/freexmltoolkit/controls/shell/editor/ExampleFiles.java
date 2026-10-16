package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The small example the Welcome page offers on a first start ("Open an Example", UX plan 5.1):
 * an order XML bound to its XSD, plus an XSLT that turns it into an HTML summary. The files are
 * bundled as resources and copied to a writable folder on first use, so they can be edited,
 * validated and transformed like any other file. Existing copies are kept (the user may have
 * changed them).
 */
public final class ExampleFiles {

    private static final String RESOURCE_DIR = "/examples/getting-started/";

    /** The bundled files; the first one is the document that is opened. */
    static final List<String> FILES = List.of("order.xml", "order.xsd", "order-summary.xslt");

    private ExampleFiles() {
    }

    /** @return the folder the example is copied to ({@code ~/.freeXmlToolkit/examples/getting-started}) */
    public static Path defaultTarget() {
        return Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "examples", "getting-started");
    }

    /**
     * Copies the example into {@code target} (missing files only) and returns the document to open.
     *
     * @throws IOException if a file cannot be written or a resource is missing
     */
    public static Path install(Path target) throws IOException {
        Files.createDirectories(target);
        for (String name : FILES) {
            Path file = target.resolve(name);
            if (Files.exists(file)) {
                continue;
            }
            try (InputStream in = ExampleFiles.class.getResourceAsStream(RESOURCE_DIR + name)) {
                if (in == null) {
                    throw new IOException("Bundled example file is missing: " + name);
                }
                Files.copy(in, file);
            }
        }
        return target.resolve(FILES.getFirst());
    }
}
