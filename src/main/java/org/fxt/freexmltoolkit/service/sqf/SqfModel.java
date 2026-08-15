package org.fxt.freexmltoolkit.service.sqf;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.sf.saxon.s9api.XdmNode;

/**
 * The immutable model of one Schematron file's SQF (Schematron Quick Fix) content:
 * fix definitions, their activities and the mapping from asserts/reports to the
 * fixes they reference. Produced by {@link SqfParser}, cached by
 * {@link SqfCatalogCache}, consumed by {@code SqfCorrelator} and the execution engine.
 */
public final class SqfModel {

    private SqfModel() {
    }

    /**
     * All SQF content of one Schematron file.
     *
     * @param schematronFile     the parsed Schematron file
     * @param namespaces         {@code sch:ns} declarations (prefix → URI), in scope for
     *                           every XPath a fix evaluates
     * @param fixesByKey         every fix definition by its unique catalog key, in
     *                           document order
     * @param fixKeysByAssertId  fix keys per assert/report {@code @id} (ordered,
     *                           default fix first)
     * @param fixKeysByAssert    fix keys per (rule context, test) pair for findings
     *                           whose assert has no id (ordered, default fix first)
     */
    public record SqfCatalog(
            File schematronFile,
            Map<String, String> namespaces,
            Map<String, SqfFix> fixesByKey,
            Map<String, List<String>> fixKeysByAssertId,
            Map<AssertKey, List<String>> fixKeysByAssert) {

        /** @return {@code true} when the Schematron declares no usable fixes */
        public boolean isEmpty() {
            return fixesByKey.isEmpty();
        }

        /** @return an empty catalog for a file without (usable) SQF content */
        public static SqfCatalog empty(File schematronFile) {
            return new SqfCatalog(schematronFile, Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * One {@code sqf:fix} definition.
     *
     * @param key                 unique catalog key (stable per parse)
     * @param id                  the author-visible {@code @id}
     * @param title               {@code sqf:description/sqf:title}, or the id as fallback
     * @param paragraphs          {@code sqf:description/sqf:p} texts
     * @param useWhen             {@code @use-when} XPath (group condition merged in), or null
     * @param letsInScope         {@code sch:let} bindings in lexical scope, outermost first
     *                            (schema, pattern, rule, fix-local)
     * @param params              declared {@code sqf:param}s (generic fixes)
     * @param userEntries         declared {@code sqf:user-entry} prompts
     * @param activities          the fix's activity elements in document order
     * @param visibleFixKeysById  fix id → catalog key visible from this fix's lexical
     *                            scope (rule-local shadows global); used by sqf:call-fix
     */
    public record SqfFix(
            String key,
            String id,
            String title,
            List<String> paragraphs,
            String useWhen,
            List<SqfLet> letsInScope,
            List<SqfParam> params,
            List<SqfUserEntry> userEntries,
            List<SqfActivity> activities,
            Map<String, String> visibleFixKeysById) {
    }

    /** One activity of a fix. */
    public sealed interface SqfActivity
            permits SqfAdd, SqfDelete, SqfReplace, SqfStringReplace, SqfCallFix {

        /** @return the {@code @use-when} XPath of this activity, or {@code null} */
        String useWhen();
    }

    /**
     * {@code sqf:add} — insert new nodes.
     *
     * @param match    XPath selecting the anchor nodes (relative to the rule context
     *                 node), or {@code null} for the context node itself
     * @param nodeType {@code keep|element|attribute|processing-instruction|pi|comment},
     *                 or {@code null}
     * @param target   name of the created node (attribute value template), or null
     * @param position {@code first-child|last-child|before|after} (default first-child)
     * @param select   XPath providing the content, or {@code null} when the element
     *                 content is the template
     * @param content  the {@code sqf:add} element itself (its children are the content
     *                 template)
     */
    public record SqfAdd(String match, String nodeType, String target, String position,
                         String select, XdmNode content, String useWhen) implements SqfActivity {
    }

    /** {@code sqf:delete} — remove the matched nodes. */
    public record SqfDelete(String match, String useWhen) implements SqfActivity {
    }

    /** {@code sqf:replace} — replace the matched nodes (see {@link SqfAdd} for attributes). */
    public record SqfReplace(String match, String nodeType, String target,
                             String select, XdmNode content, String useWhen) implements SqfActivity {
    }

    /**
     * {@code sqf:stringReplace} — regex-replace inside the matched text nodes.
     *
     * @param match  XPath selecting text nodes (relative to the anchor)
     * @param regex  the XPath regular expression (attribute value template)
     * @param flags  regex flags, or {@code null}
     * @param select XPath providing the replacement, or {@code null}
     * @param content the {@code sqf:stringReplace} element (children = replacement template)
     */
    public record SqfStringReplace(String match, String regex, String flags,
                                   String select, XdmNode content, String useWhen) implements SqfActivity {
    }

    /**
     * {@code sqf:call-fix} — execute another fix's activities.
     *
     * @param ref        the called fix's id (resolved via the caller's visible scope)
     * @param withParams parameter name → XPath select expression
     */
    public record SqfCallFix(String ref, Map<String, String> withParams,
                             String useWhen) implements SqfActivity {
    }

    /**
     * {@code sqf:user-entry} — a value prompted from the user at fix time.
     *
     * @param name         the variable name the value is bound to
     * @param title        prompt title (description title, or the name)
     * @param description  prompt description text, or {@code ""}
     * @param defaultXPath XPath computing the suggested default, or {@code null}
     */
    public record SqfUserEntry(String name, String title, String description, String defaultXPath) {
    }

    /** {@code sqf:param} — a parameter of a generic fix. */
    public record SqfParam(String name, String defaultXPath, boolean required) {
    }

    /** {@code sch:let} — a named XPath binding ({@code @value}). */
    public record SqfLet(String name, String valueXPath) {
    }

    /**
     * Correlation key for asserts/reports without an {@code @id}: the enclosing rule's
     * context expression plus the test expression, both whitespace-normalized.
     */
    public record AssertKey(String ruleContext, String normalizedTest) {

        /** Normalizes both parts (trim + collapse internal whitespace). */
        public static AssertKey of(String ruleContext, String test) {
            return new AssertKey(normalize(ruleContext), normalize(test));
        }

        /** @return {@code s} trimmed with internal whitespace runs collapsed to one space */
        public static String normalize(String s) {
            return s == null ? "" : s.trim().replaceAll("\\s+", " ");
        }
    }
}
