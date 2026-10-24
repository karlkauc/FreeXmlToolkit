package org.fxt.freexmltoolkit.debugger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * "Why did this template match?": the template rules of a mode tested against a node, the one
 * XSLT picks first, and why each other matching rule lost.
 */
public final class MatchExplanation {

    /** How a candidate relates to the node. */
    public enum Status { WINS, LOSES, NO_MATCH, NOT_TESTABLE }

    /**
     * @param candidate the rule alternative
     * @param status    whether it matched and won
     * @param reason    why it lost / could not be tested, or {@code ""}
     */
    public record Line(TemplateRules.Candidate candidate, Status status, String reason) {

        /** @return the list text, e.g. {@code ✓ match="item"  ·  priority 0  ·  a.xsl:3} */
        public String text() {
            String mark = switch (status) {
                case WINS -> "✓ wins:  ";
                case LOSES -> "matches, loses (" + reason + "):  ";
                case NO_MATCH -> "no match:  ";
                case NOT_TESTABLE -> "not tested (" + reason + "):  ";
            };
            return mark + candidate.describe();
        }
    }

    private MatchExplanation() {
    }

    /**
     * @param ordered candidates best first ({@link TemplateRules#candidates})
     * @param test    whether a candidate matches the node; {@code null} = could not be tested
     *                (the value is then the error), see {@link Result}
     * @return the matching candidates first (winner, then losers in order), then the others
     */
    public static List<Line> explain(List<TemplateRules.Candidate> ordered,
                                     Function<TemplateRules.Candidate, Result> test) {
        List<Line> matching = new ArrayList<>();
        List<Line> others = new ArrayList<>();
        TemplateRules.Candidate winner = null;
        for (TemplateRules.Candidate c : ordered) {
            Result r = test.apply(c);
            if (r.error() != null) {
                others.add(new Line(c, Status.NOT_TESTABLE, r.error()));
            } else if (!r.matches()) {
                others.add(new Line(c, Status.NO_MATCH, ""));
            } else if (winner == null) {
                winner = c;
                matching.add(new Line(c, Status.WINS, ""));
            } else {
                matching.add(new Line(c, Status.LOSES, reason(c, winner)));
            }
        }
        matching.addAll(others);
        return matching;
    }

    /** The outcome of testing one candidate. */
    public record Result(boolean matches, String error) {

        public static Result of(boolean matches) {
            return new Result(matches, null);
        }

        public static Result failed(String error) {
            return new Result(false, error == null ? "error" : error);
        }
    }

    private static String reason(TemplateRules.Candidate loser, TemplateRules.Candidate winner) {
        if (loser.rule() == winner.rule()) {
            return "another part of the same pattern";
        }
        if (loser.rule().precedence() < winner.rule().precedence()) {
            return "lower import precedence";
        }
        if (loser.priority() < winner.priority()) {
            return "lower priority";
        }
        return "earlier in the stylesheet";
    }
}
