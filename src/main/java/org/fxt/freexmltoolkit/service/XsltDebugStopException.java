package org.fxt.freexmltoolkit.service;

/**
 * Thrown by {@link XsltDebugTraceListener} when the debug session has been
 * stopped by the user. The Saxon transformer unwinds and the engine catches
 * this to produce a "stopped" result instead of a normal error.
 */
public class XsltDebugStopException extends RuntimeException {

    public XsltDebugStopException() {
        super("XSLT debug session stopped by user");
    }
}
