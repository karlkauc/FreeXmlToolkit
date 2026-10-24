package org.fxt.freexmltoolkit.debugger;

/**
 * Maps a location the engine reports - a line of the stylesheet Saxon actually runs - to the
 * source the user edits and sets breakpoints in. Used when the run stylesheet is generated
 * (a Schematron compiled to XSLT, see {@link SchematronSourceMap}); without a mapping the
 * reported locations are the source.
 */
@FunctionalInterface
public interface SourceMapping {

    /** A source location: a {@code file:} URI (or path) and a 1-based line, {@code -1} when unknown. */
    record Mapped(String systemId, int line) { }

    /**
     * @return where {@code systemId:line} of the run stylesheet comes from; a line of {@code -1}
     *         when it is generated code without a source counterpart
     */
    Mapped map(String systemId, int line);
}
