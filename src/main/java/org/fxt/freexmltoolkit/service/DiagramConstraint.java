package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.view.ConstraintRow;
import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdAssertion;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * One line an element box of the documentation diagram shows for an identity constraint or an
 * assertion. The documentation counterpart of the Graphic view's {@link ConstraintRow}: same
 * labels, a larger length limit ({@link #MAX_TEXT_LENGTH}). An identity constraint's line names its selector and fields, which
 * the diagram turns into links, so it is made of {@link Segment segments}.
 *
 * @param kind     what it is
 * @param segments the parts of the line, already shortened to {@link #MAX_TEXT_LENGTH}
 * @param tooltip  the full details
 */
public record DiagramConstraint(ConstraintRow.Kind kind, List<Segment> segments, String tooltip) {

    /**
     * Most characters of a line. Larger than the Graphic view's {@link ConstraintRow#MAX_TEXT_LENGTH}:
     * a line here also names the selector, and a documentation page has more room than a card.
     */
    public static final int MAX_TEXT_LENGTH = 60;

    /** What a part of the line stands for. */
    public enum Role {
        /** Plain text: the name, separators, an assertion. */
        TEXT,
        /** The selector XPath, relative to the element that holds the constraint. */
        SELECTOR,
        /** A field XPath, relative to what the selector selects. */
        FIELD,
        /** The name of the key or unique constraint a keyref refers to. */
        REFER
    }

    /**
     * A part of a line.
     *
     * @param text what is shown
     * @param role what it stands for
     * @param path the full XPath or key name behind it (the text may be shortened), null for text
     */
    public record Segment(String text, Role role, String path) {

        static Segment text(String text) {
            return new Segment(text, Role.TEXT, null);
        }
    }

    public DiagramConstraint {
        segments = List.copyOf(segments);
    }

    /** @return the line as shown */
    public String text() {
        StringBuilder text = new StringBuilder();
        segments.forEach(segment -> text.append(segment.text()));
        return text.toString();
    }

    /**
     * Collects the lines of an element: its identity constraints first, then the assertions of its
     * type, each in declaration order.
     *
     * @param element the element
     * @return its lines, empty if it has none
     */
    public static List<DiagramConstraint> of(XsdExtendedElement element) {
        List<DiagramConstraint> rows = new ArrayList<>();
        if (element == null) {
            return rows;
        }
        if (element.getIdentityConstraints() != null) {
            for (IdentityConstraint constraint : element.getIdentityConstraints()) {
                rows.add(identity(constraint));
            }
        }
        if (element.getAssertions() != null) {
            for (XsdAssertion assertion : element.getAssertions()) {
                if (assertion.getTest() != null && !assertion.getTest().isBlank()) {
                    rows.add(new DiagramConstraint(ConstraintRow.Kind.ASSERT,
                            List.of(Segment.text(ConstraintRow.shorten(ConstraintRow.normalize(assertion.getTest()), MAX_TEXT_LENGTH))),
                            "assert\n" + assertion.getTest().strip()));
                }
            }
        }
        return rows;
    }

    private static DiagramConstraint identity(IdentityConstraint constraint) {
        String name = constraint.getName() == null ? "" : constraint.getName();
        List<String> fields = constraint.getFields() == null ? List.of() : constraint.getFields();
        String selector = constraint.getSelector() == null ? "" : constraint.getSelector().strip();
        ConstraintRow.Kind kind;
        String keyword;
        switch (constraint.getType()) {
            case KEY -> {
                kind = ConstraintRow.Kind.KEY;
                keyword = "key";
            }
            case KEYREF -> {
                kind = ConstraintRow.Kind.KEYREF;
                keyword = "keyref";
            }
            default -> {
                kind = ConstraintRow.Kind.UNIQUE;
                keyword = "unique";
            }
        }
        String refer = constraint.getRefer();

        // name  selector / field, field → refer
        List<Segment> segments = new ArrayList<>();
        segments.add(Segment.text(name + "  "));
        if (!selector.isEmpty()) {
            segments.add(new Segment(selector, Role.SELECTOR, selector));
            if (!fields.isEmpty()) {
                segments.add(Segment.text(" / "));
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                segments.add(Segment.text(", "));
            }
            String field = fields.get(i).strip();
            segments.add(new Segment(field, Role.FIELD, field));
        }
        if (kind == ConstraintRow.Kind.KEYREF && refer != null && !refer.isBlank()) {
            segments.add(Segment.text(" → "));
            segments.add(new Segment(refer.strip(), Role.REFER, refer.strip()));
        }

        StringBuilder tooltip = new StringBuilder(keyword).append(' ').append(name);
        if (!selector.isEmpty()) {
            tooltip.append("\nselector: ").append(selector);
        }
        if (!fields.isEmpty()) {
            tooltip.append("\nfields: ").append(String.join(", ", fields));
        }
        if (refer != null && !refer.isBlank()) {
            tooltip.append("\nrefer: ").append(refer);
        }
        return new DiagramConstraint(kind, shorten(trimEnd(segments)), tooltip.toString().strip());
    }

    /** Drops the trailing blanks of a line that ends after the name. */
    private static List<Segment> trimEnd(List<Segment> segments) {
        if (segments.size() == 1) {
            return List.of(Segment.text(segments.getFirst().text().strip()));
        }
        return segments;
    }

    /**
     * Cuts the line to {@link #MAX_TEXT_LENGTH} characters: the segment that crosses
     * the limit ends in an ellipsis, later ones are dropped.
     */
    static List<Segment> shorten(List<Segment> segments) {
        if (length(segments) <= MAX_TEXT_LENGTH) {
            return segments;
        }
        // First show long paths by their last step ("…/Benchmark"): the link still goes to the
        // full path's target, the tooltip names it, and the fields stay visible.
        segments = segments.stream().map(DiagramConstraint::lastStep).toList();
        if (length(segments) <= MAX_TEXT_LENGTH) {
            return segments;
        }
        List<Segment> result = new ArrayList<>();
        int room = MAX_TEXT_LENGTH - 1; // one character for the ellipsis
        for (Segment segment : segments) {
            if (segment.text().length() < room) {
                result.add(segment);
                room -= segment.text().length();
                continue;
            }
            result.add(new Segment(segment.text().substring(0, room) + "…", segment.role(), segment.path()));
            break;
        }
        return result;
    }

    private static int length(List<Segment> segments) {
        return segments.stream().mapToInt(s -> s.text().length()).sum();
    }

    /** A selector or field with several steps shown by its last one; alternatives ({@code a|b}) stay whole. */
    private static Segment lastStep(Segment segment) {
        String text = segment.text();
        boolean path = segment.role() == Role.SELECTOR || segment.role() == Role.FIELD;
        int slash = text.lastIndexOf('/');
        if (!path || slash < 0 || text.contains("|") || slash == text.length() - 1) {
            return segment;
        }
        return new Segment("…/" + text.substring(slash + 1), segment.role(), segment.path());
    }
}
