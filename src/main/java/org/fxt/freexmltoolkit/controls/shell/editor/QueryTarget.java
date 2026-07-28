package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

/**
 * The XML target an XPath/XQuery/XSLT editor document runs against, chosen via the
 * editor toolbar's Target dropdown. Stored per open document (session-only) in
 * {@link EditorHost}; the absence of an explicit choice means {@link #AUTOMATIC}.
 */
public sealed interface QueryTarget {

    /** The default: run against {@link EditorHost#getLastXmlFamilyDocument()}. */
    QueryTarget AUTOMATIC = new Automatic();

    /** Run against the most recently active XML-family document (the default). */
    record Automatic() implements QueryTarget {
    }

    /** Run against a specific open editor document (its live, possibly unsaved text). */
    record OpenDoc(OpenDocument document) implements QueryTarget {
    }

    /** Run against an XML file on disk that is not (necessarily) open in the editor. */
    record FsFile(File file) implements QueryTarget {
    }
}
