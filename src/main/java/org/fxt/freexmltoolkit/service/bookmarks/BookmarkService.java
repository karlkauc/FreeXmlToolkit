package org.fxt.freexmltoolkit.service.bookmarks;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.debugger.LineShift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * The line bookmarks of all documents, kept across restarts in
 * {@code ~/.freeXmlToolkit/bookmarks.json} (file path → bookmarks).
 *
 * <p>Documents are identified by a key: {@link #keyOf(Path)} for a file, {@link #untitledKey(String)}
 * for a document that has no file yet — those live in memory only and move to the file's key on the
 * first save ({@link #renamePath}). UI-free and thread-safe; listeners are told the key that changed
 * ({@code null} = several) on the mutating thread.</p>
 */
public final class BookmarkService {

    private static final Logger logger = LogManager.getLogger(BookmarkService.class);
    private static final String UNTITLED_PREFIX = "untitled:";
    private static final long SAVE_DELAY_MS = 500;

    private static BookmarkService instance;

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    /** key → bookmarks sorted by line; insertion order of the keys = order in which files got their first bookmark. */
    private final Map<String, List<Bookmark>> byKey = new LinkedHashMap<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bookmark-saver");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingSave;
    /** Changes not written yet. */
    private boolean dirty;

    /** A service stored in {@code file} (tests); the app uses {@link #getInstance()}. */
    public BookmarkService(Path file) {
        this.file = file;
        load();
    }

    /** @return the app-wide service ({@code ~/.freeXmlToolkit/bookmarks.json}). */
    public static synchronized BookmarkService getInstance() {
        if (instance == null) {
            BookmarkService service = new BookmarkService(
                    Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "bookmarks.json"));
            // a change made just before the application exits is still written
            Runtime.getRuntime().addShutdownHook(new Thread(service::flushIfDirty, "bookmark-flush"));
            instance = service;
        }
        return instance;
    }

    /** Test hook: the next {@link #getInstance()} reads the (possibly redirected) {@code user.home} again. */
    public static synchronized void resetInstanceForTests() {
        if (instance != null) {
            instance.flushIfDirty();
        }
        instance = null;
    }

    // ----- keys -------------------------------------------------------------------------------

    /** @return the key of a file: its absolute, normalized path. */
    public static String keyOf(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    /** @return the key of a document without a file (kept in memory only). */
    public static String untitledKey(String id) {
        return UNTITLED_PREFIX + id;
    }

    /** @return whether {@code key} names a document without a file. */
    public static boolean isUntitled(String key) {
        return key != null && key.startsWith(UNTITLED_PREFIX);
    }

    // ----- listeners ----------------------------------------------------------------------------

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    private void changed(String key) {
        if (key == null || !isUntitled(key)) {
            scheduleSave();
        }
        for (Consumer<String> l : listeners) {
            try {
                l.accept(key);
            } catch (RuntimeException e) {
                logger.warn("Bookmark listener failed", e);
            }
        }
    }

    // ----- queries ----------------------------------------------------------------------------

    /** @return the bookmarks of a document, sorted by line (a copy). */
    public synchronized List<Bookmark> forKey(String key) {
        List<Bookmark> list = byKey.get(key);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** @return whether a document has bookmarks (cheap: no copy). */
    public synchronized boolean has(String key) {
        List<Bookmark> list = byKey.get(key);
        return list != null && !list.isEmpty();
    }

    /** @return the bookmark on a 1-based line, if any. */
    public synchronized Optional<Bookmark> at(String key, int line) {
        return forKey(key).stream().filter(b -> b.line() == line).findFirst();
    }

    /** @return every document key with bookmarks, files only, in the order they got their first bookmark. */
    public synchronized List<String> keys() {
        return byKey.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty() && !isUntitled(e.getKey()))
                .map(Map.Entry::getKey).toList();
    }

    /** A bookmark with the document it belongs to. */
    public record Located(String key, Bookmark bookmark) {
    }

    /** @return the bookmark with quick-access {@code number} (1–9), if set. */
    public synchronized Optional<Located> byNumber(int number) {
        if (number < 1 || number > 9) {
            return Optional.empty();
        }
        for (var e : byKey.entrySet()) {
            for (Bookmark b : e.getValue()) {
                if (b.number() == number) {
                    return Optional.of(new Located(e.getKey(), b));
                }
            }
        }
        return Optional.empty();
    }

    /** @return the file keys whose files no longer exist. */
    public List<String> missingFiles() {
        return keys().stream().filter(k -> !Files.exists(Path.of(k))).toList();
    }

    /** @return the total number of bookmarks in files. */
    public synchronized int count() {
        return keys().stream().mapToInt(k -> byKey.get(k).size()).sum();
    }

    // ----- changes ----------------------------------------------------------------------------

    /**
     * Sets or removes the bookmark on a 1-based line.
     *
     * @return {@code true} when a bookmark was set, {@code false} when it was removed
     */
    public boolean toggle(String key, int line, String lineText) {
        boolean set;
        synchronized (this) {
            List<Bookmark> list = byKey.computeIfAbsent(key, k -> new ArrayList<>());
            // removed an existing bookmark -> false; otherwise add one (add() is true)
            set = !list.removeIf(b -> b.line() == line) && list.add(Bookmark.at(line, lineText));
            sort(list);
            dropIfEmpty(key);
        }
        changed(key);
        return set;
    }

    /** Sets a bookmark on a line unless one is there; @return the line's bookmark. */
    public Bookmark ensure(String key, int line, String lineText) {
        Bookmark result;
        synchronized (this) {
            List<Bookmark> list = byKey.computeIfAbsent(key, k -> new ArrayList<>());
            Optional<Bookmark> existing = list.stream().filter(b -> b.line() == line).findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            result = Bookmark.at(line, lineText);
            list.add(result);
            sort(list);
        }
        changed(key);
        return result;
    }

    /** Names the bookmark on a line ({@code null} / blank removes the name). @return whether one was there. */
    public boolean rename(String key, int line, String name) {
        return replace(key, line, b -> b.withName(name));
    }

    /** Removes the bookmark on a line. @return whether one was there. */
    public boolean remove(String key, int line) {
        boolean removed;
        synchronized (this) {
            List<Bookmark> list = byKey.get(key);
            removed = list != null && list.removeIf(b -> b.line() == line);
            dropIfEmpty(key);
        }
        if (removed) {
            changed(key);
        }
        return removed;
    }

    /** Removes every bookmark of a document. @return how many were removed. */
    public int clear(String key) {
        int n;
        synchronized (this) {
            List<Bookmark> list = byKey.remove(key);
            n = list == null ? 0 : list.size();
        }
        if (n > 0) {
            changed(key);
        }
        return n;
    }

    /** Removes the bookmarks of every file. */
    public void clearAll() {
        synchronized (this) {
            byKey.keySet().removeIf(k -> !isUntitled(k));
        }
        changed(null);
    }

    /** Forgets the bookmarks of files that no longer exist. @return how many files were dropped. */
    public int removeMissingFiles() {
        List<String> missing = missingFiles();
        if (missing.isEmpty()) {
            return 0;
        }
        synchronized (this) {
            missing.forEach(byKey::remove);
        }
        changed(null);
        return missing.size();
    }

    /**
     * Gives the bookmark on a line quick-access {@code number} (1–9; 0 removes it). A number is
     * unique: another bookmark holding it loses it. A line without a bookmark gets one.
     */
    public void setNumber(String key, int line, int number, String lineText) {
        synchronized (this) {
            if (number >= 1 && number <= 9) {
                for (var e : byKey.entrySet()) {
                    e.getValue().replaceAll(b -> b.number() == number ? b.withNumber(0) : b);
                }
            }
            List<Bookmark> list = byKey.computeIfAbsent(key, k -> new ArrayList<>());
            boolean found = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).line() == line) {
                    list.set(i, list.get(i).withNumber(number));
                    found = true;
                }
            }
            if (!found && number >= 1 && number <= 9) {
                list.add(Bookmark.at(line, lineText).withNumber(number));
                sort(list);
            }
            dropIfEmpty(key);
        }
        changed(null); // another document may have lost the number
    }

    /**
     * Moves the bookmarks of a document with an edit of its text. A bookmark whose line was
     * deleted moves to the line where the deletion happened; two bookmarks landing on one line
     * merge (name and number of the first one that has them are kept).
     *
     * @return whether a bookmark moved
     */
    public boolean shiftLines(String key, LineShift shift) {
        if (shift.isNeutral()) {
            return false;
        }
        boolean moved = false;
        synchronized (this) {
            List<Bookmark> list = byKey.get(key);
            if (list == null || list.isEmpty()) {
                return false;
            }
            Map<Integer, Bookmark> merged = new LinkedHashMap<>();
            for (Bookmark b : list) {
                int to = shift.apply(b.line());
                if (to < 1) {
                    to = Math.max(1, shift.startLine());
                }
                if (to != b.line()) {
                    moved = true;
                }
                Bookmark nb = b.withLine(to);
                merged.merge(to, nb, BookmarkService::mergeTwo);
            }
            if (merged.size() != list.size()) {
                moved = true;
            }
            list.clear();
            list.addAll(merged.values());
            sort(list);
        }
        if (moved) {
            changed(key);
        }
        return moved;
    }

    private static Bookmark mergeTwo(Bookmark a, Bookmark b) {
        Bookmark r = a;
        if (r.name() == null && b.name() != null) {
            r = r.withName(b.name());
        }
        if (r.number() == 0 && b.number() != 0) {
            r = r.withNumber(b.number());
        }
        return r;
    }

    /** Remembers each bookmark's current line text (after a save), clearing the orphan flags. */
    public void updateLineTexts(String key, String text) {
        List<String> lines = BookmarkAnchor.lines(text);
        updateLineTexts(key, line -> line <= lines.size() ? lines.get(line - 1) : "");
    }

    /**
     * As {@link #updateLineTexts(String, String)}, reading only the bookmarked lines.
     *
     * @param lineAt the text of a 1-based line ("" past the end)
     */
    public void updateLineTexts(String key, java.util.function.IntFunction<String> lineAt) {
        boolean changed = false;
        synchronized (this) {
            List<Bookmark> list = byKey.get(key);
            if (list == null || list.isEmpty()) {
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                Bookmark b = list.get(i);
                String t = Bookmark.clip(lineAt.apply(b.line()));
                if (!t.equals(b.lineText()) || b.orphaned()) {
                    list.set(i, b.withLineText(t));
                    changed = true;
                }
            }
        }
        if (changed) {
            changed(key);
        }
    }

    /** What {@link #reanchor} did. */
    public record ReanchorResult(int moved, int orphaned) {
        public boolean anything() {
            return moved > 0 || orphaned > 0;
        }
    }

    /** Finds each bookmark's line again in a text that may have changed outside the editor. */
    public ReanchorResult reanchor(String key, String text) {
        List<String> lines = BookmarkAnchor.lines(text);
        int moved = 0;
        int orphaned = 0;
        boolean changed = false;
        synchronized (this) {
            List<Bookmark> list = byKey.get(key);
            if (list == null || list.isEmpty()) {
                return new ReanchorResult(0, 0);
            }
            Map<Integer, Bookmark> merged = new LinkedHashMap<>();
            for (Bookmark b : list) {
                BookmarkAnchor.Anchored a = BookmarkAnchor.anchor(b, lines);
                switch (a.outcome()) {
                    case MOVED -> moved++;
                    case ORPHANED -> orphaned++;
                    default -> { }
                }
                if (!a.bookmark().equals(b)) {
                    changed = true;
                }
                merged.merge(a.bookmark().line(), a.bookmark(), BookmarkService::mergeTwo);
            }
            if (merged.size() != list.size()) {
                changed = true;
            }
            list.clear();
            list.addAll(merged.values());
            sort(list);
        }
        if (changed) {
            changed(key);
        }
        return new ReanchorResult(moved, orphaned);
    }

    /**
     * Moves the bookmarks of {@code oldKey} to {@code newKey} (Save As, first save of an untitled
     * document, a file renamed or moved). Bookmarks already stored for {@code newKey} are replaced.
     */
    public void renamePath(String oldKey, String newKey) {
        if (oldKey == null || newKey == null || oldKey.equals(newKey)) {
            return;
        }
        synchronized (this) {
            List<Bookmark> list = byKey.remove(oldKey);
            if (list == null) {
                return;
            }
            byKey.remove(newKey);
            byKey.put(newKey, list);
        }
        changed(null);
    }

    /** Copies the bookmarks of {@code fromKey} to {@code toKey} (Save As keeps the original's). */
    public void copyPath(String fromKey, String toKey) {
        if (fromKey == null || toKey == null || fromKey.equals(toKey)) {
            return;
        }
        synchronized (this) {
            List<Bookmark> list = byKey.get(fromKey);
            if (list == null || list.isEmpty()) {
                return;
            }
            // numbers are unique: the copy does not take them
            List<Bookmark> copy = new ArrayList<>(list.stream().map(b -> b.withNumber(0)).toList());
            byKey.put(toKey, copy);
        }
        changed(toKey);
    }

    private boolean replace(String key, int line, java.util.function.UnaryOperator<Bookmark> change) {
        boolean found = false;
        synchronized (this) {
            List<Bookmark> list = byKey.get(key);
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).line() == line) {
                        list.set(i, change.apply(list.get(i)));
                        found = true;
                    }
                }
            }
        }
        if (found) {
            changed(key);
        }
        return found;
    }

    private static void sort(List<Bookmark> list) {
        list.sort(Comparator.comparingInt(Bookmark::line));
    }

    private void dropIfEmpty(String key) {
        List<Bookmark> list = byKey.get(key);
        if (list != null && list.isEmpty()) {
            byKey.remove(key);
        }
    }

    // ----- persistence --------------------------------------------------------------------------

    private synchronized void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Type type = new TypeToken<LinkedHashMap<String, List<Bookmark>>>() { }.getType();
            Map<String, List<Bookmark>> loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
            if (loaded != null) {
                loaded.forEach((k, v) -> {
                    if (k != null && v != null && !v.isEmpty() && !isUntitled(k)) {
                        List<Bookmark> list = new ArrayList<>();
                        v.stream().filter(b -> b != null && b.line() >= 1).forEach(list::add);
                        sort(list);
                        byKey.put(k, list);
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Could not read bookmarks from {}: {}", file, e.getMessage());
        }
    }

    private synchronized void scheduleSave() {
        dirty = true;
        if (pendingSave != null) {
            pendingSave.cancel(false);
        }
        pendingSave = saver.schedule(this::flush, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Writes the bookmarks if they changed since the last write. */
    public void flushIfDirty() {
        boolean write;
        synchronized (this) {
            write = dirty;
        }
        if (write) {
            flush();
        }
    }

    /** Writes the bookmarks now. */
    public void flush() {
        String json;
        synchronized (this) {
            dirty = false;
            if (pendingSave != null) {
                pendingSave.cancel(false);
                pendingSave = null;
            }
            Map<String, List<Bookmark>> out = new LinkedHashMap<>();
            byKey.forEach((k, v) -> {
                if (!isUntitled(k) && !v.isEmpty()) {
                    out.put(k, List.copyOf(v));
                }
            });
            json = gson.toJson(out);
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warn("Could not save bookmarks to {}: {}", file, e.getMessage());
        }
    }
}
