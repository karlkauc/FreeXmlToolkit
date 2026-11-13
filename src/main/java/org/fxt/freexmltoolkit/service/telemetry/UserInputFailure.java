package org.fxt.freexmltoolkit.service.telemetry;

/**
 * Marks an exception that is by definition caused by the user's input (a file that is not what it must
 * be, e.g. a "schema" whose root is not {@code xs:schema}). {@link QueryFailure#classify} reports it as
 * {@code invalid_input}, so it is not counted as an application error.
 */
public interface UserInputFailure {
}
