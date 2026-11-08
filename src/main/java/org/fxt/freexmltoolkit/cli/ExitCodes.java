package org.fxt.freexmltoolkit.cli;

/** Exit codes of {@code fxt-cli}; documented in {@code docs/command-line.md}. */
public final class ExitCodes {

    /** Everything succeeded; all documents are valid. */
    public static final int OK = 0;
    /** Problems were found (invalid documents) or a transformation / rendering failed. */
    public static final int FAILED = 1;
    /** Invalid command line or a missing input file (picocli's default for usage errors). */
    public static final int USAGE = 2;
    /** A validation could not run: the schema or Schematron does not load, or a file is unreadable. */
    public static final int NOT_RUN = 3;

    private ExitCodes() {
    }
}
