package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shared.ScrollMarkerStrip;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.bookmarks.Bookmark;
import org.fxt.freexmltoolkit.service.bookmarks.BookmarkService;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Line bookmarks of the editor tabs: every text tab gets a bookmark gutter slot and markers over
 * its scroll bar, its bookmarks follow its edits, are found again when the file changed outside
 * the editor, and move with Save As. The actions (toggle, rename, numbers 1–9, next / previous,
 * clear) work on the active tab. The bookmarks themselves live in {@link BookmarkService}.
 */
final class EditorBookmarks {

    static final String GUTTER_KEY = "bookmarks";
    private static final String SHIFT_KEY = "fxt.bookmarkShift";
    private static final String MARKER_CLASS = "fxt-scroll-marker-bookmark";

    private final EditorHost host;
    /** The key each tab's bookmarks were last stored under (it changes with Save As). */
    private final Map<EditorTab, String> keys = new WeakHashMap<>();
    private final Map<EditorTab, BookmarkGutter> gutters = new WeakHashMap<>();

    /** Asks for a bookmark name: (title, current name) → new name; empty = cancelled. Test hook. */
    private BiFunction<String, String, Optional<String>> namePrompt = EditorBookmarks::askName;
    /** Confirms clearing a document's bookmarks (receives the message). Test hook. */
    private Predicate<String> confirmer = message ->
            DialogHelper.showConfirmation(I18n.t("Bookmarks.clear-title"), message, I18n.t("Bookmarks.clear-content"));

    EditorBookmarks(EditorHost host) {
        this.host = host;
        BookmarkService.getInstance().addListener(this::serviceChanged);
        // untitled documents' bookmarks live only as long as their tab
        host.tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            while (c.next()) {
                if (c.wasRemoved() && !c.wasPermutated()) {
                    for (Tab t : c.getRemoved()) {
                        if (t instanceof EditorTab et && !host.tabPane.getTabs().contains(t)) {
                            String key = keys.remove(et);
                            gutters.remove(et);
                            if (BookmarkService.isUntitled(key)) {
                                BookmarkService.getInstance().clear(key);
                            }
                        }
                    }
                }
            }
        });
    }

    void setNamePrompt(BiFunction<String, String, Optional<String>> prompt) {
        this.namePrompt = prompt == null ? EditorBookmarks::askName : prompt;
    }

    void setConfirmer(Predicate<String> confirmer) {
        this.confirmer = confirmer;
    }

    /** Asks for a bookmark name (dialog, or the test hook). */
    Optional<String> promptName(String header, String current) {
        return namePrompt.apply(header, current == null ? "" : current);
    }

    /** Asks the user to confirm (dialog, or the test hook). */
    boolean confirm(String message) {
        return confirmer.test(message);
    }

    // ----- installation -------------------------------------------------------------------------

    /** Gives a text tab its bookmark gutter, scroll markers and edit tracking (once). */
    void install(EditorTab et) {
        if (gutters.containsKey(et) || et.view.getCodeArea() == null) {
            return;
        }
        keys.put(et, keyOf(et));
        BookmarkGutter gutter = new BookmarkGutter(() -> currentKey(et), new BookmarkGutter.Actions() {
            @Override
            public void toggle(int line) {
                EditorBookmarks.this.toggle(et, line);
            }

            @Override
            public ContextMenu menuFor(int line) {
                return EditorBookmarks.this.menuFor(et, line);
            }
        });
        gutters.put(et, gutter);
        EditorHost.setGutterContributor(et, GUTTER_KEY, gutter);
        LineShiftTracker.install(et.view.getCodeArea(), SHIFT_KEY,
                () -> BookmarkService.getInstance().has(currentKey(et)),
                shift -> BookmarkService.getInstance().shiftLines(currentKey(et), shift));
        // Save As / first save: the bookmarks go with the document to its new file
        et.document.displayNameProperty().addListener((obs, o, n) -> followPath(et));
        installContextMenu(et);
        refresh(et);
    }

    /** "Bookmark ▸" at the end of the editor's context menu, for the caret line. */
    private void installContextMenu(EditorTab et) {
        ContextMenu menu = et.view.getEditorContextMenu();
        if (menu == null) {
            return;
        }
        Menu sub = new Menu(I18n.t("Bookmarks.menu"), paint("bi-bookmark", ActionColor.NAVIGATE));
        sub.setId("editor-ctx-bookmark");
        sub.getItems().add(new MenuItem("…")); // an empty submenu would not open; filled when shown
        menu.getItems().addAll(new SeparatorMenuItem(), sub);
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING,
                e -> sub.getItems().setAll(menuItems(et, caretLine(et))));
    }

    /** @return the key of a tab's document now (its file, or an untitled id). */
    static String keyOf(EditorTab et) {
        Path path = et.document.getPath();
        return path != null ? BookmarkService.keyOf(path)
                : BookmarkService.untitledKey(Integer.toHexString(System.identityHashCode(et.document)));
    }

    /** The tab's key, moving its bookmarks along when the document got another file. */
    private String currentKey(EditorTab et) {
        String now = keyOf(et);
        String before = keys.get(et);
        if (before != null && !before.equals(now)) {
            keys.put(et, now);
            moveBookmarks(before, now, et);
        } else if (before == null) {
            keys.put(et, now);
        }
        return now;
    }

    /** The tab's document got another file (Save As): its bookmarks go along. */
    void followPath(EditorTab et) {
        if (gutters.containsKey(et)) {
            currentKey(et);
            refresh(et);
        }
    }

    private void moveBookmarks(String from, String to, EditorTab et) {
        BookmarkService service = BookmarkService.getInstance();
        if (BookmarkService.isUntitled(from)) {
            service.renamePath(from, to);
        } else {
            service.copyPath(from, to); // Save As: the original file keeps its bookmarks
        }
        service.updateLineTexts(to, line -> lineText(et, line));
    }

    private void serviceChanged(String key) {
        Runnable r = () -> {
            for (EditorTab et : List.copyOf(gutters.keySet())) {
                if (key == null || key.equals(keys.get(et))) {
                    refresh(et);
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    /** Re-renders a tab's bookmark slot and scroll markers. */
    void refresh(EditorTab et) {
        BookmarkGutter gutter = gutters.get(et);
        if (gutter == null) {
            return;
        }
        gutter.reload();
        et.view.refreshGutter();
        List<Bookmark> list = BookmarkService.getInstance().forKey(currentKey(et));
        ScrollMarkerStrip strip = et.scrollMarkers(line -> {
            if (host.tabPane.getSelectionModel().getSelectedItem() == et) {
                host.revealProblem(line, 0, null);
            }
        });
        strip.setMarkers(list.stream()
                .map(b -> new ScrollMarkerStrip.Marker(b.line(),
                        b.orphaned() ? MARKER_CLASS + "-orphaned" : MARKER_CLASS,
                        b.name() != null ? b.name() + " (" + I18n.t("Bookmarks.line", b.line()) + ")"
                                : I18n.t("Bookmarks.line", b.line())))
                .toList());
    }

    // ----- file events --------------------------------------------------------------------------

    /**
     * A tab's text was (re)read from its file: the bookmarks are found again by their line texts.
     * Tells the user when some moved or lost their line.
     */
    void textLoaded(EditorTab et, String text) {
        if (et.document.getPath() == null || !gutters.containsKey(et)) {
            return;
        }
        if (BookmarkService.getInstance().forKey(currentKey(et)).isEmpty()) {
            return; // nothing to find again (and no need to split a large text into lines)
        }
        var result = BookmarkService.getInstance().reanchor(currentKey(et), text);
        if (result.anything() && host.getScene() != null && host.getScene().getWindow() != null) {
            org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ToastNotification.info(host.getScene().getWindow(),
                    I18n.t("Bookmarks.reanchored", et.document.getDisplayName(), result.moved(), result.orphaned()));
        }
        refresh(et);
    }

    /** A tab was saved: its bookmarks take their lines' current texts. */
    void saved(EditorTab et) {
        BookmarkService.getInstance().updateLineTexts(currentKey(et), line -> lineText(et, line));
    }

    // ----- actions ------------------------------------------------------------------------------

    private Optional<EditorTab> active() {
        return host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et && gutters.containsKey(et)
                ? Optional.of(et) : Optional.empty();
    }

    private static int caretLine(EditorTab et) {
        return et.view.getCodeArea().getCurrentParagraph() + 1;
    }

    private static String lineText(EditorTab et, int line) {
        var area = et.view.getCodeArea();
        return line >= 1 && line <= area.getParagraphs().size() ? area.getParagraph(line - 1).getText() : "";
    }

    /** @return whether the active tab takes bookmarks. */
    boolean canBookmark() {
        return active().isPresent();
    }

    /** Sets or removes the bookmark on a line of a tab. */
    boolean toggle(EditorTab et, int line) {
        return BookmarkService.getInstance().toggle(currentKey(et), line, lineText(et, line));
    }

    /** Ctrl+F2: sets or removes the bookmark on the caret line. @return whether a tab took it. */
    boolean toggleAtCaret() {
        return active().map(et -> {
            toggle(et, caretLine(et));
            return true;
        }).orElse(false);
    }

    /** Names the bookmark on a line (sets one first if the line has none). */
    boolean rename(EditorTab et, int line) {
        BookmarkService service = BookmarkService.getInstance();
        String key = currentKey(et);
        Bookmark existing = service.at(key, line).orElse(null);
        Optional<String> answer = namePrompt.apply(I18n.t("Bookmarks.rename-header", line),
                existing == null || existing.name() == null ? "" : existing.name());
        if (answer.isEmpty()) {
            return false;
        }
        service.ensure(key, line, lineText(et, line));
        return service.rename(key, line, answer.get());
    }

    /** Ctrl+Shift+F2: names the bookmark on the caret line. */
    boolean renameAtCaret() {
        return active().map(et -> rename(et, caretLine(et))).orElse(false);
    }

    /** Gives the bookmark on a line quick number {@code n} (0 removes the number). */
    void setNumber(EditorTab et, int line, int n) {
        BookmarkService.getInstance().setNumber(currentKey(et), line, n, lineText(et, line));
    }

    /**
     * Ctrl+Shift+digit: the caret line gets quick bookmark {@code n}; when it already has that
     * number, the number is taken off again.
     */
    boolean toggleNumberAtCaret(int n) {
        return active().map(et -> {
            int line = caretLine(et);
            boolean has = BookmarkService.getInstance().at(currentKey(et), line)
                    .map(b -> b.number() == n).orElse(false);
            setNumber(et, line, has ? 0 : n);
            return true;
        }).orElse(false);
    }

    /** Ctrl+digit: jumps to quick bookmark {@code n}, opening its file when needed. */
    boolean goToNumber(int n) {
        return BookmarkService.getInstance().byNumber(n).map(l -> {
            openAt(l.key(), l.bookmark().line());
            return true;
        }).orElse(false);
    }

    /** Removes every bookmark of the active document, after asking. @return how many were removed. */
    int clearActive() {
        return active().map(et -> {
            String key = currentKey(et);
            int n = BookmarkService.getInstance().forKey(key).size();
            if (n == 0 || !confirmer.test(I18n.t("Bookmarks.clear-header", n, et.document.getDisplayName()))) {
                return 0;
            }
            return BookmarkService.getInstance().clear(key);
        }).orElse(0);
    }

    /**
     * F2 / Shift+F2: the next / previous bookmark after the caret line; past the last one of the
     * document the next file with bookmarks follows (in the order of the Bookmarks list), the
     * document's own first one when it is the only file.
     */
    boolean next(boolean forward) {
        Optional<EditorTab> current = active();
        BookmarkService service = BookmarkService.getInstance();
        if (current.isEmpty()) {
            List<String> files = service.keys();
            if (files.isEmpty()) {
                return false;
            }
            String key = forward ? files.getFirst() : files.getLast();
            List<Bookmark> list = service.forKey(key);
            openAt(key, (forward ? list.getFirst() : list.getLast()).line());
            return true;
        }
        EditorTab et = current.get();
        String key = currentKey(et);
        int caret = caretLine(et);
        List<Bookmark> own = service.forKey(key);
        Optional<Bookmark> inDoc = forward
                ? own.stream().filter(b -> b.line() > caret).findFirst()
                : own.reversed().stream().filter(b -> b.line() < caret).findFirst();
        if (inDoc.isPresent()) {
            openAt(key, inDoc.get().line());
            return true;
        }
        List<String> files = service.keys();
        int at = files.indexOf(key);
        for (int i = 1; i <= files.size(); i++) {
            int idx = at < 0 ? (forward ? i - 1 : files.size() - i)
                    : Math.floorMod(at + (forward ? i : -i), files.size());
            String other = files.get(idx);
            if (other.equals(key)) {
                break;
            }
            if (Files.exists(Path.of(other))) {
                List<Bookmark> list = service.forKey(other);
                openAt(other, (forward ? list.getFirst() : list.getLast()).line());
                return true;
            }
        }
        if (own.isEmpty()) {
            return false;
        }
        openAt(key, (forward ? own.getFirst() : own.getLast()).line()); // wrap around within the document
        return true;
    }

    /** Shows a bookmark: selects its open tab (or opens its file) and reveals the line. */
    void openAt(String key, int line) {
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && key.equals(keys.get(et))) {
                host.tabPane.getSelectionModel().select(tab);
                host.revealProblem(line, 0, null);
                return;
            }
        }
        if (!BookmarkService.isUntitled(key)) {
            host.openFileAtLine(Path.of(key), line);
        }
    }

    // ----- menus --------------------------------------------------------------------------------

    /** The gutter slot's menu for a 1-based line. */
    ContextMenu menuFor(EditorTab et, int line) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(menuItems(et, line));
        return menu;
    }

    /** Items for a line (gutter menu and the editor's context menu). */
    List<MenuItem> menuItems(EditorTab et, int line) {
        BookmarkService service = BookmarkService.getInstance();
        Optional<Bookmark> b = service.at(currentKey(et), line);
        MenuItem toggle = item(b.isPresent() ? I18n.t("Bookmarks.remove") : I18n.t("Bookmarks.add"),
                b.isPresent() ? "bi-bookmark-x" : "bi-bookmark-plus",
                b.isPresent() ? ActionColor.DELETE : ActionColor.CREATE);
        toggle.setOnAction(e -> toggle(et, line));
        MenuItem rename = item(I18n.t("Bookmarks.rename"), "bi-pencil", ActionColor.MODIFY);
        rename.setOnAction(e -> rename(et, line));
        Menu number = new Menu(I18n.t("Bookmarks.number"), paint("bi-123", ActionColor.STRUCTURE));
        MenuItem none = new MenuItem(I18n.t("Bookmarks.number-none"));
        none.setDisable(b.map(x -> x.number() == 0).orElse(true));
        none.setOnAction(e -> setNumber(et, line, 0));
        number.getItems().add(none);
        for (int n = 1; n <= 9; n++) {
            int digit = n;
            String taken = service.byNumber(n).map(l -> " — " + l.bookmark().label()).orElse("");
            MenuItem item = new MenuItem(n + taken);
            item.setOnAction(e -> setNumber(et, line, digit));
            number.getItems().add(item);
        }
        MenuItem clear = item(I18n.t("Bookmarks.clear-document"), "bi-trash", ActionColor.DELETE);
        clear.setDisable(service.forKey(currentKey(et)).isEmpty());
        clear.setOnAction(e -> {
            host.tabPane.getSelectionModel().select(et);
            clearActive();
        });
        return List.of(toggle, rename, number, new SeparatorMenuItem(), clear);
    }

    private static MenuItem item(String text, String icon, ActionColor color) {
        return new MenuItem(text, paint(icon, color));
    }

    private static IconifyIcon paint(String literal, ActionColor color) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return SemanticIcon.paint(icon, color);
    }

    private static Optional<String> askName(String header, String current) {
        TextInputDialog dialog = DialogHelper.createTextInputDialog(I18n.t("Bookmarks.rename-title"), header,
                I18n.t("Bookmarks.name-label"), current);
        return dialog.showAndWait().map(String::strip);
    }
}
