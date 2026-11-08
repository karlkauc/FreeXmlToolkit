package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Compares two folders file by file (recursively, hidden folders such as {@code .git} skipped):
 * which files exist on one side only, which are byte-identical, which differ. With
 * {@link Options#compareXml()}, XML files whose bytes differ are compared by structure
 * ({@link XmlStructuralDiff}); files that differ only in formatting count as
 * {@link Status#EQUIVALENT}. UI-free; used by Tools → Compare Folders and {@code fxt-cli compare}.
 */
public final class FolderComparison {

    /** The result for one relative path. */
    public enum Status {
        IDENTICAL("Identical"),
        EQUIVALENT("Equivalent as XML"),
        DIFFERENT("Different"),
        ONLY_LEFT("Only left"),
        ONLY_RIGHT("Only right");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        /** @return the status for tables and reports */
        public String label() {
            return label;
        }
    }

    /**
     * Options.
     *
     * @param compareXml  compare XML files whose bytes differ by structure
     * @param xmlOptions  what the XML comparison ignores
     */
    public record Options(boolean compareXml, XmlStructuralDiff.Options xmlOptions) {

        /** XML files compared by structure with the default options. */
        public static final Options DEFAULTS = new Options(true, XmlStructuralDiff.Options.DEFAULTS);

        public Options {
            xmlOptions = xmlOptions == null ? XmlStructuralDiff.Options.DEFAULTS : xmlOptions;
        }
    }

    /**
     * One file.
     *
     * @param relativePath    the path below both folders, with {@code /} separators
     * @param status          the result
     * @param leftSize        size on the left in bytes, -1 when missing
     * @param rightSize       likewise on the right
     * @param leftModified    last modification on the left, or {@code null}
     * @param rightModified   likewise on the right
     * @param xmlDifferences  number of XML differences for a {@link Status#DIFFERENT} XML file, else -1
     * @param note            why an XML file was compared by bytes only (not well-formed, too large), or empty
     */
    public record Entry(String relativePath, Status status, long leftSize, long rightSize, Instant leftModified,
                        Instant rightModified, int xmlDifferences, String note) {
    }

    /**
     * The comparison of two folders.
     *
     * @param left    the left folder
     * @param right   the right folder
     * @param entries one entry per relative path, sorted by path
     */
    public record Result(Path left, Path right, List<Entry> entries) {

        /** @return how many entries have this status */
        public long count(Status status) {
            return entries.stream().filter(e -> e.status() == status).count();
        }

        /** @return whether every file exists on both sides and is identical or equivalent */
        public boolean same() {
            return entries.stream().allMatch(e -> e.status() == Status.IDENTICAL || e.status() == Status.EQUIVALENT);
        }
    }

    /** Extensions of XML documents compared by structure. */
    public static final Set<String> XML_EXTENSIONS = Set.of(
            "xml", "xsd", "xsl", "xslt", "sch", "wsdl", "xpl", "xhtml", "svg", "rng", "fo", "xliff", "xlf");

    /** XML files above this size are compared by bytes only. */
    static final long MAX_XML_COMPARE_BYTES = 32L * 1024 * 1024;

    private FolderComparison() {
    }

    /**
     * Compares two folders.
     *
     * @param progress  receives the number of files compared so far (may be {@code null})
     * @param cancelled polled between files; when it returns {@code true} a {@link CancellationException} is thrown
     *                  (may be {@code null})
     * @throws IOException if a folder cannot be listed
     */
    public static Result compare(Path left, Path right, Options options, IntConsumer progress, BooleanSupplier cancelled)
            throws IOException {
        Map<String, Path> leftFiles = list(left);
        Map<String, Path> rightFiles = list(right);
        Set<String> paths = new TreeSet<>(leftFiles.keySet());
        paths.addAll(rightFiles.keySet());
        List<Entry> entries = new ArrayList<>(paths.size());
        int done = 0;
        for (String path : paths) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancellationException("Folder comparison cancelled");
            }
            entries.add(compareFile(path, leftFiles.get(path), rightFiles.get(path), options));
            if (progress != null) {
                progress.accept(++done);
            }
        }
        return new Result(left, right, List.copyOf(entries));
    }

    /** @return the files below {@code root} by relative path; hidden folders are skipped */
    static Map<String, Path> list(Path root) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && dir.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.put(root.relativize(file).toString().replace('\\', '/'), file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;   // unreadable entries are left out
            }
        });
        return files;
    }

    private static Entry compareFile(String path, Path left, Path right, Options options) {
        long leftSize = size(left);
        long rightSize = size(right);
        Instant leftModified = modified(left);
        Instant rightModified = modified(right);
        if (left == null || right == null) {
            return new Entry(path, left == null ? Status.ONLY_RIGHT : Status.ONLY_LEFT, leftSize, rightSize,
                    leftModified, rightModified, -1, "");
        }
        try {
            if (leftSize == rightSize && sameBytes(left, right)) {
                return new Entry(path, Status.IDENTICAL, leftSize, rightSize, leftModified, rightModified, -1, "");
            }
        } catch (IOException e) {
            return new Entry(path, Status.DIFFERENT, leftSize, rightSize, leftModified, rightModified, -1,
                    "Cannot read: " + e.getMessage());
        }
        if (!options.compareXml() || !isXml(path)) {
            return new Entry(path, Status.DIFFERENT, leftSize, rightSize, leftModified, rightModified, -1, "");
        }
        if (leftSize > MAX_XML_COMPARE_BYTES || rightSize > MAX_XML_COMPARE_BYTES) {
            return new Entry(path, Status.DIFFERENT, leftSize, rightSize, leftModified, rightModified, -1,
                    "Too large to compare as XML");
        }
        try {
            XmlStructuralDiff.Result xml = XmlStructuralDiff.compare(read(left), read(right), options.xmlOptions());
            if (!xml.compared()) {
                return new Entry(path, Status.DIFFERENT, leftSize, rightSize, leftModified, rightModified, -1,
                        xml.leftError() != null ? "Left: " + xml.leftError() : "Right: " + xml.rightError());
            }
            return new Entry(path, xml.equivalent() ? Status.EQUIVALENT : Status.DIFFERENT, leftSize, rightSize,
                    leftModified, rightModified, xml.equivalent() ? -1 : xml.differences().size(), "");
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return new Entry(path, Status.DIFFERENT, leftSize, rightSize, leftModified, rightModified, -1,
                    "Not compared as XML: " + e.getMessage());
        }
    }

    /** @return whether the path has an XML extension */
    public static boolean isXml(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 && XML_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean sameBytes(Path left, Path right) throws IOException {
        try (InputStream a = Files.newInputStream(left); InputStream b = Files.newInputStream(right)) {
            byte[] bufferA = new byte[64 * 1024];
            byte[] bufferB = new byte[64 * 1024];
            while (true) {
                int readA = a.readNBytes(bufferA, 0, bufferA.length);
                int readB = b.readNBytes(bufferB, 0, bufferB.length);
                if (readA != readB || !Arrays.equals(bufferA, 0, readA, bufferB, 0, readB)) {
                    return false;
                }
                if (readA < bufferA.length) {
                    return true;
                }
            }
        }
    }

    /** Reads a document as UTF-8 (the XML parser honours the declaration only on bytes; a mis-decoded
     * non-UTF-8 file is rarely equal anyway, and then shows up as different). */
    private static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.contains("�")) {
            text = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        return text;
    }

    private static long size(Path file) {
        try {
            return file == null ? -1 : Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private static Instant modified(Path file) {
        try {
            return file == null ? null : Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return null;
        }
    }
}
