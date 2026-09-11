package org.fxt.freexmltoolkit.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;

/**
 * Generates random strings of a pattern's language within a length range.
 *
 * <p>Replaces {@code Generex.random}, whose backtracking recursion has no depth bound: for a pattern such as
 * {@code .+Z} it occasionally recursed tens of thousands of levels deep, building ever longer strings, which ended in
 * a {@link StackOverflowError} on a default thread stack and in an {@link OutOfMemoryError} on a large one. This
 * sampler walks the same automaton (same regular expression syntax and predefined character classes as Generex)
 * iteratively and only takes transitions from which an accepting state stays reachable within the remaining length,
 * so it needs at most {@code maxLength} steps. It only emits characters XML 1.0 allows, preferring printable ASCII and
 * never tabs or line breaks, so the samples can be embedded in XML elements and attributes.</p>
 */
final class BoundedPatternSampler {

    /** Repetition upper bounds above this are capped: one sample never needs more, and each repetition adds states. */
    static final int MAX_SAMPLED_REPETITIONS = 256;
    /** Exact repetition counts above this are refused (the automaton would be too large); callers fall back. */
    static final int MAX_EXACT_REPETITIONS = 4096;
    /** Longest sample the walk produces even when the caller allows more. */
    private static final int MAX_SAMPLE_LENGTH = 4096;
    /** Probability of stopping in an accepting state once the minimum length is reached (as in Generex). */
    private static final double STOP_PROBABILITY = 0.35;
    private static final Pattern REPETITION = Pattern.compile("(?<!\\\\)\\{(\\d+)(,(\\d*))?}");
    /** Generex's predefined character classes, applied before the pattern is parsed. */
    private static final Map<String, String> PREDEFINED_CHARACTER_CLASSES = predefinedCharacterClasses();

    /** Candidate transitions are walked in this order, so a seeded sample does not depend on the automaton's layout. */
    private static final java.util.Comparator<Transition> TRANSITION_ORDER =
            java.util.Comparator.comparingInt(Transition::getMin).thenComparingInt(Transition::getMax);

    private final Automaton automaton;
    private final Map<State, Integer> distanceToAccept;
    /** Longest number of transitions from a state to an accepting state; {@link Integer#MAX_VALUE} past a cycle. */
    private final Map<State, Integer> longestToAccept;
    private final RandomGenerator random;

    /**
     * @param pattern the pattern (Generex / dk.brics syntax)
     * @param random  the source of randomness
     * @throws IllegalArgumentException if the pattern cannot be parsed or repeats a group too often
     */
    BoundedPatternSampler(String pattern, RandomGenerator random) {
        String regex = capRepetitionBounds(pattern);
        for (Map.Entry<String, String> characterClass : PREDEFINED_CHARACTER_CLASSES.entrySet()) {
            regex = regex.replaceAll(characterClass.getKey(), characterClass.getValue());
        }
        // RegExp.NONE: '&', '~', '#', '@' and '<' are literals in XSD patterns, not automaton operators (KML .+@.+);
        // a double quote would open a brics string literal
        this.automaton = new RegExp(regex.replace("\"", "\\\""), RegExp.NONE).toAutomaton();
        this.distanceToAccept = distancesToAccept(automaton);
        this.longestToAccept = longestDistancesToAccept(automaton, distanceToAccept);
        this.random = random;
    }

    /**
     * @param minLength minimum length of the sample
     * @param maxLength maximum length of the sample
     * @return a random member of the language within the length range, or {@code null} if the walk found none
     */
    String sample(int minLength, int maxLength) {
        int max = (int) Math.min(maxLength, Math.max(minLength, 0) + (long) MAX_SAMPLE_LENGTH);
        State state = automaton.getInitialState();
        if (distance(state) > max || longest(state) < minLength) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        List<Transition> candidates = new ArrayList<>();
        while (true) {
            int length = value.length();
            if (state.isAccept() && length >= minLength && (length == max || random.nextDouble() < STOP_PROBABILITY)) {
                return value.toString();
            }
            candidates.clear();
            for (Transition transition : state.getTransitions()) {
                State dest = transition.getDest();
                // the destination must still reach an accepting state within the maximum and at the minimum length
                // (UCI NITF length 4 with the alternative [DNIO] ended the walk at length 1)
                if (hasXmlCharacter(transition) && distance(dest) <= max - length - 1
                        && longest(dest) >= minLength - length - 1) {
                    candidates.add(transition);
                }
            }
            if (candidates.isEmpty()) {
                return state.isAccept() && length >= minLength ? value.toString() : null;
            }
            // The automaton's transitions are held in a set whose order follows object identity, so the same seed
            // walked different paths from one automaton to the next; sorting by character range makes it repeatable
            candidates.sort(TRANSITION_ORDER);
            Transition transition = candidates.get(random.nextInt(candidates.size()));
            value.append((char) pickXmlCharacter(transition));
            state = transition.getDest();
        }
    }

    /**
     * Caps repetition upper bounds ({@code {m,n}}) at {@link #MAX_SAMPLED_REPETITIONS}; the capped pattern matches a
     * subset of the original language, so every sample still matches the original pattern.
     *
     * @throws IllegalArgumentException for exact repetition counts above {@link #MAX_EXACT_REPETITIONS}
     */
    static String capRepetitionBounds(String pattern) {
        Matcher matcher = REPETITION.matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            long min = parseBound(matcher.group(1));
            if (min > MAX_EXACT_REPETITIONS) {
                throw new IllegalArgumentException("Repetition count too large for sampling: " + matcher.group());
            }
            String replacement = matcher.group();
            String upper = matcher.group(3);
            if (matcher.group(2) != null && upper != null && !upper.isEmpty() && parseBound(upper) > MAX_SAMPLED_REPETITIONS) {
                replacement = "{" + matcher.group(1) + "," + Math.max(min, MAX_SAMPLED_REPETITIONS) + "}";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static long parseBound(String digits) {
        return digits.length() > 12 ? Long.MAX_VALUE : Long.parseLong(digits);
    }

    /**
     * Character ranges tried in order when picking a character of a transition: printable ASCII first, then the other
     * characters XML 1.0 allows, the space last. Control characters, surrogates, U+FFFE and U+FFFF are never picked,
     * and neither are tab, line feed and carriage return: in attribute values they are normalized to spaces, which a
     * pattern may forbid (XTCE {@code NameType}).
     */
    private static final int[][] XML_CHARACTER_RANGES = {
            {0x21, 0x7E}, {0xA1, 0xD7FF}, {0xE000, 0xFFFD}, {0x20, 0x20}
    };

    private static boolean hasXmlCharacter(Transition transition) {
        for (int[] range : XML_CHARACTER_RANGES) {
            if (Math.max(range[0], transition.getMin()) <= Math.min(range[1], transition.getMax())) {
                return true;
            }
        }
        return false;
    }

    /** A random character of the transition that XML 1.0 allows, from the first preferred range it overlaps. */
    private int pickXmlCharacter(Transition transition) {
        for (int[] range : XML_CHARACTER_RANGES) {
            int low = Math.max(range[0], transition.getMin());
            int high = Math.min(range[1], transition.getMax());
            if (low <= high) {
                return low + random.nextInt(high - low + 1);
            }
        }
        throw new IllegalStateException("transition without XML characters");
    }

    private int distance(State state) {
        return distanceToAccept.getOrDefault(state, Integer.MAX_VALUE);
    }

    private int longest(State state) {
        return longestToAccept.getOrDefault(state, -1);
    }

    /**
     * Longest number of transitions from every state that can reach an accepting state, peeling states whose
     * successors are all resolved (reverse topological order); states left over lie on or before a cycle and get
     * {@link Integer#MAX_VALUE}.
     */
    private static Map<State, Integer> longestDistancesToAccept(Automaton automaton, Map<State, Integer> coReachable) {
        Map<State, List<State>> predecessors = new HashMap<>();
        Map<State, Integer> unresolvedSuccessors = new HashMap<>();
        Map<State, Integer> longest = new HashMap<>();
        for (State state : coReachable.keySet()) {
            int successors = 0;
            for (Transition transition : state.getTransitions()) {
                if (hasXmlCharacter(transition) && coReachable.containsKey(transition.getDest())) {
                    predecessors.computeIfAbsent(transition.getDest(), k -> new ArrayList<>()).add(state);
                    successors++;
                }
            }
            unresolvedSuccessors.put(state, successors);
            longest.put(state, state.isAccept() ? 0 : -1);
        }
        ArrayDeque<State> resolved = new ArrayDeque<>();
        unresolvedSuccessors.forEach((state, successors) -> {
            if (successors == 0) {
                resolved.add(state);
            }
        });
        Map<State, Integer> result = new HashMap<>();
        while (!resolved.isEmpty()) {
            State state = resolved.poll();
            int value = longest.get(state);
            result.put(state, value);
            for (State predecessor : predecessors.getOrDefault(state, List.of())) {
                longest.merge(predecessor, value + 1, Math::max);
                if (unresolvedSuccessors.merge(predecessor, -1, Integer::sum) == 0) {
                    resolved.add(predecessor);
                }
            }
        }
        for (State state : coReachable.keySet()) {
            result.putIfAbsent(state, Integer.MAX_VALUE);
        }
        return result;
    }

    /** Shortest number of transitions from every state to an accepting state (breadth-first over reversed edges). */
    private static Map<State, Integer> distancesToAccept(Automaton automaton) {
        Map<State, List<State>> predecessors = new HashMap<>();
        ArrayDeque<State> queue = new ArrayDeque<>();
        Map<State, Integer> distance = new HashMap<>();
        for (State state : automaton.getStates()) {
            for (Transition transition : state.getTransitions()) {
                if (hasXmlCharacter(transition)) {
                    predecessors.computeIfAbsent(transition.getDest(), k -> new ArrayList<>()).add(state);
                }
            }
            if (state.isAccept()) {
                distance.put(state, 0);
                queue.add(state);
            }
        }
        while (!queue.isEmpty()) {
            State state = queue.poll();
            int next = distance.get(state) + 1;
            for (State predecessor : predecessors.getOrDefault(state, List.of())) {
                if (!distance.containsKey(predecessor)) {
                    distance.put(predecessor, next);
                    queue.add(predecessor);
                }
            }
        }
        return distance;
    }

    private static Map<String, String> predefinedCharacterClasses() {
        Map<String, String> classes = new LinkedHashMap<>();
        classes.put("\\\\d", "[0-9]");
        classes.put("\\\\D", "[^0-9]");
        classes.put("\\\\s", "[ \t\n\f\r]");
        classes.put("\\\\S", "[^ \t\n\f\r]");
        classes.put("\\\\w", "[a-zA-Z_0-9]");
        classes.put("\\\\W", "[^a-zA-Z_0-9]");
        return classes;
    }
}
