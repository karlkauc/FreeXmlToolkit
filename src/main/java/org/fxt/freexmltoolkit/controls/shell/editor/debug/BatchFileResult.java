package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;

/** One file's outcome in a batch transform run. */
public record BatchFileResult(File file, String output, boolean ok, String error, long timeMs) {
}
