package org.fxt.freexmltoolkit.service.sqf;

/**
 * Namespace and name constants for Schematron Quick Fix (SQF) processing.
 */
public final class SqfNames {

    /** The SQF namespace URI. */
    public static final String SQF_NS = "http://www.schematron-quickfix.com/validator/process";

    /** The ISO Schematron namespace URI. */
    public static final String SCH_NS = "http://purl.oclc.org/dsdl/schematron";

    /** The SVRL (Schematron Validation Report Language) namespace URI. */
    public static final String SVRL_NS = "http://purl.oclc.org/dsdl/svrl";

    /** The XSLT namespace URI (fix content may embed XSLT instructions). */
    public static final String XSL_NS = "http://www.w3.org/1999/XSL/Transform";

    private SqfNames() {
    }
}
