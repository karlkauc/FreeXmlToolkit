package org.fxt.freexmltoolkit.domain;

/** Availability of the schema file behind a library entry (computed without network access). */
public enum SchemaEntryStatus { LOCAL_OK, LOCAL_MISSING, CACHED, NOT_DOWNLOADED, ERROR }
