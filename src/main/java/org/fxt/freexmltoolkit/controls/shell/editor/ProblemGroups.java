package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * The problems of a document grouped so they can be worked off systematically: by the security / entity / record
 * they are about (a security that occurs a hundred times is one group), by rule, or by category. Each group holds
 * its kinds of problems ({@link Kind}), each kind its occurrences. No JavaFX: built off the FX thread.
 */
public final class ProblemGroups {

    /** What the groups are. */
    public enum By { SUBJECT, RULE, CATEGORY }

    /**
     * One kind of problem inside a group (a rule / category, or - grouped by rule - one security).
     *
     * @param problems the occurrences, in document order
     */
    public record Kind(String key, String title, ValidationProblem.Level level, List<ValidationProblem> problems) {
    }

    /**
     * A group.
     *
     * @param subject  the security / entity / record (grouped by subject), or {@code null}
     * @param category the category (grouped by category or rule), or {@code null}
     * @param count    all occurrences
     */
    public record Group(String key, String title, ValidationProblem.Level level, int count, List<Kind> kinds,
                        ProblemSubject subject, ProblemCategory category) {

        /** @return every occurrence of the group, in document order */
        public List<ValidationProblem> problems() {
            List<ValidationProblem> all = new ArrayList<>();
            kinds.forEach(k -> all.addAll(k.problems()));
            all.sort(Comparator.comparingInt(ValidationProblem::line).thenComparingInt(ValidationProblem::column));
            return all;
        }
    }

    private static final Pattern XSD_CODE = Pattern.compile("^(cvc-[\\w.-]+|s4s-[\\w.-]+|src-[\\w.-]+)");
    private static final int TITLE_LENGTH = 140;

    private ProblemGroups() {
    }

    /** @return the groups: worst level first, then the most occurrences */
    public static List<Group> group(List<ValidationProblem> problems, By by) {
        Map<String, Map<String, List<ValidationProblem>>> groups = new LinkedHashMap<>();
        Map<String, ValidationProblem> firstOfGroup = new LinkedHashMap<>();
        for (ValidationProblem p : problems) {
            String groupKey = switch (by) {
                case SUBJECT -> p.subject() != null ? p.subject().groupKey() : "rule:" + ruleKey(p);
                case RULE -> "rule:" + ruleKey(p);
                case CATEGORY -> "cat:" + p.effectiveCategory().name();
            };
            String kindKey = switch (by) {
                case SUBJECT -> ruleKey(p);
                case RULE -> p.subject() != null ? p.subject().groupKey() : "";
                case CATEGORY -> ruleKey(p);
            };
            groups.computeIfAbsent(groupKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(kindKey, k -> new ArrayList<>()).add(p);
            firstOfGroup.putIfAbsent(groupKey, p);
        }
        List<Group> result = new ArrayList<>(groups.size());
        for (var g : groups.entrySet()) {
            ValidationProblem first = firstOfGroup.get(g.getKey());
            List<Kind> kinds = new ArrayList<>();
            for (var k : g.getValue().entrySet()) {
                ValidationProblem example = k.getValue().getFirst();
                String title = switch (by) {
                    case SUBJECT, CATEGORY -> ruleTitle(example);
                    case RULE -> example.subject() != null ? example.subject().display()
                            : I18n.t("ProblemGroups.no-subject");
                };
                kinds.add(new Kind(k.getKey(), title, worst(k.getValue()), List.copyOf(k.getValue())));
            }
            kinds.sort(Comparator.comparing(Kind::level).thenComparing((Kind k) -> -k.problems().size())
                    .thenComparing(Kind::title));
            int count = kinds.stream().mapToInt(k -> k.problems().size()).sum();
            ValidationProblem.Level level = kinds.stream().map(Kind::level).min(Comparator.naturalOrder())
                    .orElse(ValidationProblem.Level.INFO);
            String title;
            ProblemSubject subject = null;
            ProblemCategory category = null;
            if (by == By.SUBJECT && first.subject() != null) {
                subject = first.subject();
                title = subject.display();
            } else if (by == By.CATEGORY) {
                category = first.effectiveCategory();
                title = category.fullTitle();
            } else {
                category = first.effectiveCategory();
                title = by == By.SUBJECT ? I18n.t("ProblemGroups.no-subject") + " · " + ruleTitle(first) : ruleTitle(first);
            }
            result.add(new Group(g.getKey(), title, level, count, List.copyOf(kinds), subject, category));
        }
        result.sort(Comparator.comparing((Group g) -> by == By.SUBJECT && g.subject() == null)
                .thenComparing(Group::level).thenComparing((Group g) -> -g.count()).thenComparing(Group::title));
        return result;
    }

    /**
     * @return what makes two problems "the same kind": the category for online findings, the rule code, the XSD
     *         error code, else source and message
     */
    public static String ruleKey(ValidationProblem p) {
        ProblemCategory category = p.effectiveCategory();
        if (category.group() == ProblemCategory.Group.LEI || category.group() == ProblemCategory.Group.ISIN
                || category == ProblemCategory.ONLINE_SERVICE) {
            return category.name();
        }
        if (p.ruleId() != null && !p.ruleId().isBlank()) {
            return (p.source() == null ? "" : p.source()) + "|" + p.ruleId();
        }
        String message = p.message() == null ? "" : p.message();
        Matcher code = XSD_CODE.matcher(message);
        if (code.find()) {
            return (p.source() == null ? "" : p.source()) + "|" + code.group(1);
        }
        return (p.source() == null ? "" : p.source()) + "|" + message;
    }

    /** @return the readable name of a problem's kind: category, rule code and an example message */
    public static String ruleTitle(ValidationProblem p) {
        ProblemCategory category = p.effectiveCategory();
        if (category.group() == ProblemCategory.Group.LEI || category.group() == ProblemCategory.Group.ISIN
                || category == ProblemCategory.ONLINE_SERVICE) {
            return category.fullTitle();
        }
        String message = p.message() == null ? "" : p.message();
        if (p.ruleId() != null && !p.ruleId().isBlank() && message.contains(" – ")) {
            message = message.substring(0, message.indexOf(" – ")); // "code name – where: detail" → the rule itself
        }
        String source = p.source() == null || p.source().isBlank() ? "" : p.source() + ": ";
        return clip(source + message);
    }

    private static ValidationProblem.Level worst(List<ValidationProblem> problems) {
        ValidationProblem.Level worst = ValidationProblem.Level.INFO;
        for (ValidationProblem p : problems) {
            if (p.level().compareTo(worst) < 0) {
                worst = p.level();
            }
        }
        return worst;
    }

    private static String clip(String text) {
        return text.length() <= TITLE_LENGTH ? text : text.substring(0, TITLE_LENGTH - 1) + "…";
    }

    /** @return the number of problems per category (for the filter menu) */
    public static Map<ProblemCategory, Integer> countByCategory(List<ValidationProblem> problems) {
        Map<ProblemCategory, Integer> counts = new java.util.EnumMap<>(ProblemCategory.class);
        for (ValidationProblem p : problems) {
            counts.merge(p.effectiveCategory(), 1, Integer::sum);
        }
        return counts;
    }

    /** Groups by a function (used by the Excel export's per-subject sheet). */
    static <K> Map<K, List<ValidationProblem>> by(List<ValidationProblem> problems, Function<ValidationProblem, K> key) {
        Map<K, List<ValidationProblem>> map = new LinkedHashMap<>();
        for (ValidationProblem p : problems) {
            K k = key.apply(p);
            if (k != null) {
                map.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
            }
        }
        return map;
    }
}
