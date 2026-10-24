package org.fxt.freexmltoolkit.debugger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The persisted breakpoints of one stylesheet file, editable without a running debug
 * session: backs the breakpoint gutter of every saved XSLT document in the editor.
 *
 * <p>Breakpoints are stored under the file's own {@link BreakpointStore} key. Entries for
 * <em>other</em> files that are found under that key (aggregates written by versions that
 * stored a whole session under the main stylesheet) are carried along unchanged and
 * re-saved, but never shown or toggled here; the next debug session migrates them to their
 * own files' keys.</p>
 */
public final class FileBreakpoints implements BreakpointTarget {

    private final String path;
    private final Set<Breakpoint> all;
    private final Consumer<Set<Breakpoint>> saver;

    /**
     * @param path    the file whose breakpoints these are (any spelling, normalised here)
     * @param initial the stored entries (own and foreign), copied
     * @param saver   receives the complete entry set after every toggle
     */
    public FileBreakpoints(String path, Collection<Breakpoint> initial, Consumer<Set<Breakpoint>> saver) {
        this.path = SourceLocations.normalize(path);
        this.all = new LinkedHashSet<>(initial == null ? Set.of() : initial);
        this.saver = Objects.requireNonNull(saver, "saver");
    }

    /** Loads the file's breakpoints from the {@link BreakpointStore}; toggles are saved there. */
    public static FileBreakpoints persistent(Path file) {
        String key = file.toAbsolutePath().toString();
        return new FileBreakpoints(key, BreakpointStore.load(key), set -> BreakpointStore.save(key, set));
    }

    /** @return the normalised path of the file. */
    public String path() {
        return path;
    }

    @Override
    public boolean hasBreakpoint(String filePath, int line) {
        if (!isOwn(filePath)) {
            return false;
        }
        synchronized (all) {
            for (Breakpoint bp : all) {
                if (bp.lineNumber() == line && bp.filePath().equals(path)) {
                    return bp.enabled();
                }
            }
            return false;
        }
    }

    @Override
    public boolean toggleBreakpoint(String filePath, int line) {
        if (!isOwn(filePath)) {
            return false;
        }
        boolean added;
        Set<Breakpoint> snapshot;
        synchronized (all) {
            Breakpoint existing = null;
            for (Breakpoint bp : all) {
                if (bp.lineNumber() == line && bp.filePath().equals(path)) {
                    existing = bp;
                    break;
                }
            }
            if (existing != null) {
                all.remove(existing);
                added = false;
            } else {
                all.add(new Breakpoint(path, line, true));
                added = true;
            }
            snapshot = new LinkedHashSet<>(all);
        }
        saver.accept(snapshot);
        return added;
    }

    @Override
    public String conditionAt(String filePath, int line) {
        if (!isOwn(filePath)) {
            return null;
        }
        synchronized (all) {
            for (Breakpoint bp : all) {
                if (bp.lineNumber() == line && bp.filePath().equals(path)) {
                    return bp.condition();
                }
            }
            return null;
        }
    }

    @Override
    public boolean setCondition(String filePath, int line, String condition) {
        if (!isOwn(filePath) || line < 1) {
            return false;
        }
        Set<Breakpoint> snapshot;
        synchronized (all) {
            Breakpoint existing = null;
            for (Breakpoint bp : all) {
                if (bp.lineNumber() == line && bp.filePath().equals(path)) {
                    existing = bp;
                    break;
                }
            }
            if (existing != null) {
                all.remove(existing);
                all.add(existing.withCondition(condition));
            } else {
                all.add(new Breakpoint(path, line, true, condition));
            }
            snapshot = new LinkedHashSet<>(all);
        }
        saver.accept(snapshot);
        return true;
    }

    /** @return the breakpoints of this file only (foreign entries left out), in insertion order. */
    public Set<Breakpoint> ownBreakpoints() {
        Set<Breakpoint> own = new LinkedHashSet<>();
        synchronized (all) {
            for (Breakpoint bp : all) {
                if (bp.filePath().equals(path)) {
                    own.add(bp);
                }
            }
        }
        return own;
    }

    private boolean isOwn(String filePath) {
        return !path.isEmpty() && path.equals(SourceLocations.normalize(filePath));
    }
}
