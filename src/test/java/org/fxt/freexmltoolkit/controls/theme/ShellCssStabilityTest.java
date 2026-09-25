package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ratchet guard against "jumping" controls: a rule that only applies while a control is
 * hovered, pressed, armed, focused or selected must not change the control's geometry.
 * The legacy {@code .button:pressed} rule used to shift padding and background insets,
 * which made every side-panel button grow while the mouse was down; selection rules that
 * switch to bold reflow the text the same way.
 *
 * <p>The guard scans the stylesheets the Unified Shell loads and fails when a state rule
 * declares one of the geometry properties with a value that differs from the base rule
 * of the same selector (a state rule may restate the base value to shield itself from
 * less specific rules, e.g. {@code .fxt-action-row:pressed}).
 */
class ShellCssStabilityTest {

    /** The sheets loaded by shell.fxml / ThemeManager, in load order. */
    private static final List<String> SHELL_SHEETS = List.of(
            "src/main/resources/css/design-tokens.css",
            "src/main/resources/css/app-theme.css",
            "src/main/resources/css/fxt-theme.css",
            "src/main/resources/css/light-theme.css",
            "src/main/resources/css/dark-theme.css",
            "src/main/resources/css/unified-shell.css"
    );

    /** Properties that change a control's size or its text metrics. */
    private static final Set<String> GEOMETRY_PROPERTIES = Set.of(
            "-fx-padding", "-fx-background-insets", "-fx-border-width", "-fx-font-weight", "-fx-font-size");

    /**
     * Legacy selectors that are allowed to keep a geometry change on state. These are tab
     * headers (bold selected tab is a conventional, non-jumping treatment because the tab
     * header area reserves the width) and a status label that draws its border on hover.
     * Do not add panel/button selectors here — fix the rule instead.
     */
    private static final Set<String> LEGACY_ALLOW_LIST = Set.of(
            ".revolutionary-tab-pane .tab:selected",
            ".tab-pane .tab:selected",
            ".status-label:hover",
            ".fxt-analysis-tabs > .tab-header-area > .headers-region > .tab:selected .tab-label"
    );

    private static final Pattern STATE =
            Pattern.compile(":(pressed|hover|focused|focus-visible|armed|selected|showing)\\b");
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");
    private static final Pattern DECLARATION = Pattern.compile("(-fx-[a-z-]+)\\s*:\\s*([^;]+);");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    @Test
    @DisplayName("Hover/pressed/focused/selected rules in shell stylesheets must not change geometry")
    void stateRulesDoNotChangeGeometry() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String relative : SHELL_SHEETS) {
            Path file = Path.of(relative);
            assertTrue(Files.exists(file), "Stylesheet not found: " + file.toAbsolutePath());
            String css = COMMENT.matcher(Files.readString(file)).replaceAll("");
            Map<String, Map<String, String>> baseRules = collectBaseRules(css);
            Matcher rule = RULE.matcher(css);
            while (rule.find()) {
                String selectorList = rule.group(1).trim();
                if (!STATE.matcher(selectorList).find()) {
                    continue;
                }
                Map<String, String> declarations = declarations(rule.group(2));
                for (String selector : selectorList.split(",")) {
                    String stateSelector = normalise(selector);
                    if (!STATE.matcher(stateSelector).find() || LEGACY_ALLOW_LIST.contains(stateSelector)) {
                        continue;
                    }
                    Map<String, String> base = baseRules.getOrDefault(
                            STATE.matcher(stateSelector).replaceAll(""), Map.of());
                    for (Map.Entry<String, String> declaration : declarations.entrySet()) {
                        if (!GEOMETRY_PROPERTIES.contains(declaration.getKey())) {
                            continue;
                        }
                        String baseValue = base.get(declaration.getKey());
                        if (!declaration.getValue().equals(baseValue)) {
                            failures.add(relative + "  " + stateSelector + " { " + declaration.getKey()
                                    + ": " + declaration.getValue() + "; }  (base: " + baseValue + ")");
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "These state rules change the control's geometry, so the control jumps on hover/click. "
                        + "State rules may only change colours (or restate the base rule's value):\n"
                        + String.join("\n", failures));
    }

    /** Declarations of every stateless selector, later rules overriding earlier ones. */
    private static Map<String, Map<String, String>> collectBaseRules(String css) {
        Map<String, Map<String, String>> rules = new HashMap<>();
        Matcher rule = RULE.matcher(css);
        while (rule.find()) {
            Map<String, String> declarations = declarations(rule.group(2));
            for (String selector : rule.group(1).split(",")) {
                String normalised = normalise(selector);
                if (STATE.matcher(normalised).find()) {
                    continue;
                }
                rules.computeIfAbsent(normalised, k -> new LinkedHashMap<>()).putAll(declarations);
            }
        }
        return rules;
    }

    private static Map<String, String> declarations(String body) {
        Map<String, String> declarations = new LinkedHashMap<>();
        Matcher declaration = DECLARATION.matcher(body);
        while (declaration.find()) {
            declarations.put(declaration.group(1), normalise(declaration.group(2)));
        }
        return declarations;
    }

    private static String normalise(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
