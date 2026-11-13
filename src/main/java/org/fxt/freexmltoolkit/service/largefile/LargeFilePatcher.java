package org.fxt.freexmltoolkit.service.largefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Writes changes into a large file without loading it: byte ranges are replaced in one streaming
 * pass into a temporary file next to it, which then replaces the original (atomically where the
 * file system can). The bytes outside the ranges are copied unchanged. A cancelled or failed run
 * leaves the original as it was.
 */
public final class LargeFilePatcher {

    /** Bytes copied per step (cancel and progress are checked between steps). */
    private static final long STEP = 64L * 1024 * 1024;

    /**
     * One replacement.
     *
     * @param start the first byte replaced
     * @param end   the offset after the last byte replaced
     * @param bytes what replaces them
     */
    public record Patch(long start, long end, byte[] bytes) {
        public Patch {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid range " + start + ".." + end);
            }
        }
    }

    /**
     * The outcome.
     *
     * @param size   the new file size
     * @param backup the backup of the old file, or {@code null}
     */
    public record Result(long size, Path backup) {
    }

    private LargeFilePatcher() {
    }

    /**
     * Applies the patches.
     *
     * @param expectedSize     the file size the patches were made for
     * @param expectedModified the modification time (ms) the patches were made for
     * @param keepBackup       whether the old file is kept as {@code <name>.bak}
     * @param progress         receives the bytes written so far (may be {@code null})
     * @param cancelled        polled between steps (may be {@code null})
     * @throws IOException when the file changed since, the patches overlap or leave the file, there is
     *                     not enough space, or writing fails
     */
    public static Result apply(Path file, long expectedSize, long expectedModified, List<Patch> patches,
                               boolean keepBackup, LongConsumer progress, BooleanSupplier cancelled) throws IOException {
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        if (size != expectedSize || modified != expectedModified) {
            throw new IOException(file.getFileName() + " was changed on disk; reload it before applying changes.");
        }
        List<Patch> sorted = new ArrayList<>(patches);
        sorted.sort(Comparator.comparingLong(Patch::start));
        long newSize = size;
        long previousEnd = 0;
        for (Patch patch : sorted) {
            if (patch.start() < previousEnd || patch.end() > size) {
                throw new IOException("The changes overlap or lie outside the file.");
            }
            previousEnd = patch.end();
            newSize += patch.bytes().length - (patch.end() - patch.start());
        }
        Path folder = file.toAbsolutePath().getParent();
        FileStore store = Files.getFileStore(folder);
        if (store.getUsableSpace() < newSize) {
            throw new IOException("Not enough free space in " + folder + " to write " + newSize + " bytes.");
        }
        Path temp = folder.resolve("." + file.getFileName() + ".fxt-" + Long.toHexString(ThreadLocalRandom.current().nextLong())
                + ".tmp");
        boolean done = false;
        try {
            long written = 0;
            try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ);
                 FileChannel out = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                long at = 0;
                for (Patch patch : sorted) {
                    written = copy(in, out, at, patch.start(), written, progress, cancelled);
                    ByteBuffer replacement = ByteBuffer.wrap(patch.bytes());
                    while (replacement.hasRemaining()) {
                        out.write(replacement);
                    }
                    written += patch.bytes().length;
                    at = patch.end();
                }
                copy(in, out, at, size, written, progress, cancelled);
                out.force(true);
            }
            copyPermissions(file, temp);
            Path backup = null;
            if (keepBackup) {
                backup = file.resolveSibling(file.getFileName() + ".bak");
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, file);
            } else {
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            done = true;
            return new Result(newSize, backup);
        } finally {
            if (!done) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static long copy(FileChannel in, FileChannel out, long from, long to, long written, LongConsumer progress,
                             BooleanSupplier cancelled) throws IOException {
        long at = from;
        long total = written;
        while (at < to) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancellationException("Cancelled");
            }
            long step = Math.min(STEP, to - at);
            long moved = 0;
            while (moved < step) {
                long n = in.transferTo(at + moved, step - moved, out);
                if (n <= 0) {
                    throw new IOException("Unexpected end of file at " + (at + moved) + ".");
                }
                moved += n;
            }
            at += step;
            total += step;
            if (progress != null) {
                progress.accept(total);
            }
        }
        return total;
    }

    private static void copyPermissions(Path from, Path to) {
        try {
            PosixFileAttributeView source = Files.getFileAttributeView(from, PosixFileAttributeView.class);
            PosixFileAttributeView target = Files.getFileAttributeView(to, PosixFileAttributeView.class);
            if (source != null && target != null) {
                target.setPermissions(source.readAttributes().permissions());
            }
        } catch (IOException | UnsupportedOperationException e) {
            // keep the defaults of a new file
        }
    }
}
