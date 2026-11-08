package org.fxt.freexmltoolkit.service.largefile;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import org.fxt.freexmltoolkit.service.XercesXmlValidationService;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Validates an XML file of any size against an XML Schema (1.0 or 1.1) while reading it as a stream:
 * memory stays flat, the file is read once. Without a schema only well-formedness is checked. The
 * run stops after a number of problems, and can be cancelled. UI-free; used by the Large File Viewer.
 */
public final class LargeFileValidation {

    /** One problem. */
    public record Problem(String severity, long line, int column, String message) {
    }

    /**
     * The result.
     *
     * @param problems    the problems found (at most the limit)
     * @param truncated   whether the run stopped at the limit
     * @param schemaError why the schema could not be used, or {@code null}
     * @param fatal       whether the document is not well-formed (validation stopped at that point)
     */
    public record Result(List<Problem> problems, boolean truncated, String schemaError, boolean fatal) {

        /** @return whether the document is valid (or well-formed without a schema) */
        public boolean valid() {
            return problems.isEmpty() && schemaError == null;
        }
    }

    /** Thrown by the handler to stop at the limit. */
    private static final class Stop extends SAXException {
        Stop() {
            super("stopped");
        }
    }

    private LargeFileValidation() {
    }

    /**
     * @param schema    the XML Schema, or {@code null} for a well-formedness check
     * @param maxErrors stop after this many problems
     * @param progress  receives the bytes read (may be {@code null})
     * @param cancelled polled while reading (may be {@code null})
     */
    public static Result validate(Path xml, File schema, int maxErrors, LongConsumer progress, BooleanSupplier cancelled)
            throws IOException {
        List<Problem> problems = new ArrayList<>();
        boolean[] fatal = {false};
        ErrorHandler handler = new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) throws SAXException {
                add("Warning", e);
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                add("Error", e);
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                fatal[0] = true;
                add("Fatal", e);
            }

            private void add(String severity, SAXParseException e) throws SAXException {
                problems.add(new Problem(severity, Math.max(0, e.getLineNumber()), Math.max(0, e.getColumnNumber()),
                        String.valueOf(e.getMessage())));
                if (problems.size() >= maxErrors) {
                    throw new Stop();
                }
            }
        };
        XercesXmlValidationService service = new XercesXmlValidationService();
        try (InputStream in = new Progress(Files.newInputStream(xml), progress, cancelled)) {
            service.validateStream(in, xml.toUri().toString(), schema, handler);
        } catch (Stop stop) {
            return new Result(problems, true, null, fatal[0]);
        } catch (SAXParseException e) {
            // a fatal error already reported to the handler ends the parse
            if (problems.isEmpty()) {
                problems.add(new Problem("Fatal", Math.max(0, e.getLineNumber()), Math.max(0, e.getColumnNumber()),
                        String.valueOf(e.getMessage())));
            }
            return new Result(problems, false, null, true);
        } catch (SAXException e) {
            if (e.getCause() instanceof CancellationException c) {
                throw c; // NOPMD - the cancellation itself is the exception to report
            }
            return new Result(problems, false, String.valueOf(e.getMessage()), false);
        } catch (Cancelled e) {
            CancellationException stopped = new CancellationException("Validation cancelled");
            stopped.initCause(e);
            throw stopped;
        }
        return new Result(problems, false, null, fatal[0]);
    }

    /**
     * The schema the document declares ({@code xsi:schemaLocation} / {@code noNamespaceSchemaLocation}),
     * or the Schema Library's entry for its root, read from the first 64 KB.
     */
    public static Optional<File> declaredSchema(Path xml) {
        try (InputStream in = Files.newInputStream(xml)) {
            byte[] head = in.readNBytes(64 * 1024);
            String text = new String(head, StandardCharsets.UTF_8);
            int rootEnd = rootStartTagEnd(text);
            if (rootEnd < 0) {
                return Optional.empty();
            }
            Path parent = xml.toAbsolutePath().getParent();
            return org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner.declaredSchema(
                    text.substring(0, rootEnd) + "/>", parent == null ? null : parent.toFile());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** @return the offset of the {@code >} of the root element's start tag, or -1 */
    static int rootStartTagEnd(String text) {
        int at = 0;
        while (true) {
            int lt = text.indexOf('<', at);
            if (lt < 0 || lt + 1 >= text.length()) {
                return -1;
            }
            char next = text.charAt(lt + 1);
            if (next == '?') {
                at = text.indexOf("?>", lt) < 0 ? text.length() : text.indexOf("?>", lt) + 2;
            } else if (text.startsWith("<!--", lt)) {
                at = text.indexOf("-->", lt) < 0 ? text.length() : text.indexOf("-->", lt) + 3;
            } else if (next == '!') {
                int depth = 0;
                int i = lt;
                for (; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '[') {
                        depth++;
                    } else if (c == ']') {
                        depth--;
                    } else if (c == '>' && depth <= 0) {
                        break;
                    }
                }
                at = i + 1;
            } else {
                char quote = 0;
                for (int i = lt; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (quote != 0) {
                        if (c == quote) {
                            quote = 0;
                        }
                    } else if (c == '"' || c == '\'') {
                        quote = c;
                    } else if (c == '>') {
                        return text.charAt(i - 1) == '/' ? i - 1 : i;
                    }
                }
                return -1;
            }
        }
    }

    private static final class Cancelled extends IOException {
        Cancelled() {
            super("cancelled");
        }
    }

    /** Counts bytes and checks for cancellation every megabyte. */
    private static final class Progress extends FilterInputStream {
        private final LongConsumer progress;
        private final BooleanSupplier cancelled;
        private long read;
        private long reported;

        Progress(InputStream in, LongConsumer progress, BooleanSupplier cancelled) {
            super(new java.io.BufferedInputStream(in, 1 << 16));
            this.progress = progress;
            this.cancelled = cancelled;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read - reported >= 1 << 20) {
                reported = read;
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new Cancelled();
                }
                if (progress != null) {
                    progress.accept(read);
                }
            }
        }
    }
}
