package org.fxt.freexmltoolkit.debugger;

/**
 * When a breakpoint pauses, judged by how often it has been hit in the current run (a hit is a
 * pass of its line where its condition, if any, held): always, exactly at the N-th hit, from
 * the N-th hit on, or at every N-th hit.
 *
 * @param mode   the rule
 * @param target N (at least 1; ignored for {@link Mode#ALWAYS})
 */
public record HitCondition(Mode mode, int target) {

    /** The rule of a hit condition. */
    public enum Mode { ALWAYS, EQUAL, AT_LEAST, MULTIPLE_OF }

    /** Pause on every hit. */
    public static final HitCondition ALWAYS = new HitCondition(Mode.ALWAYS, 1);

    public HitCondition {
        if (mode == null) {
            mode = Mode.ALWAYS;
        }
        if (target < 1) {
            target = 1;
        }
    }

    /** @return whether the breakpoint pauses at its {@code hits}-th hit (1-based). */
    public boolean matches(int hits) {
        return switch (mode) {
            case ALWAYS -> true;
            case EQUAL -> hits == target;
            case AT_LEAST -> hits >= target;
            case MULTIPLE_OF -> hits % target == 0;
        };
    }

    /** @return whether this rule restricts pausing at all. */
    public boolean isSet() {
        return mode != Mode.ALWAYS;
    }

    /** Storage form: {@code ""}, {@code "=N"}, {@code ">=N"} or {@code "%N"}. */
    public String format() {
        return switch (mode) {
            case ALWAYS -> "";
            case EQUAL -> "=" + target;
            case AT_LEAST -> ">=" + target;
            case MULTIPLE_OF -> "%" + target;
        };
    }

    /** Parses {@link #format()}; anything unreadable is {@link #ALWAYS}. */
    public static HitCondition parse(String text) {
        if (text == null || text.isBlank()) {
            return ALWAYS;
        }
        String t = text.strip();
        try {
            if (t.startsWith(">=")) {
                return new HitCondition(Mode.AT_LEAST, Integer.parseInt(t.substring(2).strip()));
            }
            if (t.startsWith("=")) {
                return new HitCondition(Mode.EQUAL, Integer.parseInt(t.substring(1).strip()));
            }
            if (t.startsWith("%")) {
                return new HitCondition(Mode.MULTIPLE_OF, Integer.parseInt(t.substring(1).strip()));
            }
        } catch (NumberFormatException e) {
            return ALWAYS;
        }
        return ALWAYS;
    }

    /** Human-readable form for lists and tooltips ({@code "hit = 3"}, {@code "hit ≥ 3"}, {@code "every 3rd hit"}). */
    public String describe() {
        return switch (mode) {
            case ALWAYS -> "every hit";
            case EQUAL -> "hit = " + target;
            case AT_LEAST -> "hit ≥ " + target;
            case MULTIPLE_OF -> target == 1 ? "every hit" : "every " + ordinal(target) + " hit";
        };
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }
}
