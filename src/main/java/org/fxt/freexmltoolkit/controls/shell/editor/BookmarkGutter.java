package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.bookmarks.Bookmark;
import org.fxt.freexmltoolkit.service.bookmarks.BookmarkService;

/**
 * The bookmark slot of a text editor's gutter: a fixed-width slot per line (so lines stay
 * aligned) showing a bookmark icon, a numbered badge for quick bookmarks 1–9, or amber for a
 * bookmark whose line could not be found again. Pressing the slot toggles the line's bookmark
 * (on {@code MOUSE_PRESSED}, consumed — see {@code BreakpointGutterFactory}), right-click
 * opens the bookmark menu.
 */
final class BookmarkGutter implements IntFunction<Node> {

    /** Style class of every slot (tests and CSS). */
    static final String SLOT_STYLE_CLASS = "bookmark-gutter-slot";
    /** Added to a slot that holds a bookmark. */
    static final String MARKED_STYLE_CLASS = "bookmark-set";
    /** Added to a slot whose bookmark is orphaned. */
    static final String ORPHANED_STYLE_CLASS = "bookmark-orphaned";
    private static final double SLOT_WIDTH = 14;

    /** What the slot's clicks and menu do. */
    interface Actions {
        void toggle(int line);

        /** Builds the menu for a 1-based line (bookmark or not). */
        ContextMenu menuFor(int line);
    }

    private final Supplier<String> key;
    private final Actions actions;
    private volatile Map<Integer, Bookmark> snapshot = Map.of();

    BookmarkGutter(Supplier<String> key, Actions actions) {
        this.key = key;
        this.actions = actions;
        reload();
    }

    /** Takes the current bookmarks of the document (call before re-rendering the gutter). */
    void reload() {
        Map<Integer, Bookmark> next = new HashMap<>();
        for (Bookmark b : BookmarkService.getInstance().forKey(key.get())) {
            next.put(b.line(), b);
        }
        snapshot = next;
    }

    /** @return whether a 1-based line holds a bookmark (as last reloaded). */
    boolean has(int line) {
        return snapshot.containsKey(line);
    }

    @Override
    public Node apply(int lineIndex) {
        int line = lineIndex + 1;
        StackPane slot = new StackPane();
        slot.getStyleClass().add(SLOT_STYLE_CLASS);
        slot.setAlignment(Pos.CENTER);
        slot.setMinWidth(SLOT_WIDTH);
        slot.setPrefWidth(SLOT_WIDTH);
        slot.setMaxWidth(SLOT_WIDTH);
        slot.setCursor(Cursor.HAND);
        Bookmark b = snapshot.get(line);
        if (b != null) {
            slot.getStyleClass().add(MARKED_STYLE_CLASS);
            if (b.orphaned()) {
                slot.getStyleClass().add(ORPHANED_STYLE_CLASS);
            }
            if (b.number() > 0) {
                Label badge = new Label(String.valueOf(b.number()));
                badge.getStyleClass().add("fxt-bookmark-badge");
                if (b.orphaned()) {
                    badge.getStyleClass().add("fxt-bookmark-badge-orphaned");
                }
                slot.getChildren().add(badge);
            } else {
                IconifyIcon icon = new IconifyIcon("bi-bookmark-fill");
                icon.setIconSize(11);
                SemanticIcon.paint(icon, b.orphaned() ? DesignTokens.ColorToken.WARNING : DesignTokens.ColorToken.INDIGO);
                slot.getChildren().add(icon);
            }
            Tooltip.install(slot, new Tooltip(tooltip(b)));
        }
        slot.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                actions.toggle(line);
                e.consume();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                e.consume(); // keep the caret; the menu opens on the context-menu request
            }
        });
        slot.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            ContextMenu menu = actions.menuFor(line);
            if (menu != null && !menu.getItems().isEmpty()) {
                menu.show(slot, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
        return slot;
    }

    static String tooltip(Bookmark b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b.name() != null ? b.name() : I18n.t("Bookmarks.line", b.line()));
        if (b.number() > 0) {
            sb.append("  [").append(b.number()).append(']');
        }
        if (b.orphaned()) {
            sb.append('\n').append(I18n.t("Bookmarks.orphaned-hint"));
        }
        sb.append('\n').append(I18n.t("Bookmarks.gutter-hint"));
        return sb.toString();
    }
}
