package org.fxt.freexmltoolkit.plugin;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A menu item contributed by a plugin, appended to the Tools or the Help menu.
 *
 * @param menu    where it goes
 * @param id      the node id ({@code menu-plugin-<id>} is used)
 * @param text    the text
 * @param icon    a Bootstrap icon literal
 * @param action  runs on the JavaFX thread
 * @param visible whether it is shown (asked each time the menu opens)
 */
public record PluginMenuItem(Menu menu, String id, String text, String icon, Consumer<PluginUi> action,
                             BooleanSupplier visible) {

    /** The menus a plugin can add to. */
    public enum Menu { TOOLS, HELP }

    public PluginMenuItem {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(action, "action");
        icon = icon == null ? "bi-puzzle" : icon;
        visible = visible == null ? () -> true : visible;
    }
}
