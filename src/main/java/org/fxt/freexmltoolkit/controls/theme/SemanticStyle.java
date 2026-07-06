package org.fxt.freexmltoolkit.controls.theme;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;

/**
 * Theme-aware inline styling ({@code setStyle}) for semantic colours — the counterpart
 * to {@link SemanticIcon} for cases that colour <em>text</em> or <em>borders</em> via a
 * CSS string rather than an icon.
 *
 * <ul>
 *   <li>{@link #style(Node, DesignTokens.ColorToken, Function)} builds the node's inline
 *       style from a template that receives the current theme's hex, and re-applies it
 *       on a light/dark switch (weakly referenced; dead nodes pruned).</li>
 *   <li>{@link #hex(DesignTokens.ColorToken)} returns the current theme's hex — for nodes
 *       that already re-apply their own style (e.g. recycled {@code TableCell}s), where
 *       registering for recolour would be wrong.</li>
 * </ul>
 */
public final class SemanticStyle {

    private SemanticStyle() {
    }

    private record Reg(WeakReference<Node> node, DesignTokens.ColorToken token,
                       Function<String, String> template) {
    }

    private static final CopyOnWriteArrayList<Reg> REGISTRY = new CopyOnWriteArrayList<>();

    static {
        ThemeManager.addThemeChangeListener(SemanticStyle::recolorAll);
    }

    /** @return the current theme's hex string for {@code token} (e.g. {@code "#e03131"}). */
    public static String hex(DesignTokens.ColorToken token) {
        return toHex(token.color(ThemeManager.currentTheme()));
    }

    /**
     * Sets {@code node}'s inline style to {@code template.apply(currentHex)} and registers
     * it to be re-applied with the new theme's hex whenever the theme switches.
     *
     * @param node     the node to style (returned for chaining; null tolerated)
     * @param token    the semantic colour role
     * @param template builds the full inline-style string from a hex colour
     * @return {@code node}
     */
    public static <T extends Node> T style(T node, DesignTokens.ColorToken token,
                                           Function<String, String> template) {
        if (node == null || token == null || template == null) {
            return node;
        }
        node.setStyle(template.apply(hex(token)));
        REGISTRY.add(new Reg(new WeakReference<>(node), token, template));
        return node;
    }

    private static void recolorAll(DesignTokens.Theme theme) {
        REGISTRY.removeIf(reg -> {
            Node node = reg.node().get();
            if (node == null) {
                return true;
            }
            node.setStyle(reg.template().apply(toHex(reg.token().color(theme))));
            return false;
        });
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }
}
