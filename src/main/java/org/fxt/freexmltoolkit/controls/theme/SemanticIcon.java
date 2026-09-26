package org.fxt.freexmltoolkit.controls.theme;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Paint;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;

/**
 * Theme-aware colouring for semantic {@link IconifyIcon}s.
 *
 * <p>{@link SemanticColors} gives a single <em>value</em> for each semantic role, but a
 * static hex cannot follow a light/dark switch. This helper instead paints an icon from
 * the theme-aware {@link DesignTokens.ColorToken} for its role, and remembers the icon
 * (weakly) so it is re-tinted when {@link ThemeManager} switches theme — something CSS
 * cannot do for programmatically coloured icons.
 *
 * <p>Usage: {@code SemanticIcon.paint(new IconifyIcon("bi-trash"), ActionColor.DELETE)} for
 * menu items (the {@link ActionColor} overloads implement the unified action-colour rule), or
 * {@code SemanticIcon.bind(icon, ActionColor.CREATE)} wherever a CSS {@code -fx-icon-color}
 * rule would otherwise repaint the icon on the next CSS pass: JavaFX CSS never writes to a
 * bound property, so the bound colour survives.
 *
 * <p>Both registries hold only {@link WeakReference}s, so icons on closed tabs/popups are
 * garbage-collected normally; dead entries are pruned on the next theme switch.
 */
public final class SemanticIcon {

    private SemanticIcon() {
    }

    private record Reg(WeakReference<IconifyIcon> icon, DesignTokens.ColorToken token) {
    }

    private record Bound(WeakReference<IconifyIcon> icon, ObjectProperty<Paint> color,
                         DesignTokens.ColorToken token) {
    }

    private static final CopyOnWriteArrayList<Reg> REGISTRY = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Bound> BOUND = new CopyOnWriteArrayList<>();
    private static final int PRUNE_EVERY = 64;

    static {
        ThemeManager.addThemeChangeListener(SemanticIcon::recolorAll);
    }

    /**
     * Colours {@code icon} with the theme-aware colour for {@code token} and registers it
     * to be re-tinted on future theme switches.
     *
     * @param icon  the icon to colour (returned for chaining; null is tolerated)
     * @param token the semantic colour role
     * @return {@code icon}
     */
    public static IconifyIcon paint(IconifyIcon icon, DesignTokens.ColorToken token) {
        if (icon == null || token == null) {
            return icon;
        }
        icon.setIconColor(token.color(ThemeManager.currentTheme()));
        REGISTRY.add(new Reg(new WeakReference<>(icon), token));
        return icon;
    }

    /** Colours {@code icon} with the token of the action role; see {@link #paint(IconifyIcon, DesignTokens.ColorToken)}. */
    public static IconifyIcon paint(IconifyIcon icon, ActionColor role) {
        return role == null ? icon : paint(icon, role.token());
    }

    /**
     * Like {@link #paint(IconifyIcon, DesignTokens.ColorToken)} but <em>binds</em> the icon colour to a
     * theme-tracking property. Use this wherever a CSS rule ({@code -fx-icon-color}) would otherwise
     * repaint the icon on every CSS pass — e.g. panel action rows, status-bar items — because JavaFX
     * CSS never writes to a bound property. The icon is held weakly; the binding is released with it.
     */
    public static IconifyIcon bind(IconifyIcon icon, DesignTokens.ColorToken token) {
        if (icon == null || token == null) {
            return icon;
        }
        ObjectProperty<Paint> color = new SimpleObjectProperty<>(token.color(ThemeManager.currentTheme()));
        icon.iconColorProperty().unbind();
        icon.iconColorProperty().bind(color);
        BOUND.add(new Bound(new WeakReference<>(icon), color, token));
        if (BOUND.size() % PRUNE_EVERY == 0) {
            // Drop collected icons without waiting for a theme switch (e.g. the status badge binds a
            // fresh icon per recorded run for the lifetime of the session).
            BOUND.removeIf(b -> b.icon().get() == null);
        }
        return icon;
    }

    /** Number of live-or-not-yet-pruned bound registrations; package-private for tests. */
    static int boundRegistrySize() {
        return BOUND.size();
    }

    /** Binds the icon colour to the token of the action role; see {@link #bind(IconifyIcon, DesignTokens.ColorToken)}. */
    public static IconifyIcon bind(IconifyIcon icon, ActionColor role) {
        return role == null ? icon : bind(icon, role.token());
    }

    /** Re-tints every still-live registered icon for {@code theme}; prunes GC'd ones. */
    private static void recolorAll(DesignTokens.Theme theme) {
        REGISTRY.removeIf(reg -> {
            IconifyIcon icon = reg.icon().get();
            if (icon == null) {
                return true;
            }
            icon.setIconColor(reg.token().color(theme));
            return false;
        });
        BOUND.removeIf(b -> {
            if (b.icon().get() == null) {
                return true;
            }
            b.color().set(b.token().color(theme));
            return false;
        });
    }
}
