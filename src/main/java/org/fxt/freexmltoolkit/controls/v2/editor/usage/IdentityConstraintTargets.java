package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a node is a field target of an identity constraint (xs:key, xs:unique,
 * xs:keyref): the node the constraint's selector plus one of its fields leads to, starting at the
 * element that declares the constraint.
 * <p>
 * Covers the restricted XPath subset of identity constraints (XSD 1.0 §3.11.6, XSD 1.1 adds the
 * {@code child::} / {@code attribute::} axes): alternatives ({@code a|b}), a leading {@code ./} or
 * {@code .//}, {@code .}, name tests with or without a prefix (compared by local name),
 * {@code *}, {@code p:*} and a final {@code @name} / {@code @*} in a field.
 */
public final class IdentityConstraintTargets {

    private IdentityConstraintTargets() {
    }

    /**
     * @param selector the constraint's selector
     * @param field    one of its fields
     * @param steps    the path from the declaring element (exclusive) to the candidate node
     *                 (inclusive): element names, an attribute as {@code @name}; prefixes allowed
     * @return whether selector and field lead exactly to the candidate
     */
    public static boolean matches(String selector, String field, List<String> steps) {
        if (selector == null || field == null || steps == null) {
            return false;
        }
        for (String selectorPath : selector.split("\\|")) {
            for (String fieldPath : field.split("\\|")) {
                List<Token> pattern = new ArrayList<>();
                if (!tokenize(selectorPath, pattern) || !tokenize(fieldPath, pattern)) {
                    continue;
                }
                if (match(pattern, 0, steps, 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One step of a path: a name test, or {@code .//} (any number of element steps). */
    private record Token(String test, boolean anyDepth) {
    }

    /** Appends the tokens of one path; false if it is empty or not of the supported syntax. */
    private static boolean tokenize(String path, List<Token> into) {
        String rest = path.strip();
        if (rest.isEmpty()) {
            return false;
        }
        if (rest.startsWith(".//")) {
            into.add(new Token(null, true));
            rest = rest.substring(3);
        }
        for (String raw : rest.split("/")) {
            String step = raw.strip();
            if (step.startsWith("child::")) {
                step = step.substring("child::".length()).strip();
            } else if (step.startsWith("attribute::")) {
                step = "@" + step.substring("attribute::".length()).strip();
            }
            if (step.isEmpty()) {
                return false;
            }
            if (!".".equals(step)) {
                into.add(new Token(step, false));
            }
        }
        return true;
    }

    private static boolean match(List<Token> pattern, int p, List<String> steps, int s) {
        if (p == pattern.size()) {
            return s == steps.size();
        }
        Token token = pattern.get(p);
        if (token.anyDepth()) {
            // zero or more element steps (an attribute is never a descendant step)
            for (int skip = s; skip <= steps.size(); skip++) {
                if (match(pattern, p + 1, steps, skip)) {
                    return true;
                }
                if (skip < steps.size() && steps.get(skip).startsWith("@")) {
                    return false;
                }
            }
            return false;
        }
        return s < steps.size() && nameMatches(token.test(), steps.get(s)) && match(pattern, p + 1, steps, s + 1);
    }

    private static boolean nameMatches(String test, String step) {
        boolean attributeTest = test.startsWith("@");
        if (attributeTest != step.startsWith("@")) {
            return false;
        }
        String testName = attributeTest ? test.substring(1).strip() : test;
        String stepName = attributeTest ? step.substring(1) : step;
        if ("*".equals(testName) || testName.endsWith(":*")) {
            return true;
        }
        return localName(testName).equals(localName(stepName));
    }

    private static String localName(String name) {
        return name.substring(name.indexOf(':') + 1);
    }
}
