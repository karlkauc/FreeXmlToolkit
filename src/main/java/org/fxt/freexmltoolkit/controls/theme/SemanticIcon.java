package org.fxt.freexmltoolkit.controls.theme;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * <p>Usage: {@code SemanticIcon.paint(new IconifyIcon("bi-trash"), DesignTokens.ColorToken.DANGER)}.
 *
 * <p>The registry holds only {@link WeakReference}s, so icons on closed tabs/popups are
 * garbage-collected normally; dead entries are pruned on the next theme switch.
 */
public final class SemanticIcon {

    private SemanticIcon() {
    }

    private record Reg(WeakReference<IconifyIcon> icon, DesignTokens.ColorToken token) {
    }

    private static final CopyOnWriteArrayList<Reg> REGISTRY = new CopyOnWriteArrayList<>();

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
    }
}
