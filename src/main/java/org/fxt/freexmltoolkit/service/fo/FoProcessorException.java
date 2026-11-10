package org.fxt.freexmltoolkit.service.fo;

import java.util.List;

import org.fxt.freexmltoolkit.service.FopEvent;

/**
 * An external XSL-FO processor could not be started, failed or wrote no output. Carries the
 * messages read from the program's output so far, so the panel can still show them.
 */
public class FoProcessorException extends Exception {

    private final transient List<FopEvent> events;

    public FoProcessorException(String message, List<FopEvent> events) {
        super(message);
        this.events = events == null ? List.of() : List.copyOf(events);
    }

    public FoProcessorException(String message, Throwable cause) {
        super(message, cause);
        this.events = List.of();
    }

    /** @return the messages the program wrote before it failed */
    public List<FopEvent> events() {
        return events;
    }
}
